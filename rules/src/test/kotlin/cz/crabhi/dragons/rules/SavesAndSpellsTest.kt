package cz.crabhi.dragons.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavesAndSpellsTest {
    @Test
    fun `save tables by class group`() {
        assertEquals(10, CharacterClass.CLERIC.saves(1).target(SaveCategory.PARALYZATION_POISON_DEATH))
        assertEquals(11, CharacterClass.MAGE.saves(1).target(SaveCategory.ROD_STAFF_WAND))
        assertEquals(17, CharacterClass.FIGHTER.saves(1).target(SaveCategory.SPELL))
        assertEquals(16, CharacterClass.FIGHTER.saves(3).target(SaveCategory.SPELL))
        assertEquals(12, CharacterClass.THIEF.saves(2).target(SaveCategory.PETRIFICATION_POLYMORPH))
        assertEquals(20, SaveTable.warrior(0).target(SaveCategory.BREATH_WEAPON))
    }

    @Test
    fun `dwarves get constitution bonus against spells`() {
        val thorin = Pregens.create("thorin") // CON 16 => +4
        assertEquals(4, SavingThrows.bonus(thorin, SaveCategory.SPELL, mental = false))
        assertEquals(0, SavingThrows.bonus(thorin, SaveCategory.BREATH_WEAPON, mental = false))
        val save = SavingThrows.roll(thorin, SaveCategory.SPELL, FixedDice(13))
        assertTrue(save.success)
    }

    @Test
    fun `magic missile always hits`() {
        val mage = Pregens.create("lianna")
        mage.memorized = mutableListOf(Spell.MAGIC_MISSILE)
        val goblin = Bestiary.spawn("goblin", "g", FixedDice(7))
        val result = Spellcasting.cast(mage, Spell.MAGIC_MISSILE, listOf(goblin), FixedDice(4))
        val effect = assertIs<SpellEffect.Damaged>(result.effects.single())
        assertEquals(5, effect.amount)
        assertTrue(mage.memorized.isEmpty())
    }

    @Test
    fun `sleep affects weakest first within budget and skips undead`() {
        val mage = Pregens.create("lianna")
        val skeleton = Bestiary.spawn("skeleton", "sk", FixedDice(5))
        val gnoll = Bestiary.spawn("gnoll", "gn", FixedDice(5, 5))
        val goblins = (1..3).map { Bestiary.spawn("goblin", "g$it", FixedDice(5)) }
        // 2d4 = 2+1 = 3 HD budget: three goblins (1 each), then gnoll (2) does not fit.
        val result = Spellcasting.cast(mage, Spell.SLEEP, goblins + gnoll + skeleton, FixedDice(2, 1))
        assertTrue(goblins.all { it.has(ConditionType.ASLEEP) })
        assertFalse(gnoll.has(ConditionType.ASLEEP))
        assertTrue(result.effects.any { it is SpellEffect.Resisted && it.targetId == "sk" })
    }

    @Test
    fun `cure light wounds revives unconscious ally`() {
        val cleric = Pregens.create("bedrich")
        val pip = Pregens.create("pip")
        Combat.applyDamage(pip, pip.hp + 2)
        assertEquals(Health.UNCONSCIOUS, pip.health)
        Spellcasting.cast(cleric, Spell.CURE_LIGHT_WOUNDS, listOf(pip), FixedDice(6))
        assertEquals(4, pip.hp)
        assertEquals(Health.OK, pip.health)
    }

    @Test
    fun `charm person needs a person and a failed save`() {
        val mage = Pregens.create("lianna")
        mage.memorized = mutableListOf(Spell.CHARM_PERSON, Spell.CHARM_PERSON, Spell.CHARM_PERSON)
        val wolf = Bestiary.spawn("wolf", "w", FixedDice(5, 5))
        val r1 = Spellcasting.cast(mage, Spell.CHARM_PERSON, listOf(wolf), FixedDice(1))
        assertEquals(ResistReason.NOT_A_PERSON, (r1.effects.single() as SpellEffect.Resisted).reason)
        val bandit = Bestiary.spawn("bandit", "b", FixedDice(4))
        Spellcasting.cast(mage, Spell.CHARM_PERSON, listOf(bandit), FixedDice(19))
        assertFalse(bandit.has(ConditionType.CHARMED))
        Spellcasting.cast(mage, Spell.CHARM_PERSON, listOf(bandit), FixedDice(3))
        assertTrue(bandit.has(ConditionType.CHARMED))
        assertFalse(bandit.canAct)
    }

    @Test
    fun `bless and shield apply conditions`() {
        val cleric = Pregens.create("bedrich")
        cleric.memorized = mutableListOf(Spell.BLESS)
        val party = listOf(cleric, Pregens.create("kira"))
        Spellcasting.cast(cleric, Spell.BLESS, party, FixedDice())
        assertTrue(party.all { it.has(ConditionType.BLESSED) })
        val mage = Pregens.create("mira")
        mage.memorized = mutableListOf(Spell.SHIELD)
        Spellcasting.cast(mage, Spell.SHIELD, listOf(mage), FixedDice())
        assertEquals(4, mage.armorClass())
        assertEquals(2, mage.armorClass(vsMissile = true))
    }

    @Test
    fun `command makes target lose an action and undead are immune`() {
        val cleric = Pregens.create("bedrich")
        cleric.memorized = mutableListOf(Spell.COMMAND, Spell.COMMAND)
        val orc = Bestiary.spawn("orc", "o", FixedDice(4))
        Spellcasting.cast(cleric, Spell.COMMAND, listOf(orc), FixedDice())
        assertTrue(orc.has(ConditionType.COMMANDED))
        val zombie = Bestiary.spawn("zombie", "z", FixedDice(4, 4))
        val r = Spellcasting.cast(cleric, Spell.COMMAND, listOf(zombie), FixedDice())
        assertEquals(ResistReason.IMMUNE, (r.effects.single() as SpellEffect.Resisted).reason)
    }

    @Test
    fun `rest restores memorized spells`() {
        val cleric = Pregens.create("bedrich")
        val before = cleric.memorized.toList()
        cleric.memorized.clear()
        Spellcasting.rest(cleric)
        assertEquals(before, cleric.memorized)
    }

    @Test
    fun `spell lookup by id`() {
        assertEquals(Spell.MAGIC_MISSILE, Spell.byId("magic_missile"))
        assertEquals(Spell.CURE_LIGHT_WOUNDS, Spell.byId("Cure Light Wounds"))
        assertEquals(10, Spell.entries.size)
    }
}
