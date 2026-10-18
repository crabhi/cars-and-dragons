package cz.crabhi.dragons.rules

/** Pre-generated level 1 characters. v1 has no character creation. */
object Pregens {
    private val wizardPriority = listOf(Spell.SLEEP, Spell.MAGIC_MISSILE, Spell.SHIELD, Spell.CHARM_PERSON)
    private val priestPriority = listOf(Spell.CURE_LIGHT_WOUNDS, Spell.BLESS, Spell.CURE_LIGHT_WOUNDS, Spell.COMMAND)

    private val priestSpells = Spell.entries.filter { CasterType.PRIEST in it.casters }
    private val wizardSpells =
        listOf(
            Spell.SLEEP,
            Spell.MAGIC_MISSILE,
            Spell.SHIELD,
            Spell.CHARM_PERSON,
            Spell.LIGHT,
            Spell.DETECT_MAGIC,
            Spell.PROTECTION_FROM_EVIL,
        )

    private data class Spec(
        val id: String,
        val name: String,
        val race: Race,
        val cls: CharacterClass,
        val abilities: Abilities,
        val armor: Armor,
        val shield: Boolean,
        val weapons: List<Weapon>,
        val proficiencies: List<Proficiency>,
    )

    private val specs =
        listOf(
            Spec(
                "thorin",
                "Thorin",
                Race.DWARF,
                CharacterClass.FIGHTER,
                Abilities(str = 17, dex = 12, con = 15, int = 10, wis = 11, cha = 9),
                Armor.CHAIN_MAIL,
                true,
                listOf(Weapon.BATTLE_AXE, Weapon.LIGHT_CROSSBOW),
                listOf(Proficiency.ENDURANCE, Proficiency.MOUNTAINEERING),
            ),
            Spec(
                "lianna",
                "Lianna",
                Race.ELF,
                CharacterClass.MAGE,
                Abilities(str = 9, dex = 15, con = 13, int = 17, wis = 12, cha = 13),
                Armor.NONE,
                false,
                listOf(Weapon.DAGGER, Weapon.SLING),
                listOf(Proficiency.SPELLCRAFT, Proficiency.ANCIENT_HISTORY),
            ),
            Spec(
                "bedrich",
                "Bedřich",
                Race.HUMAN,
                CharacterClass.CLERIC,
                Abilities(str = 13, dex = 10, con = 14, int = 10, wis = 16, cha = 12),
                Armor.CHAIN_MAIL,
                true,
                listOf(Weapon.MACE, Weapon.SLING),
                listOf(Proficiency.HEALING, Proficiency.RELIGION),
            ),
            Spec(
                "pip",
                "Pip",
                Race.HALFLING,
                CharacterClass.THIEF,
                Abilities(str = 11, dex = 17, con = 13, int = 12, wis = 9, cha = 11),
                Armor.LEATHER,
                false,
                listOf(Weapon.SHORT_SWORD, Weapon.SLING),
                listOf(Proficiency.HIDING, Proficiency.MOVING_SILENTLY, Proficiency.OPENING_LOCKS, Proficiency.LISTENING),
            ),
            Spec(
                "kira",
                "Kira",
                Race.HUMAN,
                CharacterClass.FIGHTER,
                Abilities(str = 16, dex = 14, con = 15, int = 11, wis = 10, cha = 12),
                Armor.BANDED_MAIL,
                false,
                listOf(Weapon.LONG_SWORD, Weapon.SHORT_BOW),
                listOf(Proficiency.TRACKING, Proficiency.SWIMMING),
            ),
            Spec(
                "mira",
                "Míra",
                Race.HUMAN,
                CharacterClass.MAGE,
                Abilities(str = 10, dex = 13, con = 12, int = 16, wis = 13, cha = 14),
                Armor.NONE,
                false,
                listOf(Weapon.QUARTERSTAFF, Weapon.DAGGER),
                listOf(Proficiency.SPELLCRAFT, Proficiency.DIRECTION_SENSE),
            ),
        )

    val ids: List<String> get() = specs.map { it.id }

    /** Default party for [count] players: a balanced mix in this order. */
    fun party(count: Int): List<Combatant> {
        require(count in 1..specs.size) { "Party size must be 1..${specs.size}" }
        return specs.take(count).map { create(it.id) }
    }

    fun create(id: String): Combatant {
        val s = specs.first { it.id == id }
        val abilities = s.abilities.adjusted(s.race)
        val hp = s.cls.hitDie + abilities.conHp(s.cls.isWarrior) // level 1 characters start with max HP
        val spellbook =
            when (s.cls.casterType) {
                CasterType.WIZARD -> wizardSpells
                CasterType.PRIEST -> priestSpells
                null -> emptyList()
            }
        val c =
            Combatant(
                id = s.id,
                name = s.name,
                side = Side.PARTY,
                level = 1,
                hp = hp,
                maxHp = hp,
                characterClass = s.cls,
                race = s.race,
                abilities = abilities,
                armor = s.armor,
                shield = s.shield,
                weapons = s.weapons,
                spellbook = spellbook,
                inventory = mutableMapOf(Item.HEALING_POTION to 1, Item.TORCH to 2),
                proficiencies = s.proficiencies,
            )
        when (s.cls.casterType) {
            CasterType.WIZARD -> Spellcasting.prepareDefault(c, wizardPriority)
            CasterType.PRIEST -> Spellcasting.prepareDefault(c, priestPriority)
            null -> Unit
        }
        return c
    }

    fun spellPriority(c: Combatant): List<Spell> =
        when (c.characterClass?.casterType) {
            CasterType.WIZARD -> wizardPriority
            CasterType.PRIEST -> priestPriority
            null -> emptyList()
        }
}
