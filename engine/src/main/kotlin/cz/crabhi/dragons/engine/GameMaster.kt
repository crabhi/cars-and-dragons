package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.Dice
import cz.crabhi.dragons.rules.Health
import cz.crabhi.dragons.rules.Pregens
import cz.crabhi.dragons.rules.SeededDice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

data class ListenRequest(
    /** Whose turn it is; null during setup. */
    val actorId: String?,
    /** Words to bias speech recognition toward (names, game verbs). */
    val hints: List<String>,
    /** Dictation (the premise) gets a longer listening window. */
    val long: Boolean = false,
)

/** Voice or text channel to the players. Implemented by the Android app (speech) and the CLI (terminal). */
interface PlayerIo {
    suspend fun say(text: String)

    /** One utterance, or null on silence/timeout. */
    suspend fun listen(request: ListenRequest): String?

    fun onStatus(status: StatusSnapshot) {}
}

data class GameConfig(
    val tone: String = "Classic heroic fantasy with moderate violence. Warm, vivid, never gory.",
    val maxNarrationChars: Int = 450,
    val costCapUsd: Double = 3.0,
    val inputPricePerMTok: Double = 2.5,
    val outputPricePerMTok: Double = 10.0,
    val retryDelaysMs: List<Long> = listOf(2_000, 5_000, 10_000),
    val silenceRetries: Int = 2,
    val maxRejections: Int = 3,
    val outlineAttempts: Int = 3,
    val seed: Long? = null,
)

fun interface NarratorFactory {
    /** Creates the primary (online) narrator, or null to play with the offline narrator only. */
    fun create(onUsage: (inputTokens: Int, outputTokens: Int) -> Unit): Narrator?
}

/**
 * Runs the game: setup, the turn loop, narration, saving. Platform independent; all speech goes
 * through [PlayerIo].
 */
class GameMaster(
    private val io: PlayerIo,
    private val saves: SaveStore,
    private val config: GameConfig = GameConfig(),
    private val transcripts: TranscriptStore? = null,
    private val narratorFactory: NarratorFactory = NarratorFactory { null },
    private val tacticsFactory: (Dice) -> TacticsAdvisor = { HeuristicTactics(it) },
    private val glossary: Glossary = Glossary.czech,
    private val log: (String) -> Unit = {},
) {
    private val offline = OfflineNarrator(glossary)
    private var primary: Narrator? = null
    private var costGuard: CostGuard? = null
    private lateinit var state: GameState
    private lateinit var engine: GameEngine
    private lateinit var tactics: TacticsAdvisor
    private var lastSpoken = ""
    private var costExceededAnnounced = false
    private val pending = mutableListOf<GameEvent>()

    @Volatile private var recapRequested = false

    @Volatile private var stopRequested = false

    /** Ask for a one-sentence recap before the next turn (after an interruption such as a phone call). */
    fun requestRecap() {
        recapRequested = true
    }

    /** Stop after the current step; the game stays saved. */
    fun stop() {
        stopRequested = true
    }

    val currentStatus: StatusSnapshot? get() = if (::state.isInitialized) StatusSnapshot.of(state, glossary) else null

    suspend fun run(newGame: Boolean = false) {
        transcripts?.purgeOld()
        val saved = if (newGame) null else saves.load()?.takeUnless { it.isFinished }
        if (saved != null) {
            attach(saved)
            speak(glossary["msg.resume"])
            speak(narrated { it.narrate(ctx(), NarrationRequest.Recap) })
        } else {
            if (!setup()) return
        }
        loop()
    }

    private fun attach(s: GameState) {
        state = s
        engine = GameEngine(s, SeededDice(s.diceState))
        tactics = tacticsFactory(engine.dice)
        val guard = CostGuard(s, config.costCapUsd, config.inputPricePerMTok, config.outputPricePerMTok)
        costGuard = guard
        primary = narratorFactory.create { input, output -> guard.record(input, output) }
        publish()
    }

    private fun ctx() = NarrationContext(state, glossary, config.tone, config.maxNarrationChars)

    // ------------------------------------------------------------------ setup

    private suspend fun setup(): Boolean {
        val seed = config.seed ?: System.nanoTime()
        val id = "game-" + java.lang.Long.toHexString(seed and 0xFFFFFFFFL)
        attach(GameState(id = id, diceState = seed))
        io.say(glossary["msg.welcome"])
        val players = askPlayerCount() ?: return false
        val party = Pregens.party(players)
        state.party.addAll(party)
        publish()
        io.say(glossary["msg.party_intro"] + " " + party.joinToString(". ") { describeCharacter(it) } + ".")
        io.say(glossary["msg.ask_premise"])
        val premise =
            io.listen(ListenRequest(null, emptyList(), long = true))?.takeIf { it.isNotBlank() } ?: glossary["msg.premise_default"]
        if (stopRequested) return false
        transcripts?.append(state.id, "PLAYER", premise)
        io.say(glossary["msg.preparing"])
        val adventure = makeAdventure(premise, party)
        val events = engine.startGame(party, adventure)
        saves.save(state)
        publish()
        speak(narrated { it.narrate(ctx(), NarrationRequest.Events(events)) })
        return true
    }

    private suspend fun askPlayerCount(): Int? {
        repeat(2) {
            io.say(glossary["msg.ask_players"])
            val heard = io.listen(ListenRequest(null, listOf("jeden", "dva", "tři", "čtyři"))) ?: return@repeat
            if (stopRequested) return null
            CzechText.number(heard)?.takeIf { it in 1..MAX_PLAYERS }?.let { return it }
        }
        if (stopRequested) return null
        io.say(glossary["msg.players_default"])
        return 2
    }

    private fun describeCharacter(c: Combatant): String =
        "${c.name} – ${glossary["race.${c.race!!.id}"]}, ${glossary["class.${c.characterClass!!.id}"]}"

    private suspend fun makeAdventure(
        premise: String,
        party: List<Combatant>,
    ): Adventure {
        val narrator = primary
        if (narrator != null) {
            var feedback = emptyList<String>()
            repeat(config.outlineAttempts) { attempt ->
                try {
                    val adventure = withRetries { narrator.createAdventure(premise, party, feedback) }
                    val errors = AdventureValidator.validate(adventure)
                    if (errors.isEmpty()) return adventure
                    log("Outline attempt ${attempt + 1} rejected: $errors")
                    feedback = errors
                } catch (e: NarratorException) {
                    log("Outline attempt ${attempt + 1} failed: ${e.message}")
                    if (e.transient) return@repeat
                }
            }
        }
        return OfflineAdventures.create(premise, party.size)
    }

    // ------------------------------------------------------------------ main loop

    private suspend fun loop() {
        while (!state.isFinished && !stopRequested) {
            if (recapRequested) {
                recapRequested = false
                speak(narrated { it.narrate(ctx(), NarrationRequest.Recap) })
            }
            val turn = engine.beginTurn()
            pending += turn.events
            publish()
            val actor = turn.actor ?: break
            if (actor.isPc) playerTurn(actor) else monsterTurn(actor)
            saves.save(state)
            publish()
        }
        flushPending()
        if (state.isFinished) {
            saves.clear()
            publish()
        }
    }

    private suspend fun flushPending() {
        if (pending.isEmpty()) return
        val events = pending.toList()
        pending.clear()
        speak(narrated { it.narrate(ctx(), NarrationRequest.Events(events)) })
    }

    private suspend fun monsterTurn(monster: Combatant) {
        val options = engine.monsterOptions(monster)
        val choice = tactics.choose(state, monster, options)
        val index = choice.index.coerceIn(options.indices)
        log("${monster.id} -> ${options[index]} (${choice.source}, ${choice.confidence})")
        pending += engine.resolveMonster(monster, options[index])
        if (state.isFinished) flushPending()
    }

    private suspend fun playerTurn(actor: Combatant) {
        val options = engine.legalOptions(actor)
        val preceding = pending.toList()
        pending.clear()
        val prompt = narrated { it.narrate(ctx(), NarrationRequest.TurnPrompt(actor.id, options, preceding)) }
        speak(prompt)
        var silences = 0
        var rejections = 0
        while (!stopRequested) {
            val heard = io.listen(ListenRequest(actor.id, hints()))
            if (stopRequested) return
            if (heard.isNullOrBlank()) {
                silences++
                if (silences > config.silenceRetries) {
                    speak(glossary["msg.skip_turn"])
                    val wait = if (state.phase == Phase.COMBAT) ProposedAction.Defend else ProposedAction.Narrative("The character waits.")
                    engine.resolve(actor, wait)
                    return
                }
                speak(glossary["msg.didnt_hear"])
                continue
            }
            transcripts?.append(state.id, actor.name, heard)
            state.addLog(actor.name, heard)
            val meta = offline.meta(heard)
            val action = if (meta != null) ProposedAction.Meta(meta) else narrated { it.interpret(ctx(), actor, heard, options) }.action
            log("${actor.id} said '$heard' -> $action")
            if (action is ProposedAction.Meta) {
                handleMeta(action.command)
                if (stopRequested) return
                continue
            }
            val rejection = engine.validate(actor, action)
            if (rejection != null) {
                rejections++
                val text =
                    if (rejections > config.maxRejections) {
                        glossary[rejection.key]
                    } else {
                        narrated { it.narrate(ctx(), NarrationRequest.Rejected(actor.id, heard, rejection)) }
                    }
                speak(text)
                continue
            }
            val events = engine.resolve(actor, action)
            saves.save(state)
            publish()
            speak(narrated { it.narrate(ctx(), NarrationRequest.Events(events, actor.id, heard, action)) })
            return
        }
    }

    private suspend fun handleMeta(command: MetaCommand) {
        when (command) {
            MetaCommand.STATUS -> {
                speak(statusText())
            }

            MetaCommand.REPEAT -> {
                io.say(lastSpoken)
            }

            MetaCommand.QUIT -> {
                saves.save(state)
                io.say(glossary["msg.saved_quit"])
                stopRequested = true
            }
        }
    }

    fun statusText(): String =
        glossary["msg.status"] + " " +
            state.party.joinToString(". ") { c ->
                val hp = "${glossary["term.hp_label"].lowercase()} ${c.hp} z ${c.maxHp}"
                if (c.health == Health.OK && c.conditions.isEmpty()) "${c.name} – $hp" else "${c.name} – $hp, ${glossary.healthLabel(c)}"
            } + "."

    private fun hints(): List<String> =
        state.party.map { it.name } +
            state.monsters.mapNotNull { it.monsterId }.distinct().map { glossary.monsterName(it) } +
            listOf("útočím", "kouzlím", "bráním se", "jdeme dál", "lektvar", "odpočineme", "utečeme")

    // ------------------------------------------------------------------ narration plumbing

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        lastSpoken = text
        state.addLog("DM", text)
        transcripts?.append(state.id, "DM", text)
        io.say(text)
    }

    private fun publish() {
        if (::state.isInitialized) io.onStatus(StatusSnapshot.of(state, glossary))
    }

    /** Retries transient narrator failures with a spoken notice. Rethrows when retries run out. */
    private suspend fun <T> withRetries(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: NarratorException) {
                if (!e.transient || attempt >= config.retryDelaysMs.size) throw e
                log("Narrator problem, retrying: ${e.message}")
                if (attempt == 0) io.say(glossary["msg.offline"])
                delay(config.retryDelaysMs[attempt])
                attempt++
            }
        }
    }

    /**
     * Runs [block] with the online narrator, retrying through short outages; falls back to the offline
     * narrator when there is no online narrator, the spending cap is reached, or the network stays down.
     */
    private suspend fun <T> narrated(block: suspend (Narrator) -> T): T {
        val narrator = primary
        val guard = costGuard
        if (narrator == null) return block(offline)
        if (guard != null && guard.exceeded) {
            if (!costExceededAnnounced) {
                costExceededAnnounced = true
                io.say(glossary["msg.cost_exceeded"])
            }
            return block(offline)
        }
        return try {
            val result = withRetries { block(narrator) }
            if (guard?.shouldWarn() == true) io.say(glossary["msg.cost_warning"])
            result
        } catch (e: NarratorException) {
            log("Narrator failed, using offline narration: ${e.message}")
            if (e.transient) io.say(glossary["msg.offline_fallback"])
            block(offline)
        }
    }

    companion object {
        const val MAX_PLAYERS = 4
    }
}
