package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Pregens
import cz.crabhi.dragons.rules.SeededDice
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LlmTest {
    private class FakeHttp(
        private val reply: (String) -> String,
    ) : HttpClient() {
        val bodies = mutableListOf<String>()

        override suspend fun postJson(
            url: String,
            headers: Map<String, String>,
            body: String,
        ): String {
            bodies += body
            return reply(body)
        }
    }

    private fun completion(content: String) =
        GameJson.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put(
                    "choices",
                    kotlinx.serialization.json.buildJsonArray {
                        add(
                            kotlinx.serialization.json.buildJsonObject {
                                put(
                                    "message",
                                    kotlinx.serialization.json.buildJsonObject {
                                        put("content", kotlinx.serialization.json.JsonPrimitive(content))
                                    },
                                )
                            },
                        )
                    },
                )
                put(
                    "usage",
                    kotlinx.serialization.json.buildJsonObject {
                        put("prompt_tokens", kotlinx.serialization.json.JsonPrimitive(1000))
                        put("completion_tokens", kotlinx.serialization.json.JsonPrimitive(100))
                    },
                )
            },
        )

    @Test
    fun `openai narrator parses proposed actions and records usage`() =
        runTest {
            var tokens = 0
            val http = FakeHttp { completion("""{"action":{"type":"attack","target":"goblin-2","ranged":false}}""") }
            val narrator = OpenAiNarrator(LlmConfig("key"), "heroic", { i, o -> tokens += i + o }, http)
            val engine = newEngine()
            val ctx = NarrationContext(engine.state, Glossary.czech, "heroic", 300)
            val thorin = engine.state.party.first()
            val result = narrator.interpret(ctx, thorin, "seknu toho druhého", engine.legalOptions(thorin))
            assertEquals(ProposedAction.Attack("goblin-2"), result.action)
            assertEquals(1100, tokens)
            assertTrue(http.bodies.single().contains("gpt-6-sol"))
            assertTrue(http.bodies.single().contains("json_object"))
        }

    @Test
    fun `openai narrator builds a valid adventure`() =
        runTest {
            val adventure =
                """
                {"title":"Mlýnský kámen","premise":"p","scenes":[
                 {"id":"a","title":"Ves","location":"Ves","description":"Popis.","goal":"Jít","npcs":[{"name":"Mlynář","role":"mlynář"}]},
                 {"id":"b","title":"Les","location":"Les","description":"Popis.","encounter":{"monsters":[{"monster":"Goblin","count":2}]}},
                 {"id":"c","title":"Jeskyně","location":"Jeskyně","description":"Popis.","encounter":{"monsters":[{"monster":"orc","count":3}]},"magic_item":true}
                ]}
                """.trimIndent()
            val narrator = OpenAiNarrator(LlmConfig("key"), "heroic", http = FakeHttp { completion(adventure) })
            val result = narrator.createAdventure("p", Pregens.party(2), emptyList())
            assertTrue(AdventureValidator.validate(result).isEmpty(), AdventureValidator.validate(result).toString())
            assertEquals("goblin", result.scenes[1].encounter!!.monsters.single().monster)
            assertTrue(result.scenes[2].magicItem)
        }

    @Test
    fun `server errors are transient, auth errors are not`() =
        runTest {
            val e503 =
                runCatching {
                    OpenAiNarrator(LlmConfig("k"), "t", http = FakeHttp { throw HttpClient.HttpException(503, "busy") })
                        .narrate(NarrationContext(newEngine().state, Glossary.czech, "t", 100), NarrationRequest.Recap)
                }.exceptionOrNull() as NarratorException
            assertTrue(e503.transient)
            val e401 =
                runCatching {
                    OpenAiNarrator(LlmConfig("k"), "t", http = FakeHttp { throw HttpClient.HttpException(401, "no") })
                        .narrate(NarrationContext(newEngine().state, Glossary.czech, "t", 100), NarrationRequest.Recap)
                }.exceptionOrNull() as NarratorException
            assertTrue(!e401.transient)
        }

    @Test
    fun `jev choices are used when confident and fall back otherwise`() =
        runTest {
            val engine = newEngine()
            engine.resolve(engine.beginTurn().actor!!, ProposedAction.Advance)
            val goblin = engine.state.monsters.first()
            val options = engine.monsterOptions(goblin)
            val fallback = HeuristicTactics(SeededDice(1))
            val confident = JevTacticsAdvisor(JevConfig("https://jev", "k"), fallback, FakeHttp { """{"choice":1,"confidence":0.9}""" })
            assertEquals(TacticsChoice(1, 0.9, "jev"), confident.choose(engine.state, goblin, options))
            val unsure = JevTacticsAdvisor(JevConfig("https://jev", "k"), fallback, FakeHttp { """{"choice":1,"confidence":0.1}""" })
            assertEquals("heuristic", unsure.choose(engine.state, goblin, options).source)
            val down = JevTacticsAdvisor(JevConfig("https://jev", "k"), fallback, FakeHttp { throw IOException("offline") })
            assertEquals("heuristic", down.choose(engine.state, goblin, options).source)
            val outOfRange = JevTacticsAdvisor(JevConfig("https://jev", "k"), fallback, FakeHttp { """{"choice":99,"confidence":1}""" })
            assertEquals("heuristic", outOfRange.choose(engine.state, goblin, options).source)
        }
}
