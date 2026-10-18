package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.Dice
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

data class TacticsChoice(
    val index: Int,
    val confidence: Double,
    val source: String,
)

/** Chooses among legal monster actions enumerated by the engine. Never invents actions. */
interface TacticsAdvisor {
    suspend fun choose(
        state: GameState,
        monster: Combatant,
        options: List<MonsterOption>,
    ): TacticsChoice
}

/** Focus fire on the weakest standing character, with some randomness so fights do not feel scripted. */
class HeuristicTactics(
    private val dice: Dice,
) : TacticsAdvisor {
    override suspend fun choose(
        state: GameState,
        monster: Combatant,
        options: List<MonsterOption>,
    ): TacticsChoice {
        val attacks = options.withIndex().filter { it.value is MonsterOption.AttackTarget }
        if (attacks.isEmpty()) {
            val flee = options.indexOf(MonsterOption.Flee)
            return TacticsChoice(if (flee >= 0) flee else 0, 1.0, "heuristic")
        }
        val fleeIndex = options.indexOf(MonsterOption.Flee)
        if (fleeIndex >= 0 && dice.percent(FLEE_PERCENT)) return TacticsChoice(fleeIndex, 1.0, "heuristic")
        if (dice.percent(RANDOM_TARGET_PERCENT)) {
            return TacticsChoice(attacks[dice.roll(attacks.size) - 1].index, 1.0, "heuristic")
        }
        val weakest =
            attacks.minBy { (_, option) ->
                state.find((option as MonsterOption.AttackTarget).targetId)?.hp ?: Int.MAX_VALUE
            }
        return TacticsChoice(weakest.index, 1.0, "heuristic")
    }

    companion object {
        const val FLEE_PERCENT = 30
        const val RANDOM_TARGET_PERCENT = 30
    }
}

data class JevConfig(
    val endpoint: String,
    val apiKey: String,
    val minConfidence: Double = 0.5,
)

/**
 * Tactics from Jev (TypeSafe AI). Sends the legal options and expects `{"choice": <index>, "confidence": <0..1>}`.
 * Any failure, an out-of-range choice or low confidence falls back to [fallback].
 */
class JevTacticsAdvisor(
    private val config: JevConfig,
    private val fallback: TacticsAdvisor,
    private val http: HttpClient = HttpClient(readTimeoutMs = 8_000),
    private val glossary: Glossary = Glossary.czech,
) : TacticsAdvisor {
    override suspend fun choose(
        state: GameState,
        monster: Combatant,
        options: List<MonsterOption>,
    ): TacticsChoice {
        val body =
            buildJsonObject {
                put("task", "choose_monster_action")
                put(
                    "instructions",
                    "Pick the index of the best legal action for the monster. Reply with JSON {\"choice\": int, \"confidence\": 0..1}.",
                )
                put("monster", PromptContext.combatant(monster, glossary))
                put("party", buildJsonArray { state.party.forEach { add(PromptContext.combatant(it, glossary)) } })
                put(
                    "allies",
                    buildJsonArray {
                        state.monsters.filter { it.id != monster.id }.forEach { add(PromptContext.combatant(it, glossary)) }
                    },
                )
                put("options", buildJsonArray { options.forEach { add(GameJson.encodeToJsonElement(MonsterOption.serializer(), it)) } })
            }.toString()
        return try {
            val text = http.postJson(config.endpoint, mapOf("Authorization" to "Bearer ${config.apiKey}"), body)
            val obj = GameJson.parseToJsonElement(text).jsonObject
            val choice = obj["choice"]?.jsonPrimitive?.intOrNull
            val confidence = obj["confidence"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (choice == null || choice !in options.indices || confidence < config.minConfidence) {
                fallback.choose(state, monster, options)
            } else {
                TacticsChoice(choice, confidence, "jev")
            }
        } catch (e: IOException) {
            fallback.choose(state, monster, options)
        } catch (e: SerializationException) {
            fallback.choose(state, monster, options)
        } catch (e: IllegalArgumentException) {
            fallback.choose(state, monster, options)
        }
    }
}
