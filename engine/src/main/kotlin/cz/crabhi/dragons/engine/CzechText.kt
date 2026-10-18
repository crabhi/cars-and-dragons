package cz.crabhi.dragons.engine

import java.text.Normalizer

/** Helpers for matching spoken Czech: lowercase, no diacritics, word tokens. */
object CzechText {
    private val marks = Regex("\\p{M}+")
    private val nonWord = Regex("[^a-z0-9]+")

    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(marks, "")

    fun tokens(text: String): List<String> = normalize(text).split(nonWord).filter { it.isNotEmpty() }

    /** Stem used to match inflected forms: "thorin" matches "thorina", "thorinovi". */
    fun stem(word: String): String {
        val w = normalize(word)
        return w.take(maxOf(3, w.length - 2))
    }

    fun containsStem(
        tokens: List<String>,
        stem: String,
    ): Boolean = tokens.any { it.startsWith(stem) }

    fun containsAnyStem(
        tokens: List<String>,
        stems: Collection<String>,
    ): Boolean = stems.any { containsStem(tokens, it) }

    private val numberWords =
        mapOf(
            1 to listOf("jedn", "jeden", "jedna", "prvn", "jednick"),
            2 to listOf("dva", "dve", "druh", "dvojk"),
            3 to listOf("tri", "tret", "trojk"),
            4 to listOf("ctyr", "ctvrt", "ctyrk"),
            5 to listOf("pet", "pat"),
            6 to listOf("sest"),
        )

    /** First number in the text, spoken ("dva", "druhého") or written ("2"). */
    fun number(text: String): Int? {
        for (token in tokens(text)) {
            token.toIntOrNull()?.let { return it }
            for ((n, stems) in numberWords) {
                if (stems.any { token.startsWith(it) }) return n
            }
        }
        return null
    }
}
