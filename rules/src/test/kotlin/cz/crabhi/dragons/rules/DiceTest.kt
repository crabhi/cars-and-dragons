package cz.crabhi.dragons.rules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiceTest {
    @Test
    fun `seeded dice are reproducible`() {
        val a = SeededDice(42)
        val b = SeededDice(42)
        val rollsA = List(100) { a.roll(20) }
        val rollsB = List(100) { b.roll(20) }
        assertEquals(rollsA, rollsB)
    }

    @Test
    fun `seeded dice resume from stored state`() {
        val a = SeededDice(7)
        repeat(10) { a.d20() }
        val resumed = SeededDice(a.state)
        assertEquals(List(20) { a.d20() }, List(20) { resumed.d20() })
    }

    @Test
    fun `rolls stay in range and cover all faces`() {
        val dice = SeededDice(1)
        val seen = List(2000) { dice.roll(6) }.toSet()
        assertEquals((1..6).toSet(), seen)
    }

    @Test
    fun `dice expressions parse and print`() {
        assertEquals(DiceExpr(2, 4, 1), d("2d4+1"))
        assertEquals(DiceExpr(1, 8, -1), d("1d8-1"))
        assertEquals(DiceExpr(1, 6, 0), d("d6"))
        assertEquals(DiceExpr(0, 0, 3), d("3"))
        assertEquals("2d4+1", d("2d4+1").toString())
        assertEquals("1d8-1", d("1d8-1").toString())
        assertFailsWith<IllegalArgumentException> { d("abc") }
    }

    @Test
    fun `dice expression bounds`() {
        val expr = d("2d4+2")
        val dice = SeededDice(3)
        repeat(200) { assertTrue(expr.roll(dice) in expr.min..expr.max) }
        assertEquals(7.0, expr.average)
    }

    @Test
    fun `fixed dice return scripted values`() {
        val dice = FixedDice(3, 25, 0)
        assertEquals(3, dice.roll(20))
        assertEquals(20, dice.roll(20))
        assertEquals(1, dice.roll(20))
    }
}
