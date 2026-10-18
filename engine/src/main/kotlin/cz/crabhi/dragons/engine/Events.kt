package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Ability
import cz.crabhi.dragons.rules.AttackResult
import cz.crabhi.dragons.rules.CheckResult
import cz.crabhi.dragons.rules.ConditionType
import cz.crabhi.dragons.rules.InitiativeResult
import cz.crabhi.dragons.rules.Item
import cz.crabhi.dragons.rules.Proficiency
import cz.crabhi.dragons.rules.SpellResult
import cz.crabhi.dragons.rules.XpAward
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Resolved, factual outcomes produced by the engine. The narrator describes them; it never changes them. */
@Serializable
sealed interface GameEvent {
    @Serializable
    @SerialName("scene_entered")
    data class SceneEntered(
        val index: Int,
        val sceneId: String,
        val finalScene: Boolean,
    ) : GameEvent

    @Serializable
    @SerialName("encounter_started")
    data class EncounterStarted(
        val monsterIds: List<String>,
    ) : GameEvent

    @Serializable
    @SerialName("round_started")
    data class RoundStarted(
        val round: Int,
        val initiative: InitiativeResult,
    ) : GameEvent

    @Serializable
    @SerialName("turn_skipped")
    data class TurnSkipped(
        val actorId: String,
        val reason: ConditionType,
    ) : GameEvent

    @Serializable
    @SerialName("attacked")
    data class Attacked(
        val result: AttackResult,
    ) : GameEvent

    @Serializable
    @SerialName("spell_cast")
    data class SpellCast(
        val result: SpellResult,
    ) : GameEvent

    @Serializable
    @SerialName("item_used")
    data class ItemUsed(
        val actorId: String,
        val item: Item,
        val targetId: String,
        val healed: Int,
        val hpAfter: Int,
    ) : GameEvent

    @Serializable
    @SerialName("defended")
    data class Defended(
        val actorId: String,
    ) : GameEvent

    @Serializable
    @SerialName("flee_attempt")
    data class FleeAttempt(
        val actorId: String,
        val roll: Int,
        val success: Boolean,
    ) : GameEvent

    @Serializable
    @SerialName("monsters_fled")
    data class MonstersFled(
        val monsterIds: List<String>,
    ) : GameEvent

    @Serializable
    @SerialName("conditions_expired")
    data class ConditionsExpired(
        val expired: List<ExpiredCondition>,
    ) : GameEvent

    @Serializable
    @SerialName("check_made")
    data class CheckMade(
        val actorId: String,
        val ability: Ability,
        val proficiency: Proficiency?,
        val result: CheckResult,
    ) : GameEvent

    @Serializable
    @SerialName("narrative_action")
    data class NarrativeAction(
        val actorId: String,
        val summary: String,
    ) : GameEvent

    @Serializable
    @SerialName("rested")
    data class Rested(
        val healed: Map<String, Int>,
    ) : GameEvent

    @Serializable
    @SerialName("encounter_won")
    data class EncounterWon(
        val defeatedIds: List<String>,
        val xp: XpAward,
    ) : GameEvent

    @Serializable
    @SerialName("encounter_escaped")
    data object EncounterEscaped : GameEvent

    @Serializable
    @SerialName("adventure_completed")
    data object AdventureCompleted : GameEvent

    @Serializable
    @SerialName("party_defeated")
    data object PartyDefeated : GameEvent
}

@Serializable
data class ExpiredCondition(
    val combatantId: String,
    val condition: ConditionType,
)
