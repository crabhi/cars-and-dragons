package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Bestiary
import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.Spell
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.IOException

/** Where to send chat requests. Any OpenAI-compatible Chat Completions endpoint works. */
data class LlmConfig(
    val apiKey: String,
    val model: String = DEFAULT_MODEL,
    val baseUrl: String = "https://api.openai.com/v1",
    /** Upper bound for generated tokens per call, including any hidden reasoning tokens. */
    val maxOutputTokens: Int = 1200,
) {
    companion object {
        const val DEFAULT_MODEL = "gpt-6-sol"
    }
}

/**
 * Narrator backed by an OpenAI-compatible chat model (default GPT-6 Sol). Prompts are English, every
 * player-facing string it produces is Czech. It only proposes actions and describes engine results.
 */
class OpenAiNarrator(
    private val config: LlmConfig,
    private val tone: String,
    private val onUsage: (inputTokens: Int, outputTokens: Int) -> Unit = { _, _ -> },
    private val http: HttpClient = HttpClient(),
    private val glossary: Glossary = Glossary.czech,
) : Narrator {
    private val baseRules =
        """
        You are the Dungeon Master voice of "Cars & Dragons", an AD&D 2nd Edition game played by voice in a car.
        Players are passengers (and possibly the driver) listening through the car speakers.
        Hard rules:
        - A deterministic rules engine owns all game math: dice, hits, damage, saves, HP, spells, XP. You never decide or change outcomes.
        - Everything the players hear is natural, spoken Czech. Use the Czech glossary terms below for game terms.
        - Address player characters by name in the correct Czech vocative (e.g. "Thorine, co uděláš?").
        - Never say THAC0, dice rolls or target numbers. Say that an attack "zasáhl" or "minul".
        - No markdown, no lists, no emoji: this text is read aloud by text-to-speech.
        - Tone: $tone
        Czech glossary (key = singular|few|many):
        """.trimIndent() + "\n" + glossary.asPromptTable()

    override suspend fun createAdventure(
        premise: String,
        party: List<Combatant>,
        feedback: List<String>,
    ): Adventure {
        val monsters =
            Bestiary.all.joinToString("\n") { "- ${it.id} (${glossary.monsterName(it.id)}): HD ${it.hitDice}, AC ${it.ac}, XP ${it.xp}" }
        val system =
            """
            $baseRules

            Task: expand the players' premise into a short adventure outline for a ${party.size}-character level 1-3 party.
            Return one JSON object exactly in this shape:
            {"title": "...", "premise": "...", "scenes": [{"id": "english_snake_case", "title": "...", "location": "...",
              "description": "2-3 spoken sentences", "goal": "...", "npcs": [{"name": "...", "role": "..."}],
              "encounter": {"monsters": [{"monster": "<id from the list>", "count": 2}]} or null, "magic_item": false}]}
            Constraints:
            - ${AdventureValidator.MIN_SCENES} to ${AdventureValidator.MAX_SCENES} scenes; the last scene is the finale with a fight.
            - At least two scenes have encounters; at most ${AdventureValidator.MAX_MONSTERS_PER_ENCOUNTER} monsters per encounter.
            - Monster ids must come from this list (nothing else exists in the rules):
            $monsters
            - Balance: total monster hit dice per encounter about 1 to 1.5 times the party size; the finale may be harder.
            - Text fields (title, location, description, goal, npc names and roles) are Czech; ids are English.
            """.trimIndent()
        val user =
            buildJsonObject {
                put("premise", premise)
                put("party", buildJsonArray { party.forEach { add(PromptContext.combatant(it, glossary)) } })
                if (feedback.isNotEmpty()) {
                    put(
                        "fix_these_problems_from_previous_attempt",
                        buildJsonArray {
                            feedback.forEach { add(it.toJson()) }
                        },
                    )
                }
            }.toString()
        val obj = parseObject(chat(system, user, json = true))
        return try {
            GameJson.decodeFromJsonElement<AdventureDto>(obj).toAdventure(premise)
        } catch (e: SerializationException) {
            throw NarratorException("Bad adventure JSON", e, transient = false)
        } catch (e: IllegalArgumentException) {
            throw NarratorException("Bad adventure JSON", e, transient = false)
        }
    }

    override suspend fun interpret(
        ctx: NarrationContext,
        actor: Combatant,
        utterance: String,
        options: LegalOptions,
    ): Interpretation {
        val system =
            """
            $baseRules

            Task: turn what the player said (Czech speech recognition output, may contain recognition errors) into ONE proposed action
            for their character. The engine will validate it. Return JSON {"action": {...}} where action is one of:
            {"type":"attack","target":"<monster id or null>","ranged":false}
            {"type":"cast","spell":"<spell id>","target":"<combatant id or null>"}   spell ids: ${Spell.entries.joinToString { it.id }}
            {"type":"use_item","item":"healing_potion","target":"<party member id or null for self>"}
            {"type":"defend"}  {"type":"flee"}  {"type":"rest"}  {"type":"advance"} (move the party on to the next scene)
            {"type":"check","ability":"str|dex|con|int|wis|cha","proficiency":"<optional id>"} (searching, sneaking, climbing...)
            {"type":"narrative","summary":"<short English summary>"} (talking, role play, anything without game mechanics)
            {"type":"meta","command":"status|repeat|quit"} (out-of-game requests)
            Prefer the action types listed in legal_options. Use exact ids from the state. If unclear, use "narrative".
            """.trimIndent()
        val user =
            buildJsonObject {
                put("state", PromptContext.build(ctx.state, glossary))
                put("actor_id", actor.id)
                put("legal_options", PromptContext.options(options))
                put("player_said", utterance)
            }.toString()
        val obj = parseObject(chat(system, user, json = true))
        val action =
            try {
                GameJson.decodeFromJsonElement<ProposedAction>(obj["action"] ?: obj)
            } catch (e: SerializationException) {
                ProposedAction.Narrative(utterance)
            } catch (e: IllegalArgumentException) {
                ProposedAction.Narrative(utterance)
            }
        return Interpretation(action)
    }

    override suspend fun narrate(
        ctx: NarrationContext,
        request: NarrationRequest,
    ): String {
        val (task, payload) = describeRequest(request)
        val system =
            """
            $baseRules

            Task: $task
            Answer with the Czech text to be spoken only, at most ${ctx.maxChars} characters. Short is better in a car.
            Describe engine events faithfully: who hit or missed, damage, who fell, spells and their effects. Do not invent other outcomes.
            You may improvise atmosphere, NPC dialogue and details within the current scene, and keep the story moving toward the scene goal.
            When exploration_turns_in_scene is 3 or more, nudge the players to move on (they can say "jdeme dál").
            """.trimIndent()
        val user =
            buildJsonObject {
                put("state", PromptContext.build(ctx.state, glossary))
                payload.forEach { (k, v) -> put(k, v) }
            }.toString()
        return trimToLength(chat(system, user, json = false).trim(), ctx.maxChars)
    }

    private fun describeRequest(request: NarrationRequest): Pair<String, JsonObject> =
        when (request) {
            is NarrationRequest.Events ->
                "Narrate these resolved game events as the DM." to
                    buildJsonObject {
                        request.actorId?.let { put("actor_id", it) }
                        request.utterance?.let { put("player_said", it) }
                        PromptContext.action(request.action)?.let { put("proposed_action", it) }
                        put("events", PromptContext.events(request.events))
                    }
            is NarrationRequest.TurnPrompt ->
                "First narrate preceding_events (if any) briefly. Then ask the character with actor_id what they do now, " +
                    "by name in the vocative, in one sentence. In combat you may hint at the most relevant options." to
                    buildJsonObject {
                        put("actor_id", request.actorId)
                        put("legal_options", PromptContext.options(request.options))
                        put("preceding_events", PromptContext.events(request.precedingEvents))
                    }
            is NarrationRequest.Rejected ->
                "The player's proposed action is not possible. Explain briefly why (reason code given) and ask again." to
                    buildJsonObject {
                        put("actor_id", request.actorId)
                        put("player_said", request.utterance)
                        put("reason", request.rejection.name.lowercase())
                        put("reason_czech", glossary[request.rejection.key])
                    }
            NarrationRequest.Recap ->
                "The game was interrupted. Remind the players where the story stands in one sentence." to buildJsonObject { }
        }

    private suspend fun chat(
        system: String,
        user: String,
        json: Boolean,
    ): String {
        val body =
            buildJsonObject {
                put("model", config.model)
                put(
                    "messages",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("role", "system")
                                put("content", system)
                            },
                        )
                        add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", user)
                            },
                        )
                    },
                )
                put("max_completion_tokens", config.maxOutputTokens)
                if (json) putJsonObject("response_format") { put("type", "json_object") }
            }.toString()
        val text =
            try {
                http.postJson(
                    "${config.baseUrl.trimEnd('/')}/chat/completions",
                    mapOf("Authorization" to "Bearer ${config.apiKey}"),
                    body,
                )
            } catch (e: HttpClient.HttpException) {
                val transient = e.status == 429 || e.status >= 500
                throw NarratorException("Narrator HTTP ${e.status}", e, transient)
            } catch (e: IOException) {
                throw NarratorException("Narrator unreachable: ${e.message}", e, transient = true)
            }
        val response = parseObject(text)
        response["usage"]?.jsonObject?.let { usage ->
            onUsage(
                usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
                usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
        return response["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw NarratorException("Empty narrator response", transient = false)
    }

    private fun parseObject(text: String): JsonObject =
        try {
            GameJson.parseToJsonElement(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")).jsonObject
        } catch (e: SerializationException) {
            throw NarratorException("Invalid JSON from narrator", e, transient = false)
        } catch (e: IllegalArgumentException) {
            throw NarratorException("Invalid JSON from narrator", e, transient = false)
        }

    private fun String.toJson() = JsonPrimitive(this)

    companion object {
        /** Cuts at the last sentence end within [max] characters. */
        fun trimToLength(
            text: String,
            max: Int,
        ): String {
            if (text.length <= max) return text
            val cut = text.substring(0, max)
            val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
            return if (end > max / 3) cut.substring(0, end + 1) else cut.trimEnd() + "…"
        }
    }
}

@kotlinx.serialization.Serializable
private data class AdventureDto(
    val title: String,
    val premise: String? = null,
    val scenes: List<SceneDto>,
) {
    fun toAdventure(fallbackPremise: String) =
        Adventure(title, premise?.takeIf { it.isNotBlank() } ?: fallbackPremise, scenes.map { it.toScene() })
}

@kotlinx.serialization.Serializable
private data class SceneDto(
    val id: String,
    val title: String,
    val location: String = "",
    val description: String,
    val goal: String = "",
    val npcs: List<Npc> = emptyList(),
    val encounter: Encounter? = null,
    @kotlinx.serialization.SerialName("magic_item") val magicItem: Boolean = false,
) {
    fun toScene() =
        Scene(
            id,
            title,
            location,
            description,
            goal,
            npcs,
            encounter?.takeIf { it.monsters.isNotEmpty() }?.let { e ->
                e.copy(monsters = e.monsters.map { it.copy(monster = it.monster.lowercase()) })
            },
            magicItem,
        )
}
