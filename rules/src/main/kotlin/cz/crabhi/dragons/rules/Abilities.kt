package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

/** The six ability scores (3..18, no exceptional strength in v1). */
@Serializable
data class Abilities(
    val str: Int,
    val dex: Int,
    val con: Int,
    val int: Int,
    val wis: Int,
    val cha: Int,
) {
    init {
        listOf(str, dex, con, int, wis, cha).forEach { require(it in 3..19) { "Ability score out of range: $it" } }
    }

    fun get(ability: Ability): Int =
        when (ability) {
            Ability.STR -> str
            Ability.DEX -> dex
            Ability.CON -> con
            Ability.INT -> int
            Ability.WIS -> wis
            Ability.CHA -> cha
        }

    /** Strength attack adjustment (PHB table 1). */
    val strengthHit: Int
        get() =
            when {
                str <= 3 -> -3
                str <= 5 -> -2
                str <= 7 -> -1
                str <= 16 -> 0
                else -> 1
            }

    /** Strength damage adjustment (PHB table 1). */
    val strengthDamage: Int
        get() =
            when {
                str <= 5 -> -1
                str <= 15 -> 0
                str <= 17 -> 1
                else -> 2
            }

    /** Dexterity missile attack adjustment (PHB table 2). */
    val dexMissile: Int
        get() =
            when {
                dex <= 3 -> -3
                dex == 4 -> -2
                dex == 5 -> -1
                dex <= 15 -> 0
                dex == 16 -> 1
                else -> 2
            }

    /** Dexterity defensive adjustment to (descending) AC; negative is better (PHB table 2). */
    val dexDefense: Int
        get() =
            when {
                dex <= 3 -> 4
                dex <= 6 -> 7 - dex
                dex <= 14 -> 0
                dex <= 18 -> 14 - dex
                else -> -4
            }

    /** Constitution hit point adjustment per hit die (PHB table 3). Warriors get the higher values. */
    fun conHp(warrior: Boolean): Int =
        when {
            con <= 3 -> -2
            con <= 6 -> -1
            con <= 14 -> 0
            con == 15 -> 1
            con == 16 -> 2
            con == 17 -> if (warrior) 3 else 2
            else -> if (warrior) 4 else 2
        }

    /** Wisdom magical defense adjustment, applies to saves against mind-affecting spells (PHB table 5). */
    val wisMagicDefense: Int
        get() =
            when {
                wis <= 3 -> -3
                wis == 4 -> -2
                wis <= 7 -> -1
                wis <= 14 -> 0
                wis <= 18 -> wis - 14
                else -> 4
            }

    /** Bonus 1st-level priest spells from high wisdom (PHB table 5, 1st-level column only). */
    val bonusPriestSpells: Int
        get() =
            when {
                wis >= 14 -> 2
                wis == 13 -> 1
                else -> 0
            }

    /** Constitution-based save bonus of dwarves and halflings (PHB chapter 2). */
    val stoutSaveBonus: Int
        get() =
            when {
                con >= 18 -> 5
                con >= 14 -> 4
                con >= 11 -> 3
                con >= 7 -> 2
                con >= 4 -> 1
                else -> 0
            }

    fun adjusted(race: Race): Abilities =
        copy(
            str = str + race.strAdjust,
            dex = dex + race.dexAdjust,
            con = con + race.conAdjust,
            cha = cha + race.chaAdjust,
        )
}

enum class Ability { STR, DEX, CON, INT, WIS, CHA }
