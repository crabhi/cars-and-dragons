package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.Health

/**
 * Czech terminology from `glossary.cs`. Values may hold three plural forms separated by `|`
 * (one, few = 2..4, many = 0 and 5+), matching Czech grammar.
 */
class Glossary(
    private val entries: Map<String, String>,
) {
    val keys: Set<String> get() = entries.keys

    fun has(key: String): Boolean = key in entries

    /** Nominative singular form, or the key itself when missing (so gaps are audible in testing). */
    operator fun get(key: String): String = entries[key]?.substringBefore('|') ?: key

    fun forms(key: String): List<String> {
        val parts = entries[key]?.split('|') ?: return listOf(key, key, key)
        return if (parts.size == 3) parts else List(3) { parts.first() }
    }

    fun plural(
        key: String,
        count: Int,
    ): String = forms(key)[pluralIndex(count)]

    /** `5 životů`, `2 životy`, `1 život`. */
    fun count(
        key: String,
        count: Int,
    ): String = "$count ${plural(key, count)}"

    fun monsterName(monsterId: String): String = get("monster.$monsterId")

    /** Label for screens and lists: "goblin 2". */
    fun combatantLabel(c: Combatant): String {
        if (c.isPc) return c.name
        val type = c.monsterId ?: return c.name
        val number = c.id.substringAfterLast('-', "")
        return "${monsterName(type)} $number".trim()
    }

    fun healthLabel(c: Combatant): String =
        when {
            c.fled -> get("status.fled")
            c.health == Health.DEAD -> get("health.dead")
            c.health == Health.UNCONSCIOUS -> get("health.unconscious")
            c.conditions.isNotEmpty() -> c.conditions.joinToString(", ") { get("condition.${it.type.name.lowercase()}") }
            else -> get("health.ok")
        }

    /** Glossary as prompt text for the narrator. */
    fun asPromptTable(): String =
        entries
            .filterKeys { !it.startsWith("msg.") && !it.startsWith("reject.") }
            .entries
            .joinToString("\n") { (k, v) -> "$k = $v" }

    companion object {
        fun pluralIndex(count: Int): Int =
            when (count) {
                1 -> 0
                in 2..4 -> 1
                else -> 2
            }

        fun parse(text: String): Glossary {
            val map = linkedMapOf<String, String>()
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
                .forEach { line ->
                    map[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
                }
            return Glossary(map)
        }

        val czech: Glossary by lazy {
            val stream = Glossary::class.java.getResourceAsStream("/glossary.cs") ?: error("glossary.cs missing")
            parse(stream.bufferedReader(Charsets.UTF_8).use { it.readText() })
        }
    }
}
