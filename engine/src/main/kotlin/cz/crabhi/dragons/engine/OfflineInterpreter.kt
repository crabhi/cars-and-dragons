package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.Proficiency
import cz.crabhi.dragons.rules.Spell

/**
 * Keyword-based Czech intent parser. Used when no language model is available and for out-of-game
 * commands, which are always recognized locally so they work without signal.
 */
class OfflineInterpreter(
    private val glossary: Glossary,
) {
    private val spellStems: Map<Spell, List<String>> =
        mapOf(
            Spell.SLEEP to listOf("usp", "spanek", "spank"),
            Spell.MAGIC_MISSILE to listOf("magick"),
            Spell.SHIELD to listOf("stit"),
            Spell.CHARM_PERSON to listOf("okouzl"),
            Spell.LIGHT to listOf("svetl", "oslep"),
            Spell.DETECT_MAGIC to listOf("odhal", "detek"),
            Spell.PROTECTION_FROM_EVIL to listOf("ochran"),
            Spell.CURE_LIGHT_WOUNDS to listOf("lec", "vylec", "uzdrav", "hoj"),
            Spell.BLESS to listOf("pozehn"),
            Spell.COMMAND to listOf("rozkaz", "prikaz", "porouc"),
        )

    private val checkStems: List<Pair<List<String>, ProposedAction.Check>> =
        listOf(
            listOf("pliz", "potich", "nepozorovan") to ProposedAction.Check("dex", Proficiency.MOVING_SILENTLY.id),
            listOf("schov", "skryj", "skryv", "ukryj") to ProposedAction.Check("dex", Proficiency.HIDING.id),
            listOf("odemk", "zamek", "zamk", "paklic") to ProposedAction.Check("dex", Proficiency.OPENING_LOCKS.id),
            listOf("stopy", "stopuj", "stopovat") to ProposedAction.Check("wis", Proficiency.TRACKING.id),
            listOf("poslouch", "naslouch") to ProposedAction.Check("wis", Proficiency.LISTENING.id),
            listOf("splh", "vylez", "lezu", "slez") to ProposedAction.Check("con", Proficiency.MOUNTAINEERING.id),
            listOf("plav", "preplav") to ProposedAction.Check("str", Proficiency.SWIMMING.id),
            listOf("vzpomen", "histori", "legend") to ProposedAction.Check("int", Proficiency.ANCIENT_HISTORY.id),
            listOf("vypac", "vyraz", "zvedn", "odval") to ProposedAction.Check("str"),
            listOf("prohled", "hledam", "pohled", "zkoum", "patram", "rozhled") to ProposedAction.Check("wis"),
        )

    private val attackStems =
        listOf(
            "utoc",
            "zautoc",
            "sekn",
            "sekam",
            "bodn",
            "bodam",
            "uder",
            "mlat",
            "bij",
            "zasahn",
            "tluc",
            "strel",
            "stril",
            "vystrel",
            "bojuj",
            "napadn",
            "zabij",
            "rubu",
            "rubam",
            "seknu",
            "praskn",
            "trefim",
            "mirim",
        )
    private val rangedStems = listOf("strel", "stril", "vystrel", "luk", "kus", "prak", "hodim", "mirim")
    private val potionStems = listOf("lektvar", "napij", "vypij", "napoj")
    private val defendStems = listOf("bran", "kryj", "vyck", "cekam", "uhyb", "kryt")
    private val fleeStems = listOf("utec", "utik", "prchn", "prchame", "ustoup", "zdrhn")
    private val restStems = listOf("odpoc", "tabor", "vyspi", "prespi", "utabor")
    private val advanceStems = listOf("dal", "pokrac", "vyraz", "jdeme", "pujdeme", "vstoup", "postoup", "jedeme", "vydame")
    private val selfWords = setOf("sebe", "sobe", "se", "mne", "me", "mi")

    /** Recognizes out-of-game commands. Returns null for ordinary game actions. */
    fun meta(utterance: String): MetaCommand? {
        val n = CzechText.normalize(utterance)
        return when {
            listOf("konec hry", "ukonci hru", "ukoncit hru", "uloz hru", "uloz a skonc").any { it in n } -> MetaCommand.QUIT

            listOf("zopakuj", "co jsi rikal", "cos rikal", "jeste jednou").any { it in n } -> MetaCommand.REPEAT

            listOf("stav druziny", "stav hry", "jak jsme na tom", "jak na tom jsme", "kolik mam zivot", "kolik mame zivot")
                .any { it in n } -> MetaCommand.STATUS

            else -> null
        }
    }

    fun interpret(
        state: GameState,
        actor: Combatant,
        utterance: String,
    ): ProposedAction {
        meta(utterance)?.let { return ProposedAction.Meta(it) }
        val tokens = CzechText.tokens(utterance)
        val inCombat = state.phase == Phase.COMBAT

        if (CzechText.containsAnyStem(tokens, potionStems)) {
            return ProposedAction.UseItem("healing_potion", allyTarget(state, tokens))
        }
        if (actor.spellbook.isNotEmpty()) {
            val spell =
                spellStems.entries.firstOrNull { (spell, stems) ->
                    spell in actor.spellbook &&
                        CzechText.containsAnyStem(tokens, stems)
                }
            if (spell != null) {
                val target =
                    when {
                        spell.key.needsEnemy -> enemyTarget(state, utterance, tokens)
                        else -> allyTarget(state, tokens)
                    }
                return ProposedAction.Cast(spell.key.id, target)
            }
        }
        if (inCombat) {
            if (CzechText.containsAnyStem(tokens, fleeStems)) return ProposedAction.Flee
            if (CzechText.containsAnyStem(tokens, defendStems)) return ProposedAction.Defend
            val ranged = CzechText.containsAnyStem(tokens, rangedStems)
            val target = enemyTarget(state, utterance, tokens)
            if (CzechText.containsAnyStem(tokens, attackStems) || target != null) {
                return ProposedAction.Attack(target, ranged)
            }
            return ProposedAction.Narrative(utterance)
        }
        if (CzechText.containsAnyStem(tokens, restStems)) return ProposedAction.Rest
        checkStems.firstOrNull { (stems, _) -> CzechText.containsAnyStem(tokens, stems) }?.let { return it.second }
        if (CzechText.containsAnyStem(tokens, advanceStems)) return ProposedAction.Advance
        if (CzechText.containsAnyStem(tokens, attackStems)) return ProposedAction.Attack(null)
        return ProposedAction.Narrative(utterance)
    }

    private fun enemyTarget(
        state: GameState,
        utterance: String,
        tokens: List<String>,
    ): String? {
        val enemies = state.monsters.filter { it.isAlive && !it.fled }
        if (enemies.isEmpty()) return null
        val byType =
            enemies.filter { m ->
                val forms = glossary.forms("monster.${m.monsterId}")
                forms.any { form -> CzechText.containsStem(tokens, CzechText.stem(form.split(' ').last())) }
            }
        val number = CzechText.number(utterance)
        val pool = byType.ifEmpty { enemies }
        if (number != null) {
            pool.firstOrNull { it.id.substringAfterLast('-') == number.toString() }?.let { return it.id }
            pool.getOrNull(number - 1)?.let { return it.id }
        }
        return if (byType.isNotEmpty()) (byType.firstOrNull { it.isActiveThreat } ?: byType.first()).id else null
    }

    private fun allyTarget(
        state: GameState,
        tokens: List<String>,
    ): String? {
        if (tokens.any { it in selfWords } && state.party.none { CzechText.containsStem(tokens, CzechText.stem(it.name)) }) return null
        return state.party.firstOrNull { CzechText.containsStem(tokens, CzechText.stem(it.name)) }?.id
    }
}
