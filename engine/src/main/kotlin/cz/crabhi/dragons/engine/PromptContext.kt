package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

val GameJson =
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

/** Compact English description of the game state for language-model prompts. */
object PromptContext {
    private const val RECENT_LOG = 8

    fun combatant(
        c: Combatant,
        glossary: Glossary,
    ): JsonObject =
        buildJsonObject {
            put("id", c.id)
            put("name", glossary.combatantLabel(c))
            if (c.isPc) {
                put("class", c.characterClass!!.id)
                put("race", c.race!!.id)
                put("level", c.level)
                put("spells_ready", JsonArray(c.memorized.map { JsonPrimitive(it.id) }))
                put("items", JsonObject(c.inventory.filterValues { it > 0 }.map { (k, v) -> k.id to JsonPrimitive(v) }.toMap()))
                put("weapons", JsonArray(c.weapons.map { JsonPrimitive(it.id) }))
            } else {
                put("monster", c.monsterId)
            }
            put("hp", c.hp)
            put("max_hp", c.maxHp)
            put("ac", c.armorClass())
            put("health", c.health.name.lowercase())
            if (c.fled) put("fled", true)
            if (c.conditions.isNotEmpty()) put("conditions", JsonArray(c.conditions.map { JsonPrimitive(it.type.name.lowercase()) }))
        }

    fun build(
        state: GameState,
        glossary: Glossary,
    ): JsonObject =
        buildJsonObject {
            state.adventure?.let { adv ->
                put("adventure_title", adv.title)
                put("premise", adv.premise)
                put("scene_number", state.sceneIndex + 1)
                put("scene_count", adv.scenes.size)
            }
            state.scene?.let { scene ->
                putJsonObject("scene") {
                    put("id", scene.id)
                    put("title", scene.title)
                    put("location", scene.location)
                    put("description", scene.description)
                    put("goal", scene.goal)
                    put("npcs", GameJson.encodeToJsonElement(scene.npcs))
                    put("final", state.sceneIndex == (state.adventure?.scenes?.lastIndex ?: 0))
                }
                state.adventure?.scenes?.getOrNull(state.sceneIndex + 1)?.let { next ->
                    putJsonObject("next_scene") {
                        put("title", next.title)
                        put("location", next.location)
                    }
                }
            }
            put("phase", state.phase.name.lowercase())
            if (state.phase == Phase.COMBAT) put("round", state.round)
            put("exploration_turns_in_scene", state.explorationTurns)
            putJsonArray("party") { state.party.forEach { add(combatant(it, glossary)) } }
            putJsonArray("monsters") { state.monsters.forEach { add(combatant(it, glossary)) } }
            put(
                "recent_narration",
                buildJsonArray {
                    state.log.takeLast(RECENT_LOG).forEach { e ->
                        addJsonObject {
                            put("speaker", e.speaker)
                            put("text", e.text)
                        }
                    }
                },
            )
        }

    fun events(events: List<GameEvent>) = GameJson.encodeToJsonElement(events)

    fun action(action: ProposedAction?) = action?.let { GameJson.encodeToJsonElement(it) }

    fun options(options: LegalOptions) = GameJson.encodeToJsonElement(options)
}
