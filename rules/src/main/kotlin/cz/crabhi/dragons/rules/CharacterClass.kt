package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

/** The four class groups of 2nd Edition; they share THAC0 progression and saving throws. */
enum class ClassGroup { WARRIOR, PRIEST, ROGUE, WIZARD }

enum class CasterType { PRIEST, WIZARD }

@Serializable
enum class CharacterClass(
    val group: ClassGroup,
    val hitDie: Int,
    /** XP needed to reach level 1, 2, 3. */
    private val xpTable: List<Int>,
    val casterType: CasterType? = null,
    val allowedWeapons: Set<Weapon>,
    val allowedArmor: Set<Armor>,
    val shieldAllowed: Boolean,
) {
    FIGHTER(
        ClassGroup.WARRIOR,
        10,
        listOf(0, 2000, 4000),
        allowedWeapons = Weapon.entries.toSet(),
        allowedArmor = Armor.entries.toSet(),
        shieldAllowed = true,
    ),
    CLERIC(
        ClassGroup.PRIEST,
        8,
        listOf(0, 1500, 3000),
        CasterType.PRIEST,
        allowedWeapons = Weapon.entries.filter { it.blunt }.toSet(),
        allowedArmor = Armor.entries.toSet(),
        shieldAllowed = true,
    ),
    MAGE(
        ClassGroup.WIZARD,
        4,
        listOf(0, 2500, 5000),
        CasterType.WIZARD,
        allowedWeapons = setOf(Weapon.DAGGER, Weapon.QUARTERSTAFF, Weapon.SLING),
        allowedArmor = setOf(Armor.NONE),
        shieldAllowed = false,
    ),
    THIEF(
        ClassGroup.ROGUE,
        6,
        listOf(0, 1250, 2500),
        allowedWeapons =
            setOf(
                Weapon.DAGGER,
                Weapon.SHORT_SWORD,
                Weapon.LONG_SWORD,
                Weapon.QUARTERSTAFF,
                Weapon.SLING,
                Weapon.SHORT_BOW,
                Weapon.LIGHT_CROSSBOW,
                Weapon.MACE,
            ),
        allowedArmor = setOf(Armor.NONE, Armor.LEATHER, Armor.STUDDED_LEATHER),
        shieldAllowed = false,
    ),
    ;

    val id: String get() = name.lowercase()

    val isWarrior: Boolean get() = group == ClassGroup.WARRIOR

    fun thac0(level: Int): Int {
        val lvl = level.coerceIn(1, MAX_LEVEL)
        return when (group) {
            ClassGroup.WARRIOR -> 21 - lvl
            ClassGroup.PRIEST -> 20 - 2 * ((lvl - 1) / 3)
            ClassGroup.ROGUE -> 20 - (lvl - 1) / 2
            ClassGroup.WIZARD -> 20 - (lvl - 1) / 3
        }
    }

    fun xpForLevel(level: Int): Int = xpTable[level.coerceIn(1, MAX_LEVEL) - 1]

    fun levelForXp(xp: Int): Int = xpTable.indexOfLast { xp >= it } + 1

    fun saves(level: Int): SaveTable = SaveTable.forGroup(group, level)

    /** Number of 1st-level spell slots at [level]. Only 1st-level spells exist in v1. */
    fun firstLevelSlots(
        level: Int,
        abilities: Abilities,
    ): Int =
        when (casterType) {
            null -> 0
            CasterType.WIZARD -> if (level <= 1) 1 else 2
            CasterType.PRIEST -> (if (level <= 1) 1 else 2) + abilities.bonusPriestSpells
        }

    companion object {
        const val MAX_LEVEL = 3

        fun byId(id: String): CharacterClass? = entries.firstOrNull { it.id == id.lowercase() }
    }
}
