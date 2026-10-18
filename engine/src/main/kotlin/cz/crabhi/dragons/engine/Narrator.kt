package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant

/** Everything a narrator may look at. Read-only by convention: narrators never mutate the state. */
data class NarrationContext(
    val state: GameState,
    val glossary: Glossary,
    val tone: String,
    val maxChars: Int,
)

sealed interface NarrationRequest {
    /** Describe resolved engine events. [actorId]/[utterance]/[action] say what caused them, if a player did. */
    data class Events(
        val events: List<GameEvent>,
        val actorId: String? = null,
        val utterance: String? = null,
        val action: ProposedAction? = null,
    ) : NarrationRequest

    /** Ask a player what their character does. */
    data class TurnPrompt(
        val actorId: String,
        val options: LegalOptions,
        /** Events since the last narration (monster turns, a new round) to describe before asking. */
        val precedingEvents: List<GameEvent> = emptyList(),
    ) : NarrationRequest

    /** Explain why a proposed action is not possible and ask again. */
    data class Rejected(
        val actorId: String,
        val utterance: String,
        val rejection: Rejection,
    ) : NarrationRequest

    /** One-sentence reminder of where the story is (after an interruption or on resume). */
    data object Recap : NarrationRequest
}

data class Interpretation(
    val action: ProposedAction,
)

class NarratorException(
    message: String,
    cause: Throwable? = null,
    /** True for network problems that may go away (no signal); false for bad responses. */
    val transient: Boolean = true,
) : Exception(message, cause)

/**
 * Turns speech into proposed actions and engine outcomes into Czech narration. Implementations must not
 * decide outcomes; the engine does.
 */
interface Narrator {
    /** Expand a premise into an outline. [feedback] lists validation errors from the previous attempt. */
    suspend fun createAdventure(
        premise: String,
        party: List<Combatant>,
        feedback: List<String>,
    ): Adventure

    suspend fun interpret(
        ctx: NarrationContext,
        actor: Combatant,
        utterance: String,
        options: LegalOptions,
    ): Interpretation

    suspend fun narrate(
        ctx: NarrationContext,
        request: NarrationRequest,
    ): String
}
