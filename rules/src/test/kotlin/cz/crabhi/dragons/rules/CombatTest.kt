package cz.crabhi.dragons.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CombatTest {
    private fun goblin(hp: Int = 5) = Bestiary.spawn("goblin", "goblin-1", FixedDice(hp))

    @Test
    fun `hit when d20 plus bonus reaches thac0 minus ac`() {
        val thorin = Pregens.create("thorin") // THAC0 20, STR 17 => +1 to hit, +1 damage
        val target = goblin(7) // AC 6 => needs 14
        val miss = Combat.attack(thorin, target, thorin.attacks().first(), FixedDice(12))
        assertFalse(miss.hit)
        assertEquals(14, miss.needed)
        val hit = Combat.attack(thorin, target, thorin.attacks().first(), FixedDice(13, 4))
        assertTrue(hit.hit)
        assertEquals(5, hit.damage) // 1d8 = 4, +1 strength
        assertEquals(1, target.hp) // 1d8-1 = 6 hp
    }

    @Test
    fun `natural 20 always hits and natural 1 always misses`() {
        val lianna = Pregens.create("lianna")
        val ogre = Bestiary.spawn("ogre", "ogre-1", FixedDice(8, 8, 8, 8))
        ogre.naturalAc.let { assertEquals(5, it) }
        assertTrue(Combat.attack(lianna, ogre, lianna.attacks().first(), FixedDice(20, 1)).hit)
        val strong = Bestiary.spawn("bugbear", "b", FixedDice(8, 8, 8))
        val weak = goblin().apply { addCondition(Condition(ConditionType.BLESSED, 3)) }
        assertFalse(Combat.attack(strong, weak, strong.attacks().first(), FixedDice(1)).hit)
    }

    @Test
    fun `player characters fall unconscious at zero and die at minus ten`() {
        val pip = Pregens.create("pip")
        Combat.applyDamage(pip, pip.hp)
        assertEquals(Health.UNCONSCIOUS, pip.health)
        assertFalse(pip.canAct)
        Combat.applyDamage(pip, 9)
        assertEquals(-9, pip.hp)
        assertEquals(Health.UNCONSCIOUS, pip.health)
        Combat.applyDamage(pip, 5)
        assertEquals(Health.DEAD, pip.health)
        assertEquals(-10, pip.hp)
        assertEquals(0, Combat.heal(pip, 5))
    }

    @Test
    fun `monsters die at zero`() {
        val g = goblin(3)
        Combat.applyDamage(g, 3)
        assertEquals(Health.DEAD, g.health)
    }

    @Test
    fun `helpless targets are hit automatically`() {
        val g = goblin(7)
        g.addCondition(Condition(ConditionType.ASLEEP))
        val lianna = Pregens.create("lianna")
        val result = Combat.attack(lianna, g, lianna.attacks().first(), FixedDice(2))
        assertTrue(result.autoHit && result.hit)
        assertFalse(g.has(ConditionType.ASLEEP), "damage wakes sleepers")
    }

    @Test
    fun `initiative rerolls ties and lower goes first`() {
        val init = Combat.rollInitiative(FixedDice(5, 5, 3, 8))
        assertEquals(1, init.rerolls)
        assertEquals(Side.PARTY, init.first)
        assertEquals(Side.MONSTERS, Combat.rollInitiative(FixedDice(9, 2)).first)
    }

    @Test
    fun `morale check on 2d10`() {
        assertTrue(Combat.moraleHolds(10, FixedDice(5, 5)))
        assertFalse(Combat.moraleHolds(10, FixedDice(5, 6)))
    }

    @Test
    fun `conditions expire at end of round`() {
        val g = goblin()
        g.addCondition(Condition(ConditionType.BLINDED, 2))
        g.addCondition(Condition(ConditionType.CHARMED))
        assertTrue(Combat.endRound(listOf(g)).isEmpty())
        val expired = Combat.endRound(listOf(g))
        assertEquals(listOf(ConditionType.BLINDED), expired.map { it.second })
        assertTrue(g.has(ConditionType.CHARMED))
    }

    @Test
    fun `ghoul paralysis does not affect elves`() {
        val ghoul = Bestiary.spawn("ghoul", "ghoul-1", FixedDice(8, 8))
        val lianna = Pregens.create("lianna")
        val claw = ghoul.attacks().first()
        val result = Combat.attack(ghoul, lianna, claw, FixedDice(20, 1))
        assertTrue(result.effect!!.immune)
        val kira = Pregens.create("kira")
        val vsHuman = Combat.attack(ghoul, kira, claw, FixedDice(20, 1, 2, 3))
        assertTrue(kira.has(ConditionType.PARALYZED), "failed save paralyzes: $vsHuman")
    }

    @Test
    fun `poison adds damage on failed save`() {
        val spider = Bestiary.spawn("large_spider", "s", FixedDice(5))
        val kira = Pregens.create("kira")
        val hpBefore = kira.hp
        val result = Combat.attack(spider, kira, spider.attacks().first(), FixedDice(20, 2, 4, 4))
        assertEquals(1 + 8, result.damage)
        assertEquals(hpBefore - 9, kira.hp)
    }

    @Test
    fun `protection from evil penalizes evil attackers`() {
        val orc = Bestiary.spawn("orc", "o", FixedDice(4))
        val kira = Pregens.create("kira")
        assertEquals(0, Combat.toHitBonus(orc, orc.attacks().first(), kira))
        kira.addCondition(Condition(ConditionType.PROTECTED_FROM_EVIL, 3))
        assertEquals(-2, Combat.toHitBonus(orc, orc.attacks().first(), kira))
    }

    @Test
    fun `weapon damage depends on target size`() {
        val kira = Pregens.create("kira")
        val sword = kira.attacks().first()
        val ogre = Bestiary.spawn("ogre", "o", FixedDice(8, 8, 8, 8))
        assertEquals(d("1d12+1"), Combat.damageDice(kira, sword, ogre))
        assertEquals(d("1d8+1"), Combat.damageDice(kira, sword, goblin()))
    }
}
