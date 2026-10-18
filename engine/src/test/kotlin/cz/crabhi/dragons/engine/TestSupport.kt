package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Pregens

/** Player IO that answers from a script function and records everything said. */
class ScriptedIo(
    private val answer: (ListenRequest, StatusSnapshot?) -> String?,
) : PlayerIo {
    val said = mutableListOf<String>()
    val heard = mutableListOf<String?>()
    var status: StatusSnapshot? = null
    var listens = 0

    override suspend fun say(text: String) {
        said += text
    }

    override suspend fun listen(request: ListenRequest): String? {
        listens++
        check(listens < 5_000) { "Game did not finish" }
        return answer(request, status).also { heard += it }
    }

    override fun onStatus(status: StatusSnapshot) {
        this.status = status
    }
}

/** Plays aggressively: attack in combat, move on otherwise. */
fun simplePlayer(players: Int = 3): (ListenRequest, StatusSnapshot?) -> String? =
    { request, status ->
        when {
            request.actorId == null && request.long -> "Skřeti ukradli mlýnský kámen"
            request.actorId == null -> players.toString()
            status?.phase == Phase.COMBAT -> "Útočím"
            else -> "Jdeme dál"
        }
    }

fun testAdventure(): Adventure =
    Adventure(
        title = "Test",
        premise = "Goblini v mlýně",
        scenes =
            listOf(
                Scene("start", "Vesnice", "Náves", "Klidná vesnice.", "Vyrazit."),
                Scene("fight", "Mlýn", "Starý mlýn", "Goblini!", "Porazit je.", encounter = Encounter(listOf(MonsterGroup("goblin", 2)))),
                Scene("end", "Návrat", "Vesnice", "Oslava.", "Vrátit se.", magicItem = true),
            ),
    )

fun newEngine(
    seed: Long = 1,
    players: Int = 3,
): GameEngine {
    val state = GameState("test", diceState = seed)
    val engine = GameEngine(state)
    engine.startGame(Pregens.party(players), testAdventure())
    return engine
}
