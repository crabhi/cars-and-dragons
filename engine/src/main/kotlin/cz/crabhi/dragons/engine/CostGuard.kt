package cz.crabhi.dragons.engine

/** Per-session API spending cap. Prices are USD per million tokens and configurable. */
class CostGuard(
    private val state: GameState,
    val capUsd: Double,
    private val inputPricePerMTok: Double,
    private val outputPricePerMTok: Double,
    private val warnFraction: Double = 0.8,
) {
    val spentUsd: Double get() = state.spentUsd

    val exceeded: Boolean get() = capUsd > 0 && state.spentUsd >= capUsd

    fun record(
        inputTokens: Int,
        outputTokens: Int,
    ) {
        state.spentUsd += inputTokens * inputPricePerMTok / 1_000_000 + outputTokens * outputPricePerMTok / 1_000_000
    }

    /** Returns true exactly once, when spending first crosses the warning threshold. */
    fun shouldWarn(): Boolean {
        if (state.costWarningGiven || capUsd <= 0 || state.spentUsd < capUsd * warnFraction) return false
        state.costWarningGiven = true
        return true
    }
}
