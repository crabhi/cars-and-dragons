package cz.crabhi.dragons.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A structured action proposed by the narrator from player speech. The engine validates it and
 * resolves only legal ones. Ids are English.
 */
@Serializable
sealed interface ProposedAction {
    @Serializable
    @SerialName("attack")
    data class Attack(
        val target: String? = null,
        val ranged: Boolean = false,
    ) : ProposedAction

    @Serializable
    @SerialName("cast")
    data class Cast(
        val spell: String,
        val target: String? = null,
    ) : ProposedAction

    @Serializable
    @SerialName("use_item")
    data class UseItem(
        val item: String,
        val target: String? = null,
    ) : ProposedAction

    @Serializable
    @SerialName("defend")
    data object Defend : ProposedAction

    @Serializable
    @SerialName("flee")
    data object Flee : ProposedAction

    @Serializable
    @SerialName("rest")
    data object Rest : ProposedAction

    @Serializable
    @SerialName("advance")
    data object Advance : ProposedAction

    @Serializable
    @SerialName("check")
    data class Check(
        val ability: String,
        val proficiency: String? = null,
    ) : ProposedAction

    /** Free-form role play with no mechanical effect (talking, looking around). */
    @Serializable
    @SerialName("narrative")
    data class Narrative(
        val summary: String,
    ) : ProposedAction

    /** Out-of-game commands: status, repeat, quit. */
    @Serializable
    @SerialName("meta")
    data class Meta(
        val command: MetaCommand,
    ) : ProposedAction
}

@Serializable
enum class MetaCommand {
    @SerialName("status")
    STATUS,

    @SerialName("repeat")
    REPEAT,

    @SerialName("quit")
    QUIT,
}

/** Why the engine rejected an action. Each code has a Czech message in the glossary (`reject.<code>`). */
@Serializable
enum class Rejection {
    NOT_YOUR_TURN,
    CANNOT_ACT,
    NO_ENEMIES,
    UNKNOWN_TARGET,
    INVALID_TARGET,
    UNKNOWN_SPELL,
    SPELL_NOT_KNOWN,
    SPELL_NOT_MEMORIZED,
    NO_ITEM,
    UNKNOWN_ITEM,
    NOT_IN_COMBAT,
    IN_COMBAT,
    ALREADY_RESTED,
    NO_RANGED_WEAPON,
    UNKNOWN_ABILITY,
    ;

    val key: String get() = "reject.${name.lowercase()}"
}
