package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Bestiary
import kotlinx.serialization.Serializable

@Serializable
data class Npc(
    val name: String,
    val role: String,
)

@Serializable
data class MonsterGroup(
    val monster: String,
    val count: Int,
)

@Serializable
data class Encounter(
    val monsters: List<MonsterGroup>,
)

/**
 * One scene of the adventure outline. Text fields are Czech (they are narrated); [id] and monster ids
 * are English.
 */
@Serializable
data class Scene(
    val id: String,
    val title: String,
    val location: String,
    val description: String,
    val goal: String = "",
    val npcs: List<Npc> = emptyList(),
    val encounter: Encounter? = null,
    val magicItem: Boolean = false,
)

@Serializable
data class Adventure(
    val title: String,
    val premise: String,
    val scenes: List<Scene>,
)

object AdventureValidator {
    const val MIN_SCENES = 3
    const val MAX_SCENES = 5
    const val MAX_MONSTERS_PER_ENCOUNTER = 8

    /** Returns English error messages; empty when the outline is acceptable. */
    fun validate(adventure: Adventure): List<String> {
        val errors = mutableListOf<String>()
        if (adventure.title.isBlank()) errors += "Adventure title is empty."
        if (adventure.scenes.size !in MIN_SCENES..MAX_SCENES) {
            errors += "Adventure must have $MIN_SCENES to $MAX_SCENES scenes, got ${adventure.scenes.size}."
        }
        val ids = adventure.scenes.map { it.id }
        if (ids.toSet().size != ids.size) errors += "Scene ids must be unique."
        adventure.scenes.forEach { scene ->
            if (scene.title.isBlank()) errors += "Scene '${scene.id}' has no title."
            if (scene.description.isBlank()) errors += "Scene '${scene.id}' has no description."
            scene.encounter?.let { enc ->
                if (enc.monsters.isEmpty()) errors += "Scene '${scene.id}' has an encounter without monsters."
                enc.monsters.forEach { group ->
                    if (Bestiary.get(group.monster) == null) {
                        errors +=
                            "Scene '${scene.id}': unknown monster '${group.monster}'. Allowed: ${Bestiary.ids.sorted().joinToString()}."
                    }
                    if (group.count < 1) errors += "Scene '${scene.id}': monster count must be at least 1."
                }
                val total = enc.monsters.sumOf { it.count }
                if (total > MAX_MONSTERS_PER_ENCOUNTER) {
                    errors += "Scene '${scene.id}': at most $MAX_MONSTERS_PER_ENCOUNTER monsters per encounter, got $total."
                }
            }
        }
        if (adventure.scenes.none { it.encounter != null }) errors += "At least one scene must contain an encounter."
        return errors
    }
}
