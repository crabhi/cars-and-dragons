package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.InitiativeResult
import kotlinx.serialization.Serializable

@Serializable
enum class Phase { SETUP, EXPLORATION, COMBAT, VICTORY, DEFEAT }

@Serializable
data class LogEntry(
    val speaker: String,
    val text: String,
)

/** The complete, serializable game. Saved as JSON after every resolved action. */
@Serializable
class GameState(
    val id: String,
    var phase: Phase = Phase.SETUP,
    var premise: String = "",
    var adventure: Adventure? = null,
    var sceneIndex: Int = 0,
    val party: MutableList<Combatant> = mutableListOf(),
    val monsters: MutableList<Combatant> = mutableListOf(),
    var round: Int = 0,
    var initiative: InitiativeResult? = null,
    var turnOrder: MutableList<String> = mutableListOf(),
    var turnIndex: Int = 0,
    /** Actor whose turn has started but not finished. Lets a resumed game continue the same turn. */
    var currentActorId: String? = null,
    var restedInScene: Boolean = false,
    var encounterDone: Boolean = false,
    var moraleChecked: Boolean = false,
    /** Exploration turns taken in the current scene; the narrator nudges the story on when it grows. */
    var explorationTurns: Int = 0,
    var diceState: Long = 0,
    var spentUsd: Double = 0.0,
    var costWarningGiven: Boolean = false,
    val log: MutableList<LogEntry> = mutableListOf(),
    var version: Int = CURRENT_VERSION,
) {
    val scene: Scene? get() = adventure?.scenes?.getOrNull(sceneIndex)

    val isFinished: Boolean get() = phase == Phase.VICTORY || phase == Phase.DEFEAT

    val all: List<Combatant> get() = party + monsters

    fun find(id: String?): Combatant? = all.firstOrNull { it.id == id }

    val currentActor: Combatant? get() = find(currentActorId)

    fun addLog(
        speaker: String,
        text: String,
    ) {
        log += LogEntry(speaker, text)
        while (log.size > MAX_LOG) log.removeAt(0)
    }

    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_LOG = 30
    }
}
