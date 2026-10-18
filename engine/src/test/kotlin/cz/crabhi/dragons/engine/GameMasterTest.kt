package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameMasterTest {
    private fun tempDir() = Files.createTempDirectory("dragons").toFile()

    @Test
    fun `offline game plays to the end and clears the save`() =
        runTest {
            val dir = tempDir()
            val io = ScriptedIo(simplePlayer())
            val saves = SaveStore(dir)
            GameMaster(io, saves, GameConfig(seed = 42), TranscriptStore(dir.resolve("t"))).run()
            val end = io.said.last()
            assertTrue(end.contains("Vyhráli") || end.contains("padla"), end)
            assertNull(saves.load())
            assertTrue(dir.resolve("t").listFiles()!!.isNotEmpty())
        }

    @Test
    fun `same seed and inputs give the same game`() =
        runTest {
            fun play(): List<String> {
                val io = ScriptedIo(simplePlayer())
                kotlinx.coroutines.runBlocking { GameMaster(io, SaveStore(tempDir()), GameConfig(seed = 7)).run() }
                return io.said
            }
            assertEquals(play(), play())
        }

    @Test
    fun `quit saves and a new master resumes`() =
        runTest {
            val dir = tempDir()
            var quit = false
            val io =
                ScriptedIo { req, status ->
                    if (req.actorId != null && status?.phase == Phase.COMBAT && !quit) {
                        quit = true
                        "konec hry"
                    } else {
                        simplePlayer()(req, status)
                    }
                }
            GameMaster(io, SaveStore(dir), GameConfig(seed = 11)).run()
            val saved = SaveStore(dir).load()!!
            assertEquals(Phase.COMBAT, saved.phase)
            val io2 = ScriptedIo(simplePlayer())
            GameMaster(io2, SaveStore(dir), GameConfig(seed = 11)).run()
            assertTrue(io2.said.first().contains("uložené"))
            assertNull(SaveStore(dir).load())
        }

    @Test
    fun `silence skips the turn after retries`() =
        runTest {
            var count = 0
            val io =
                ScriptedIo { req, status ->
                    if (req.actorId != null) {
                        count++
                        if (count <= 3) null else simplePlayer()(req, status)
                    } else {
                        simplePlayer()(req, status)
                    }
                }
            GameMaster(io, SaveStore(tempDir()), GameConfig(seed = 3)).run()
            assertTrue(io.said.any { it.contains("Nic jsem neslyšel") })
            assertTrue(io.said.any { it.contains("vyčkává") })
        }

    @Test
    fun `illegal proposal is rejected and the player is asked again`() =
        runTest {
            var first = true
            val io =
                ScriptedIo { req, status ->
                    if (req.actorId != null && first) {
                        first = false
                        "útočím na goblina" // nothing to attack in the first scene
                    } else {
                        simplePlayer()(req, status)
                    }
                }
            GameMaster(io, SaveStore(tempDir()), GameConfig(seed = 3)).run()
            assertTrue(io.said.any { it == Glossary.czech["reject.no_enemies"] })
        }

    private class BrokenNarrator(
        private val transient: Boolean,
    ) : Narrator {
        var calls = 0

        private fun fail(): Nothing {
            calls++
            throw NarratorException("down", transient = transient)
        }

        override suspend fun createAdventure(
            premise: String,
            party: List<Combatant>,
            feedback: List<String>,
        ): Adventure = fail()

        override suspend fun interpret(
            ctx: NarrationContext,
            actor: Combatant,
            utterance: String,
            options: LegalOptions,
        ): Interpretation = fail()

        override suspend fun narrate(
            ctx: NarrationContext,
            request: NarrationRequest,
        ): String = fail()
    }

    @Test
    fun `network failures fall back to offline narration`() =
        runTest {
            val broken = BrokenNarrator(transient = true)
            val io = ScriptedIo(simplePlayer())
            val config = GameConfig(seed = 42, retryDelaysMs = listOf(1, 1))
            GameMaster(io, SaveStore(tempDir()), config, narratorFactory = { broken }).run()
            assertTrue(io.said.any { it == Glossary.czech["msg.offline"] })
            assertTrue(io.said.any { it == Glossary.czech["msg.offline_fallback"] })
            assertTrue(broken.calls > 3)
            val end = io.said.last()
            assertTrue(end.contains("Vyhráli") || end.contains("padla"), end)
        }

    @Test
    fun `cost guard warns once and then stops using the paid narrator`() {
        val state = GameState("x")
        val guard = CostGuard(state, capUsd = 1.0, inputPricePerMTok = 1.0, outputPricePerMTok = 1.0)
        guard.record(500_000, 0)
        assertTrue(!guard.shouldWarn())
        guard.record(300_000, 100_000)
        assertTrue(guard.shouldWarn())
        assertTrue(!guard.shouldWarn())
        assertTrue(!guard.exceeded)
        guard.record(100_000, 0)
        assertTrue(guard.exceeded)
    }
}
