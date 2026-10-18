package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

/** Simple non-weapon proficiencies: roll d20, succeed if the roll is at most ability + modifier. */
@Serializable
enum class Proficiency(
    val ability: Ability,
    val modifier: Int,
) {
    TRACKING(Ability.WIS, 0),
    HEALING(Ability.WIS, -2),
    MOUNTAINEERING(Ability.CON, 0),
    SWIMMING(Ability.STR, 0),
    HIDING(Ability.DEX, -2),
    MOVING_SILENTLY(Ability.DEX, -2),
    OPENING_LOCKS(Ability.DEX, -4),
    SPELLCRAFT(Ability.INT, -2),
    RELIGION(Ability.WIS, 0),
    BLIND_FIGHTING(Ability.WIS, 0),
    ENDURANCE(Ability.CON, 0),
    ANCIENT_HISTORY(Ability.INT, -1),
    DIRECTION_SENSE(Ability.WIS, 1),
    LISTENING(Ability.WIS, 0),
    ;

    val id: String get() = name.lowercase()

    companion object {
        fun byId(id: String): Proficiency? = entries.firstOrNull { it.id == id.lowercase() }
    }
}

@Serializable
data class CheckResult(
    val roll: Int,
    val target: Int,
    val proficient: Boolean,
) {
    val success: Boolean get() = roll <= target
}

object Checks {
    /**
     * Ability check, optionally for a proficiency. A plain check succeeds on a roll at most the ability
     * score. A proficiency check uses the proficiency modifier; characters lacking the proficiency
     * roll against half the ability score, which keeps unskilled attempts possible but unlikely.
     */
    fun check(
        who: Combatant,
        ability: Ability,
        proficiency: Proficiency?,
        dice: Dice,
    ): CheckResult {
        val score = who.abilities?.get(ability) ?: 10
        val proficient = proficiency != null && proficiency in who.proficiencies
        val target =
            when {
                proficiency == null -> score
                proficient -> score + proficiency.modifier
                else -> score / 2
            }
        return CheckResult(dice.d20(), target, proficient)
    }
}
