package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Bestiary
import cz.crabhi.dragons.rules.CharacterClass
import cz.crabhi.dragons.rules.ConditionType
import cz.crabhi.dragons.rules.Item
import cz.crabhi.dragons.rules.Pregens
import cz.crabhi.dragons.rules.Proficiency
import cz.crabhi.dragons.rules.Race
import cz.crabhi.dragons.rules.Spell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CzechTest {
    private val g = Glossary.czech

    @Test
    fun `glossary covers every id the game can show or say`() {
        val required =
            Bestiary.ids.map { "monster.$it" } +
                Spell.entries.map { "spell.${it.id}" } +
                Item.entries.map { "item.${it.id}" } +
                Race.entries.map { "race.${it.id}" } +
                CharacterClass.entries.map { "class.${it.id}" } +
                Proficiency.entries.map { "proficiency.${it.id}" } +
                ConditionType.entries.map { "condition.${it.name.lowercase()}" } +
                Rejection.entries.map { it.key } +
                listOf("term.hp", "term.ac", "term.saving_throw", "term.initiative", "term.level", "term.experience")
        val missing = required.filterNot { g.has(it) }
        assertTrue(missing.isEmpty(), "Missing glossary keys: $missing")
    }

    @Test
    fun `countable glossary entries have all three czech plural forms`() {
        val countable =
            g.keys.filter {
                it.startsWith("monster.") ||
                    it.startsWith("item.") ||
                    it in setOf("term.hp", "term.damage", "term.xp_points")
            }
        countable.forEach { assertEquals(3, g.forms(it).size, it) }
        assertEquals("1 život", g.count("term.hp", 1))
        assertEquals("3 životy", g.count("term.hp", 3))
        assertEquals("7 životů", g.count("term.hp", 7))
        assertEquals("0 životů", g.count("term.hp", 0))
        assertEquals("2 goblini", g.count("monster.goblin", 2))
    }

    @Test
    fun `thac0 is never part of the glossary`() {
        assertTrue(g.keys.none { g[it].contains("THAC0", ignoreCase = true) })
    }

    private fun stateInCombat(): GameState {
        val engine = newEngine()
        engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
        return engine.state
    }

    @Test
    fun `offline interpreter understands common czech commands`() {
        val state = stateInCombat()
        val p = OfflineInterpreter(g)
        val thorin = Pregens.create("thorin")
        val lianna = state.party.first { it.id == "lianna" }
        val bedrich = state.party.first { it.id == "bedrich" }
        assertEquals(ProposedAction.Attack("goblin-2"), p.interpret(state, thorin, "Útočím sekerou na druhého goblina"))
        assertEquals(ProposedAction.Attack("goblin-1", ranged = true), p.interpret(state, thorin, "Střílím z kuše na goblina"))
        assertEquals(ProposedAction.Cast("sleep", null), p.interpret(state, lianna, "Sesílám spánek"))
        assertEquals(ProposedAction.Cast("cure_light_wounds", "thorin"), p.interpret(state, bedrich, "Vyléčím Thorina"))
        assertEquals(ProposedAction.UseItem("healing_potion", null), p.interpret(state, thorin, "Vypiju lektvar"))
        assertEquals(ProposedAction.Flee, p.interpret(state, thorin, "Utečeme!"))
        assertEquals(ProposedAction.Defend, p.interpret(state, thorin, "Bráním se štítem"))
        assertEquals(ProposedAction.Meta(MetaCommand.STATUS), p.interpret(state, thorin, "Jak jsme na tom?"))
    }

    @Test
    fun `offline interpreter in exploration`() {
        val engine = newEngine(players = 4)
        val p = OfflineInterpreter(g)
        val pip = engine.state.party.first { it.id == "pip" }
        assertEquals(ProposedAction.Advance, p.interpret(engine.state, pip, "Jdeme dál"))
        assertEquals(ProposedAction.Rest, p.interpret(engine.state, pip, "Odpočineme si"))
        assertEquals(ProposedAction.Check("dex", "moving_silently"), p.interpret(engine.state, pip, "Plížím se potichu ke dveřím"))
        assertEquals(ProposedAction.Narrative("Zeptám se starosty"), p.interpret(engine.state, pip, "Zeptám se starosty"))
    }

    @Test
    fun `numbers in speech`() {
        assertEquals(2, CzechText.number("dva"))
        assertEquals(3, CzechText.number("Budeme hrát tři"))
        assertEquals(4, CzechText.number("4"))
        assertEquals(1, CzechText.number("prvního"))
    }

    @Test
    fun `status snapshot uses czech labels`() {
        val engine = newEngine()
        engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
        val snap = StatusSnapshot.of(engine.state)
        assertEquals("Mlýn", snap.title)
        assertEquals("2 goblini", snap.enemies)
        assertEquals(3, snap.rows.size)
    }

    @Test
    fun `narration trimming keeps whole sentences`() {
        val text = "První věta. Druhá věta je delší. Třetí."
        assertEquals("První věta. Druhá věta je delší.", OpenAiNarrator.trimToLength(text, 35))
    }
}
