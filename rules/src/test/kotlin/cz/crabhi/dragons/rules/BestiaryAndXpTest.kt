package cz.crabhi.dragons.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BestiaryAndXpTest {
    @Test
    fun `bestiary has about twenty monsters with sane stats`() {
        assertTrue(Bestiary.all.size >= 20)
        assertEquals(Bestiary.all.size, Bestiary.ids.size, "ids are unique")
        for (m in Bestiary.all) {
            assertTrue(m.ac in -2..10, m.id)
            assertTrue(m.thac0 in 10..20, m.id)
            assertTrue(m.attacks.isNotEmpty(), m.id)
            assertTrue(m.xp > 0, m.id)
            assertTrue(m.hp.max >= 1, m.id)
        }
    }

    @Test
    fun `spawned monsters have rolled hit points`() {
        val dice = SeededDice(99)
        repeat(50) {
            val orc = Bestiary.spawn("orc", "orc-$it", dice)
            assertTrue(orc.hp in 1..8)
            assertEquals(orc.hp, orc.maxHp)
        }
        assertEquals(1, Bestiary.spawn("goblin", "g", FixedDice(1)).hp, "minimum 1 hp")
    }

    @Test
    fun `xp is split and levels are gained`() {
        val party = Pregens.party(2)
        Combat.applyDamage(party[1], 30) // Lianna dies, gets nothing
        val award = Experience.award(party, 2100, FixedDice(6))
        assertEquals(listOf("thorin"), award.recipients)
        assertEquals(2100, party[0].xp)
        assertEquals(2, party[0].level)
        assertEquals(LevelUp("thorin", 2, 8), award.levelUps.single()) // d10 = 6, CON 16 warrior +2
        assertEquals(20, party[0].maxHp)
    }

    @Test
    fun `level is capped at three`() {
        val pip = Pregens.create("pip")
        Experience.award(listOf(pip), 100_000, SeededDice(1))
        assertEquals(3, pip.level)
    }

    @Test
    fun `casters gain slots on level up`() {
        val mage = Pregens.create("lianna")
        assertEquals(1, mage.memorized.size)
        Experience.award(listOf(mage), 2500, FixedDice(3))
        assertEquals(2, mage.memorized.size)
    }
}
