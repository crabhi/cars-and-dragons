package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Health

/** Immutable copy of what the car screen shows. Built on the game thread, safe to hand to the UI. */
data class StatusSnapshot(
    val title: String,
    val phase: Phase,
    val rows: List<Row>,
    val enemies: String,
    val currentActorLabel: String?,
    val paused: Boolean = false,
) {
    data class Row(
        val id: String,
        val label: String,
        val hp: Int,
        val maxHp: Int,
        val ac: Int,
        val status: String,
        val down: Boolean,
        val current: Boolean,
    )

    companion object {
        fun of(
            state: GameState,
            glossary: Glossary = Glossary.czech,
        ): StatusSnapshot {
            val rows =
                state.party.map { c ->
                    Row(
                        id = c.id,
                        label = c.name,
                        hp = c.hp,
                        maxHp = c.maxHp,
                        ac = c.armorClass(),
                        status = glossary.healthLabel(c),
                        down = c.health != Health.OK,
                        current = c.id == state.currentActorId,
                    )
                }
            val active = state.monsters.filter { it.isAlive && !it.fled }
            val enemies =
                if (active.isEmpty()) {
                    "—"
                } else {
                    active
                        .groupingBy { it.monsterId ?: it.name }
                        .eachCount()
                        .entries
                        .joinToString(", ") { (type, n) -> glossary.count("monster.$type", n) }
                }
            return StatusSnapshot(
                title = state.scene?.title ?: glossary["term.party"],
                phase = state.phase,
                rows = rows,
                enemies = enemies,
                currentActorLabel = state.currentActor?.let { glossary.combatantLabel(it) },
            )
        }
    }
}
