package cz.crabhi.dragons.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Every player-facing string must exist in Czech, and Czech plurals need all their forms. */
class StringResourcesTest {
    private fun load(dir: String): Pair<Map<String, String>, Map<String, Set<String>>> {
        val file = listOf(File("src/main/res/$dir/strings.xml"), File("app/src/main/res/$dir/strings.xml")).first { it.exists() }
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as Element
            strings[e.getAttribute("name")] = e.textContent
        }
        val plurals = mutableMapOf<String, Set<String>>()
        val pl = doc.getElementsByTagName("plurals")
        for (i in 0 until pl.length) {
            val e = pl.item(i) as Element
            val items = e.getElementsByTagName("item")
            plurals[e.getAttribute("name")] = (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
        }
        return strings to plurals
    }

    @Test
    fun everyStringHasACzechTranslation() {
        val (defaultStrings, defaultPlurals) = load("values")
        val (czStrings, czPlurals) = load("values-cs")
        assertEquals(emptySet<String>(), defaultStrings.keys - czStrings.keys)
        assertEquals(emptySet<String>(), defaultPlurals.keys - czPlurals.keys)
    }

    @Test
    fun czechPluralsAreComplete() {
        val (_, czPlurals) = load("values-cs")
        czPlurals.forEach { (name, quantities) ->
            assertEquals("Plural $name", setOf("one", "few", "many", "other"), quantities)
        }
    }

    @Test
    fun formatArgumentsMatch() {
        val (defaultStrings, _) = load("values")
        val (czStrings, _) = load("values-cs")
        val arg = Regex("%\\d\\$[sd]")
        defaultStrings.forEach { (name, value) ->
            val cz = czStrings[name] ?: return@forEach
            assertEquals("Arguments of $name", arg.findAll(value).map { it.value }.toSet(), arg.findAll(cz).map { it.value }.toSet())
        }
        assertTrue(czStrings.getValue("app_name").isNotBlank())
    }
}
