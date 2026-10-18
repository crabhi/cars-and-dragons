package cz.crabhi.dragons.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CharacterTest {
    @Test
    fun `ability modifiers follow the 2E tables`() {
        val a = Abilities(str = 18, dex = 16, con = 17, int = 10, wis = 15, cha = 10)
        assertEquals(1, a.strengthHit)
        assertEquals(2, a.strengthDamage)
        assertEquals(-2, a.dexDefense)
        assertEquals(1, a.dexMissile)
        assertEquals(3, a.conHp(warrior = true))
        assertEquals(2, a.conHp(warrior = false))
        assertEquals(1, a.wisMagicDefense)
        val weak = Abilities(str = 3, dex = 3, con = 3, int = 3, wis = 3, cha = 3)
        assertEquals(-3, weak.strengthHit)
        assertEquals(4, weak.dexDefense)
        assertEquals(-2, weak.conHp(false))
    }

    @Test
    fun `race adjusts abilities`() {
        val base = Abilities(10, 10, 10, 10, 10, 10)
        assertEquals(11, base.adjusted(Race.DWARF).con)
        assertEquals(9, base.adjusted(Race.DWARF).cha)
        assertEquals(11, base.adjusted(Race.ELF).dex)
        assertEquals(9, base.adjusted(Race.HALFLING).str)
    }

    @Test
    fun `thac0 by class and level`() {
        assertEquals(20, CharacterClass.FIGHTER.thac0(1))
        assertEquals(18, CharacterClass.FIGHTER.thac0(3))
        assertEquals(20, CharacterClass.CLERIC.thac0(3))
        assertEquals(20, CharacterClass.MAGE.thac0(3))
        assertEquals(20, CharacterClass.THIEF.thac0(2))
        assertEquals(19, CharacterClass.THIEF.thac0(3))
    }

    @Test
    fun `xp thresholds`() {
        assertEquals(1, CharacterClass.THIEF.levelForXp(1249))
        assertEquals(2, CharacterClass.THIEF.levelForXp(1250))
        assertEquals(3, CharacterClass.MAGE.levelForXp(9999))
        assertEquals(2000, CharacterClass.FIGHTER.xpForLevel(2))
    }

    @Test
    fun `spell slots include wisdom bonus for priests`() {
        val wise = Abilities(10, 10, 10, 10, 16, 10)
        assertEquals(3, CharacterClass.CLERIC.firstLevelSlots(1, wise))
        assertEquals(4, CharacterClass.CLERIC.firstLevelSlots(2, wise))
        assertEquals(1, CharacterClass.MAGE.firstLevelSlots(1, wise))
        assertEquals(2, CharacterClass.MAGE.firstLevelSlots(3, wise))
        assertEquals(0, CharacterClass.FIGHTER.firstLevelSlots(3, wise))
    }

    @Test
    fun `pregenerated characters are valid`() {
        for (id in Pregens.ids) {
            val c = Pregens.create(id)
            val cls = c.characterClass!!
            assertTrue(c.hp > 0 && c.hp == c.maxHp, "$id hp")
            assertTrue(c.weapons.all { it in cls.allowedWeapons }, "$id weapons")
            assertTrue(c.armor in cls.allowedArmor, "$id armor")
            assertTrue(!c.shield || cls.shieldAllowed, "$id shield")
            assertEquals(cls.firstLevelSlots(1, c.abilities!!), c.memorized.size, "$id slots")
            assertTrue(c.memorized.all { Spellcasting.knows(c, it) }, "$id knows its spells")
        }
    }

    @Test
    fun `thorin has expected derived stats`() {
        val thorin = Pregens.create("thorin")
        assertEquals(16, thorin.abilities!!.con)
        assertEquals(12, thorin.maxHp)
        assertEquals(4, thorin.armorClass())
        assertEquals(20, thorin.thac0())
    }

    @Test
    fun `proficiency checks`() {
        val pip = Pregens.create("pip")
        // Pip has DEX 18 after racial adjustment; hiding is DEX - 2 = 16.
        assertTrue(Checks.check(pip, Ability.DEX, Proficiency.HIDING, FixedDice(16)).success)
        assertTrue(!Checks.check(pip, Ability.DEX, Proficiency.HIDING, FixedDice(17)).success)
        // Lacking a proficiency halves the ability.
        assertEquals(4, Checks.check(pip, Ability.WIS, Proficiency.TRACKING, FixedDice(1)).target)
        assertEquals(9, Checks.check(pip, Ability.WIS, null, FixedDice(1)).target)
    }

    @Test
    fun `party sizes`() {
        assertEquals(listOf("thorin", "lianna"), Pregens.party(2).map { it.id })
        assertEquals(4, Pregens.party(4).size)
    }
}
