package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combat
import cz.crabhi.dragons.rules.Health
import cz.crabhi.dragons.rules.Spell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun `adventure validator rejects unknown monsters and bad sizes`() {
        assertTrue(AdventureValidator.validate(testAdventure()).isEmpty())
        val bad =
            testAdventure().copy(
                scenes = testAdventure().scenes.take(2).map { it.copy(encounter = Encounter(listOf(MonsterGroup("dragon", 1)))) },
            )
        val errors = AdventureValidator.validate(bad)
        assertTrue(errors.any { "dragon" in it })
        assertTrue(errors.any { "3 to 5 scenes" in it })
    }

    @Test
    fun `exploration rejects combat actions and allows advancing into an encounter`() {
        val engine = newEngine()
        val actor = engine.beginTurn().actor!!
        assertEquals("thorin", actor.id)
        assertEquals(Rejection.NO_ENEMIES, engine.validate(actor, ProposedAction.Attack()))
        assertEquals(Rejection.NOT_IN_COMBAT, engine.validate(actor, ProposedAction.Flee))
        val events = engine.resolve(actor, ProposedAction.Advance)
        assertTrue(events.any { it is GameEvent.EncounterStarted })
        assertEquals(Phase.COMBAT, engine.state.phase)
        assertEquals(listOf("goblin-1", "goblin-2"), engine.state.monsters.map { it.id })
    }

    @Test
    fun `only the current actor may act`() {
        val engine = newEngine()
        val first = engine.beginTurn().actor!!
        val other = engine.state.party.first { it.id != first.id }
        assertEquals(Rejection.NOT_YOUR_TURN, engine.validate(other, ProposedAction.Rest))
    }

    @Test
    fun `rest only once per scene and restores spells`() {
        val engine = newEngine()
        val lianna = engine.state.party.first { it.id == "lianna" }
        lianna.memorized.clear()
        val a = engine.beginTurn().actor!!
        engine.resolve(a, ProposedAction.Rest)
        assertEquals(listOf(Spell.SLEEP), lianna.memorized)
        val b = engine.beginTurn().actor!!
        assertEquals(Rejection.ALREADY_RESTED, engine.validate(b, ProposedAction.Rest))
    }

    @Test
    fun `spell validation`() {
        val engine = newEngine()
        engine.beginTurn() // thorin
        engine.resolve(engine.state.currentActor!!, ProposedAction.Narrative("looks around"))
        val lianna = engine.beginTurn().actor!!
        assertEquals("lianna", lianna.id)
        assertEquals(Rejection.UNKNOWN_SPELL, engine.validate(lianna, ProposedAction.Cast("fireball")))
        assertEquals(Rejection.SPELL_NOT_KNOWN, engine.validate(lianna, ProposedAction.Cast("bless")))
        assertEquals(Rejection.SPELL_NOT_MEMORIZED, engine.validate(lianna, ProposedAction.Cast("shield")))
        assertEquals(Rejection.NO_ENEMIES, engine.validate(lianna, ProposedAction.Cast("sleep")))
    }

    @Test
    fun `combat runs to an end with initiative rounds`() {
        val engine = newEngine(seed = 5)
        engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
        var rounds = 0
        var guard = 0
        while (engine.state.phase == Phase.COMBAT && guard++ < 500) {
            val turn = engine.beginTurn()
            rounds += turn.events.count { it is GameEvent.RoundStarted }
            val actor = turn.actor ?: break
            if (actor.isPc) {
                engine.resolve(actor, ProposedAction.Attack())
            } else {
                val options = engine.monsterOptions(actor)
                engine.resolveMonster(actor, options.first())
            }
        }
        assertTrue(rounds >= 1)
        assertTrue(engine.state.phase == Phase.EXPLORATION || engine.state.phase == Phase.DEFEAT)
        if (engine.state.phase == Phase.EXPLORATION) {
            assertTrue(engine.state.monsters.isEmpty())
            assertTrue(engine.state.party.all { it.xp > 0 || !it.isAlive })
        }
    }

    @Test
    fun `sleep ends the fight and awards xp`() {
        val engine = newEngine(seed = 3)
        engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
        val lianna = engine.state.party.first { it.id == "lianna" }
        var events: List<GameEvent> = emptyList()
        var guard = 0
        while (engine.state.phase == Phase.COMBAT && guard++ < 50) {
            val actor = engine.beginTurn().actor ?: break
            when {
                actor.id == "lianna" && Spell.SLEEP in lianna.memorized -> events = engine.resolve(actor, ProposedAction.Cast("sleep"))
                actor.isPc -> engine.resolve(actor, ProposedAction.Defend)
                else -> engine.resolveMonster(actor, engine.monsterOptions(actor).first())
            }
            if (events.isNotEmpty()) break
        }
        val cast = events.filterIsInstance<GameEvent.SpellCast>().single()
        assertEquals(Spell.SLEEP, cast.result.spell)
        if (engine.state.monsters.isEmpty()) {
            val won = events.filterIsInstance<GameEvent.EncounterWon>().single()
            assertEquals(30, won.xp.total)
        }
    }

    @Test
    fun `party defeat is detected`() {
        val engine = newEngine(players = 1)
        engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
        val thorin = engine.state.party.single()
        Combat.applyDamage(thorin, thorin.hp + 3)
        val turn = engine.beginTurn()
        assertNull(turn.actor)
        assertEquals(Phase.DEFEAT, engine.state.phase)
    }

    @Test
    fun `advancing from the last scene completes the adventure`() {
        val engine = newEngine()
        engine.state.sceneIndex = 2
        val actor = engine.beginTurn().actor!!
        val events = engine.resolve(actor, ProposedAction.Advance)
        assertIs<GameEvent.AdventureCompleted>(events.last())
        assertEquals(Phase.VICTORY, engine.state.phase)
    }

    @Test
    fun `healing potion heals an ally`() {
        val engine = newEngine()
        val pip = engine.state.party.first { it.id == "lianna" }
        Combat.applyDamage(pip, pip.hp + 2)
        val thorin = engine.beginTurn().actor!!
        val events = engine.resolve(thorin, ProposedAction.UseItem("healing_potion", "lianna"))
        val used = assertIs<GameEvent.ItemUsed>(events.single())
        assertTrue(used.healed > 0)
        assertEquals(Health.OK, pip.health)
        assertEquals(0, thorin.inventory.values.first())
    }

    @Test
    fun `turn in progress survives save and load`() {
        val engine = newEngine(seed = 9)
        val actor = engine.beginTurn().actor!!
        val json = GameJson.encodeToString(GameState.serializer(), engine.state)
        val restored = GameJson.decodeFromString(GameState.serializer(), json)
        val engine2 = GameEngine(restored)
        assertEquals(actor.id, engine2.beginTurn().actor!!.id)
        assertEquals(engine.dice.state, engine2.dice.state)
        assertNotNull(restored.adventure)
    }
}
