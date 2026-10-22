package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.AttackEffectType
import cz.crabhi.dragons.rules.AttackResult
import cz.crabhi.dragons.rules.Bestiary
import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.ConditionType
import cz.crabhi.dragons.rules.Health
import cz.crabhi.dragons.rules.Side
import cz.crabhi.dragons.rules.SpellEffect
import cz.crabhi.dragons.rules.SpellResult

/**
 * Narrator that works without network: keyword parsing and template narration. Sentences keep every
 * name in the nominative and use present tense, so no Czech inflection of names is needed.
 */
class OfflineNarrator(
    private val glossary: Glossary = Glossary.czech,
) : Narrator {
    private val interpreter = OfflineInterpreter(glossary)

    override suspend fun createAdventure(
        premise: String,
        party: List<Combatant>,
        feedback: List<String>,
    ): Adventure = OfflineAdventures.create(premise, party.size)

    override suspend fun interpret(
        ctx: NarrationContext,
        actor: Combatant,
        utterance: String,
        options: LegalOptions,
    ): Interpretation = Interpretation(interpreter.interpret(ctx.state, actor, utterance))

    override suspend fun narrate(
        ctx: NarrationContext,
        request: NarrationRequest,
    ): String =
        when (request) {
            is NarrationRequest.Events -> {
                describe(ctx.state, request.events, request.actorId)
            }

            is NarrationRequest.TurnPrompt -> {
                (describe(ctx.state, request.precedingEvents, null) + " " + turnPrompt(ctx.state, request.actorId)).trim()
            }

            is NarrationRequest.Rejected -> {
                glossary[request.rejection.key]
            }

            NarrationRequest.Recap -> {
                recap(ctx.state)
            }
        }

    private fun label(
        state: GameState,
        id: String,
    ): String = state.find(id)?.let { glossary.combatantLabel(it) } ?: labelFromId(id)

    private fun labelFromId(id: String): String {
        val type = id.substringBeforeLast('-')
        return if (Bestiary.get(type) != null) "${glossary.monsterName(type)} ${id.substringAfterLast('-')}" else id
    }

    private fun turnPrompt(
        state: GameState,
        actorId: String,
    ): String {
        val name = label(state, actorId)
        return "${glossary["msg.your_turn"]} $name. ${glossary["msg.what_do"]}"
    }

    fun recap(state: GameState): String {
        val scene = state.scene ?: return glossary["msg.resume"]
        val combat = if (state.phase == Phase.COMBAT) " Probíhá boj, nepřátel zbývá ${state.monsters.count { it.isActiveThreat }}." else ""
        val goal = if (scene.goal.isNotBlank()) " Cíl: ${scene.goal}" else ""
        return "Jste ve scéně ${scene.title}. ${scene.location}.$goal$combat".replace("..", ".")
    }

    fun describe(
        state: GameState,
        events: List<GameEvent>,
        actorId: String?,
    ): String {
        val out = events.flatMap { describe(state, it) }.toMutableList()
        if (out.isEmpty() && actorId != null) out += "${label(state, actorId)} vyčkává."
        return sentenceCase(out.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" "))
    }

    private fun describe(
        state: GameState,
        event: GameEvent,
    ): List<String> =
        when (event) {
            is GameEvent.SceneEntered -> sceneEntered(state, event)
            is GameEvent.EncounterStarted -> listOf("Pozor, nepřátelé! " + enemyList(event.monsterIds) + ".")
            is GameEvent.RoundStarted -> listOf(roundStarted(event))
            is GameEvent.TurnSkipped -> listOf(skipped(state, event))
            is GameEvent.Attacked -> listOf(attack(state, event.result))
            is GameEvent.SpellCast -> listOf(spell(state, event.result))
            is GameEvent.ItemUsed -> listOf(itemUsed(state, event))
            is GameEvent.Defended -> listOf("${label(state, event.actorId)} se kryje a vyčkává.")
            is GameEvent.FleeAttempt -> listOf(if (event.success) "Družina prchá z boje!" else "Útěk se nedaří, nepřátelé nepustí.")
            is GameEvent.MonstersFled -> listOf(monstersFled(state, event))
            is GameEvent.ConditionsExpired -> conditionsExpired(state, event)
            is GameEvent.CheckMade -> listOf(check(event))
            is GameEvent.NarrativeAction -> listOf(improvise(state))
            is GameEvent.Rested -> listOf("Družina odpočívá. Síly se vracejí a kouzla jsou znovu připravená.")
            is GameEvent.EncounterWon -> encounterWon(state, event)
            GameEvent.EncounterEscaped -> listOf("Podařilo se vám uniknout. Řekněte: jdeme dál.")
            GameEvent.AdventureCompleted -> listOf(glossary["msg.victory"])
            GameEvent.PartyDefeated -> listOf(glossary["msg.defeat"])
        }

    private fun sceneEntered(
        state: GameState,
        event: GameEvent.SceneEntered,
    ): List<String> {
        val scene = state.adventure?.scenes?.getOrNull(event.index) ?: return emptyList()
        return buildList {
            add("${scene.title}. ${scene.description}")
            if (scene.npcs.isNotEmpty()) add("Potkáte: " + scene.npcs.joinToString(", ") { "${it.name}, ${it.role}" } + ".")
            if (scene.goal.isNotBlank() && scene.encounter == null) add("Cíl: ${scene.goal}")
        }
    }

    private fun roundStarted(event: GameEvent.RoundStarted): String =
        if (event.initiative.first == Side.PARTY) {
            "Kolo ${event.round}. Družina jedná první."
        } else {
            "Kolo ${event.round}. Nepřátelé jednají první."
        }

    private fun itemUsed(
        state: GameState,
        event: GameEvent.ItemUsed,
    ): String {
        val used = "${label(state, event.actorId)} používá ${glossary["item.${event.item.id}"]}."
        if (event.healed <= 0) return used
        return "$used ${label(state, event.targetId)} se uzdravuje o ${glossary.count("term.hp", event.healed)}."
    }

    private fun monstersFled(
        state: GameState,
        event: GameEvent.MonstersFled,
    ): String =
        if (event.monsterIds.size == 1) {
            "${label(state, event.monsterIds.first())} prchá z boje."
        } else {
            "Nepřátelé ztrácejí odvahu a prchají!"
        }

    private fun conditionsExpired(
        state: GameState,
        event: GameEvent.ConditionsExpired,
    ): List<String> =
        event.expired
            .filter { it.condition in setOf(ConditionType.ASLEEP, ConditionType.PARALYZED, ConditionType.BLINDED) }
            .map { "${label(state, it.combatantId)} se znovu může naplno hýbat." }

    private fun check(event: GameEvent.CheckMade): String {
        val what = event.proficiency?.let { glossary["proficiency.${it.id}"] } ?: glossary["ability.${event.ability.name.lowercase()}"]
        return "Zkouška – $what. " + if (event.result.success) "Daří se!" else "Tentokrát to nevyšlo."
    }

    private fun encounterWon(
        state: GameState,
        event: GameEvent.EncounterWon,
    ): List<String> =
        buildList {
            add("Vítězství! Nepřátelé jsou poraženi.")
            if (event.xp.perCharacter > 0) add("Každý získává ${glossary.count("term.xp_points", event.xp.perCharacter)}.")
            event.xp.levelUps.forEach { add("${label(state, it.characterId)} postupuje na úroveň ${it.newLevel}!") }
            if (!state.isFinished) add("Až budete připraveni, řekněte: jdeme dál.")
        }

    private fun enemyList(ids: List<String>): String =
        ids
            .groupingBy { it.substringBeforeLast('-') }
            .eachCount()
            .entries
            .joinToString(", ") { (type, count) ->
                if (count == 1) glossary.monsterName(type) else glossary.count("monster.$type", count)
            }

    private fun skipped(
        state: GameState,
        event: GameEvent.TurnSkipped,
    ): String {
        val who = label(state, event.actorId)
        return when (event.reason) {
            ConditionType.COMMANDED -> "$who poslouchá rozkaz a ztrácí tah."
            ConditionType.PARALYZED -> "$who se nemůže ani pohnout."
            ConditionType.ASLEEP -> ""
            else -> "$who vynechává tah."
        }
    }

    private fun attack(
        state: GameState,
        r: AttackResult,
    ): String {
        val attacker = label(state, r.attackerId)
        val target = label(state, r.targetId)
        if (!r.hit) return "$attacker útočí, ale $target uhýbá."
        val parts = mutableListOf("$attacker útočí. $target dostává zásah za ${glossary.count("term.damage", r.damage)}.")
        r.effect?.let { e ->
            when {
                e.immune -> Unit
                e.amount == 0 -> parts += "$target odolává ${if (e.type == AttackEffectType.POISON) "jedu" else "ochromení"}."
                e.type == AttackEffectType.POISON -> parts += "Jed účinkuje!"
                e.type == AttackEffectType.PARALYSIS -> parts += "$target se nemůže pohnout."
            }
        }
        parts += healthChange(target, r.healthBefore, r.healthAfter, state.find(r.targetId))
        return parts.joinToString(" ")
    }

    private fun healthChange(
        who: String,
        before: Health,
        after: Health,
        c: Combatant?,
    ): String =
        when {
            before == after -> ""
            after == Health.DEAD && c?.isPc == false -> "$who padá a už se nezvedne."
            after == Health.DEAD -> "$who umírá."
            after == Health.UNCONSCIOUS -> "$who padá do bezvědomí."
            else -> ""
        }

    private fun spell(
        state: GameState,
        r: SpellResult,
    ): String {
        val parts = mutableListOf("${label(state, r.casterId)} sesílá kouzlo ${glossary["spell.${r.spell.id}"]}.")
        var blessed = false
        for (e in r.effects) {
            val who = label(state, e.targetId)
            parts +=
                when (e) {
                    is SpellEffect.Damaged -> {
                        "$who dostává ${glossary.count("term.damage", e.amount)}. " +
                            healthChange(who, Health.OK, e.healthAfter, state.find(e.targetId))
                    }

                    is SpellEffect.Healed -> {
                        if (e.amount > 0) "$who se uzdravuje o ${glossary.count("term.hp", e.amount)}." else "$who už je zcela zdráv."
                    }

                    is SpellEffect.ConditionApplied -> {
                        when (e.condition) {
                            ConditionType.ASLEEP -> "$who usíná."
                            ConditionType.CHARMED -> "$who podléhá kouzlu a přestává bojovat."
                            ConditionType.BLESSED -> if (blessed) "" else "Družinu naplňuje požehnání.".also { blessed = true }
                            ConditionType.SHIELDED -> "Kolem sesilatele se třpytí neviditelný štít."
                            ConditionType.PROTECTED_FROM_EVIL -> "$who získává ochranu před zlem."
                            ConditionType.BLINDED -> "Světlo oslepuje cíl: $who."
                            ConditionType.COMMANDED -> "$who poslouchá rozkaz a ztratí příští tah."
                            ConditionType.PARALYZED -> "$who se nemůže pohnout."
                        }
                    }

                    is SpellEffect.Resisted -> {
                        "$who kouzlu odolává."
                    }

                    is SpellEffect.Detected -> {
                        if (e.magicPresent) "Poblíž je cítit magická aura!" else "Žádná magie tu není."
                    }
                }
        }
        if (r.effects.isEmpty()) parts += "Okolí zalévá jasné světlo."
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    private val ambience =
        listOf(
            "Chvíli se nic neděje, jen vítr šumí v korunách stromů.",
            "Okolí je tiché, ale ve vzduchu visí napětí.",
            "Kdesi v dálce zakráká havran.",
            "Stíny se prodlužují a cesta pokračuje dál.",
        )

    private fun improvise(state: GameState): String {
        val line = ambience[(state.explorationTurns + state.sceneIndex) % ambience.size]
        val goal = state.scene?.goal?.takeIf { it.isNotBlank() }?.let { " Cíl: $it" } ?: ""
        val nudge = if (state.explorationTurns >= 2) " Až budete chtít pokračovat, řekněte: jdeme dál." else ""
        return "$line$goal$nudge"
    }

    private fun sentenceCase(text: String): String {
        val sb = StringBuilder(text.replace(Regex("\\s+"), " "))
        var capitalize = true
        for (i in sb.indices) {
            val ch = sb[i]
            if (capitalize && ch.isLetter()) {
                sb.setCharAt(i, ch.uppercaseChar())
                capitalize = false
            } else if (ch == '.' || ch == '!' || ch == '?') {
                capitalize = true
            } else if (!ch.isWhitespace()) {
                capitalize = false
            }
        }
        return sb.toString()
    }

    /** Interpreter exposed for local meta commands. */
    fun meta(utterance: String): MetaCommand? = interpreter.meta(utterance)
}
