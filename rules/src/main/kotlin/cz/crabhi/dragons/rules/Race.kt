package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
enum class Race(
    val strAdjust: Int = 0,
    val dexAdjust: Int = 0,
    val conAdjust: Int = 0,
    val chaAdjust: Int = 0,
    /** Dwarves and halflings add a constitution-based bonus to some saving throws. */
    val stout: Boolean = false,
    /** Percent chance to resist sleep and charm spells. */
    val sleepCharmResistance: Int = 0,
    val infravision: Boolean = false,
) {
    HUMAN,
    DWARF(conAdjust = 1, chaAdjust = -1, stout = true, infravision = true),
    ELF(dexAdjust = 1, conAdjust = -1, sleepCharmResistance = 90, infravision = true),
    HALFLING(strAdjust = -1, dexAdjust = 1, stout = true),
    ;

    val id: String get() = name.lowercase()
}
