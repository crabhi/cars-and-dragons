package cz.crabhi.dragons.cli

import cz.crabhi.dragons.engine.GameConfig
import cz.crabhi.dragons.engine.GameMaster
import cz.crabhi.dragons.engine.HeuristicTactics
import cz.crabhi.dragons.engine.JevConfig
import cz.crabhi.dragons.engine.JevTacticsAdvisor
import cz.crabhi.dragons.engine.ListenRequest
import cz.crabhi.dragons.engine.LlmConfig
import cz.crabhi.dragons.engine.NarratorFactory
import cz.crabhi.dragons.engine.OpenAiNarrator
import cz.crabhi.dragons.engine.PlayerIo
import cz.crabhi.dragons.engine.SaveStore
import cz.crabhi.dragons.engine.TranscriptStore
import kotlinx.coroutines.runBlocking
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintStream
import java.util.Properties

/** Terminal harness: the same game as in the car, with typed input and printed narration (Czech). */
private class ConsoleIo(
    private val input: BufferedReader,
    private val out: PrintStream,
    private val echo: Boolean,
) : PlayerIo {
    override suspend fun say(text: String) {
        out.println("\nPJ: $text")
    }

    override suspend fun listen(request: ListenRequest): String? {
        out.print(if (request.long) "\n(námět) > " else "\n> ")
        out.flush()
        val line = input.readLine() ?: return "konec hry"
        if (echo) out.println(line)
        return line.trim().ifEmpty { null }
    }
}

private data class Options(
    val seed: Long?,
    val newGame: Boolean,
    val offline: Boolean,
    val dataDir: File,
    val script: File?,
    val verbose: Boolean,
)

private fun parseArgs(args: Array<String>): Options {
    var seed: Long? = null
    var newGame = false
    var offline = false
    var dataDir = File("build/cli-data")
    var script: File? = null
    var verbose = false
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--seed" -> {
                seed = args[++i].toLong()
            }

            "--new" -> {
                newGame = true
            }

            "--offline" -> {
                offline = true
            }

            "--data" -> {
                dataDir = File(args[++i])
            }

            "--script" -> {
                script = File(args[++i])
            }

            "--verbose" -> {
                verbose = true
            }

            "--help", "-h" -> {
                println(
                    """
                    Použití: cli [--new] [--offline] [--seed N] [--data DIR] [--script FILE] [--verbose]
                      --new      začít novou hru místo pokračování v uložené
                      --offline  bez jazykového modelu (zjednodušený vypravěč)
                      --script   číst vstupy hráčů ze souboru (řádek = jedna promluva, prázdný řádek = ticho)
                    Klíče: OPENAI_API_KEY, OPENAI_MODEL, OPENAI_BASE_URL, JEV_ENDPOINT, JEV_API_KEY
                    (proměnné prostředí nebo local.properties: openai.apiKey, openai.model, jev.endpoint, jev.apiKey)
                    """.trimIndent(),
                )
                kotlin.system.exitProcess(0)
            }

            else -> {
                error("Unknown argument ${args[i]}")
            }
        }
        i++
    }
    return Options(seed, newGame, offline, dataDir, script, verbose)
}

private fun localProperties(): Properties {
    val props = Properties()
    val file = File("local.properties")
    if (file.exists()) file.reader(Charsets.UTF_8).use { props.load(it) }
    return props
}

fun main(args: Array<String>) {
    val options = parseArgs(args)
    val props = localProperties()

    fun setting(
        env: String,
        prop: String,
    ): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: props.getProperty(prop)?.takeIf { it.isNotBlank() }

    val apiKey = setting("OPENAI_API_KEY", "openai.apiKey")
    val model = setting("OPENAI_MODEL", "openai.model") ?: LlmConfig.DEFAULT_MODEL
    val baseUrl = setting("OPENAI_BASE_URL", "openai.baseUrl") ?: "https://api.openai.com/v1"
    val jevEndpoint = setting("JEV_ENDPOINT", "jev.endpoint")
    val jevKey = setting("JEV_API_KEY", "jev.apiKey")
    val config = GameConfig(seed = options.seed)

    val out = PrintStream(System.out, true, "UTF-8")
    val input =
        if (options.script != null) {
            options.script.bufferedReader(Charsets.UTF_8)
        } else {
            BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        }
    val narratorFactory =
        NarratorFactory { onUsage ->
            if (options.offline || apiKey == null) null else OpenAiNarrator(LlmConfig(apiKey, model, baseUrl), config.tone, onUsage)
        }
    if (!options.offline && apiKey == null) out.println("(Bez OPENAI_API_KEY – hraje se se zjednodušeným vypravěčem.)")
    val master =
        GameMaster(
            io = ConsoleIo(input, out, echo = options.script != null),
            saves = SaveStore(File(options.dataDir, "saves")),
            config = config,
            transcripts = TranscriptStore(File(options.dataDir, "transcripts")),
            narratorFactory = narratorFactory,
            tacticsFactory = { dice ->
                val heuristic = HeuristicTactics(dice)
                if (jevEndpoint != null && jevKey != null) JevTacticsAdvisor(JevConfig(jevEndpoint, jevKey), heuristic) else heuristic
            },
            log = { if (options.verbose) System.err.println("[log] $it") },
        )
    runBlocking { master.run(newGame = options.newGame) }
}
