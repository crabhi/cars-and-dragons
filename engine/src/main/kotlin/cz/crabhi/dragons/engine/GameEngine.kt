package cz.crabhi.dragons.engine

import cz.crabhi.dragons.rules.Ability
import cz.crabhi.dragons.rules.Bestiary
import cz.crabhi.dragons.rules.Checks
import cz.crabhi.dragons.rules.Combat
import cz.crabhi.dragons.rules.Combatant
import cz.crabhi.dragons.rules.ConditionType
import cz.crabhi.dragons.rules.Experience
import cz.crabhi.dragons.rules.Health
import cz.crabhi.dragons.rules.Item
import cz.crabhi.dragons.rules.Proficiency
import cz.crabhi.dragons.rules.SeededDice
import cz.crabhi.dragons.rules.Side
import cz.crabhi.dragons.rules.Spell
import cz.crabhi.dragons.rules.SpellTarget
import cz.crabhi.dragons.rules.Spellcasting
import kotlinx.serialization.Serializable

data class TurnStart(
    /** Who acts now, or null when the game has ended. */
    val actor: Combatant?,
    /** Things that happened before this turn: a new round, skipped turns, expired conditions. */
    val events: List<GameEvent>,
)

/** A legal monster action enumerated by the engine; the tactics advisor picks one. */
@Serializable
sealed interface MonsterOption {
    @Serializable
    data class AttackTarget(
        val targetId: String,
    ) : MonsterOption

    @Serializable
    data object Flee : MonsterOption

    @Serializable
    data object Wait : MonsterOption
}

/** What the current actor may legally do; given to the narrator so it proposes sensible actions. */
@Serializable
data class LegalOptions(
    val phase: Phase,
    val enemies: List<String>,
    val allies: List<String>,
    val spells: List<String>,
    val items: Map<String, Int>,
    val hasRangedWeapon: Boolean,
    val canRest: Boolean,
    val canAdvance: Boolean,
    val actionTypes: List<String>,
)

/**
 * Deterministic game engine. Validates proposed actions against the rules and resolves them with the
 * seeded dice. Owns all mutation of [GameState].
 */
class GameEngine(
    val state: GameState,
    val dice: SeededDice = SeededDice(state.diceState),
) {
    private fun sync() {
        state.diceState = dice.state
    }

    // ---------------------------------------------------------------- setup and scenes

    fun startGame(
        party: List<Combatant>,
        adventure: Adventure,
    ): List<GameEvent> {
        require(AdventureValidator.validate(adventure).isEmpty()) { "Invalid adventure" }
        state.party.clear()
        state.party.addAll(party)
        state.adventure = adventure
        state.premise = adventure.premise
        return enterScene(0)
    }

    private fun enterScene(index: Int): List<GameEvent> {
        val adventure = requireNotNull(state.adventure)
        state.sceneIndex = index
        state.restedInScene = false
        state.encounterDone = false
        state.moraleChecked = false
        state.explorationTurns = 0
        state.turnIndex = 0
        state.currentActorId = null
        val scene = adventure.scenes[index]
        val events = mutableListOf<GameEvent>(GameEvent.SceneEntered(index, scene.id, index == adventure.scenes.lastIndex))
        val encounter = scene.encounter
        if (encounter != null) {
            events += startEncounter(encounter)
        } else {
            state.phase = Phase.EXPLORATION
        }
        sync()
        return events
    }

    private fun startEncounter(encounter: Encounter): List<GameEvent> {
        state.monsters.clear()
        for (group in encounter.monsters) {
            repeat(group.count) { i ->
                state.monsters += Bestiary.spawn(group.monster, "${group.monster}-${i + 1}", dice)
            }
        }
        state.phase = Phase.COMBAT
        state.round = 0
        state.turnOrder.clear()
        state.turnIndex = 0
        state.initiative = null
        return listOf(GameEvent.EncounterStarted(state.monsters.map { it.id }))
    }

    // ---------------------------------------------------------------- turns

    /**
     * Starts the next turn, or returns the turn already in progress (after a resume). Handles round
     * boundaries, initiative and skipped turns.
     */
    fun beginTurn(): TurnStart {
        state.currentActor?.let { return TurnStart(it, emptyList()) }
        val events = mutableListOf<GameEvent>()
        var guard = 0
        while (guard++ < MAX_TURN_SEARCH) {
            when (state.phase) {
                Phase.EXPLORATION -> {
                    val actor = nextExplorer(events)
                    if (actor != null) {
                        state.currentActorId = actor.id
                        sync()
                        return TurnStart(actor, events)
                    }
                }

                Phase.COMBAT -> {
                    if (state.turnIndex >= state.turnOrder.size) {
                        events += endRound()
                        if (state.phase != Phase.COMBAT) continue
                        events += startRound()
                    }
                    val c = state.find(state.turnOrder[state.turnIndex++]) ?: continue
                    if (!c.isAlive || c.fled) continue
                    if (c.has(ConditionType.COMMANDED)) {
                        c.removeCondition(ConditionType.COMMANDED)
                        events += GameEvent.TurnSkipped(c.id, ConditionType.COMMANDED)
                        continue
                    }
                    if (!c.canAct) {
                        skipReason(c)?.let { events += GameEvent.TurnSkipped(c.id, it) }
                        continue
                    }
                    state.currentActorId = c.id
                    sync()
                    return TurnStart(c, events)
                }

                else -> {
                    break
                }
            }
        }
        sync()
        return TurnStart(null, events)
    }

    private fun skipReason(c: Combatant): ConditionType? =
        listOf(ConditionType.PARALYZED, ConditionType.ASLEEP).firstOrNull { c.has(it) && c.health == Health.OK }

    private fun nextExplorer(events: MutableList<GameEvent>): Combatant? {
        if (state.party.none { it.canAct }) {
            if (state.party.none { it.isAlive }) {
                state.phase = Phase.DEFEAT
                events += GameEvent.PartyDefeated
                return null
            }
            // Out of combat, unconscious characters come round after a while.
            events += rest(free = true)
        }
        val size = state.party.size
        repeat(size) {
            val c = state.party[state.turnIndex % size]
            state.turnIndex = (state.turnIndex + 1) % size
            if (c.canAct) return c
        }
        return null
    }

    private fun startRound(): List<GameEvent> {
        state.round += 1
        val initiative = Combat.rollInitiative(dice)
        state.initiative = initiative
        val partyIds = state.party.filter { it.isAlive }.map { it.id }
        val monsterIds = state.monsters.filter { it.isAlive && !it.fled }.map { it.id }
        state.turnOrder = (if (initiative.first == Side.PARTY) partyIds + monsterIds else monsterIds + partyIds).toMutableList()
        state.turnIndex = 0
        return listOf(GameEvent.RoundStarted(state.round, initiative))
    }

    private fun endRound(): List<GameEvent> {
        if (state.round == 0) return checkCombatEnd()
        val events = mutableListOf<GameEvent>()
        val expired = Combat.endRound(state.all).map { (c, type) -> ExpiredCondition(c.id, type) }
        if (expired.isNotEmpty()) events += GameEvent.ConditionsExpired(expired)
        if (!state.moraleChecked) {
            val beaten = state.monsters.count { !it.isActiveThreat && !it.fled }
            val active = state.monsters.filter { it.isActiveThreat }
            if (active.isNotEmpty() && beaten * 2 >= state.monsters.size) {
                state.moraleChecked = true
                val morale = active.maxOf { it.morale }
                if (!Combat.moraleHolds(morale, dice)) {
                    active.forEach { it.fled = true }
                    events += GameEvent.MonstersFled(active.map { it.id })
                }
            }
        }
        events += checkCombatEnd()
        return events
    }

    private fun endTurn(events: MutableList<GameEvent>): List<GameEvent> {
        state.currentActorId = null
        events += checkCombatEnd()
        sync()
        return events
    }

    private fun checkCombatEnd(): List<GameEvent> {
        if (state.phase != Phase.COMBAT) return emptyList()
        if (state.party.none { it.health == Health.OK }) {
            state.phase = Phase.DEFEAT
            state.currentActorId = null
            return listOf(GameEvent.PartyDefeated)
        }
        if (state.monsters.any { it.isActiveThreat }) return emptyList()
        val defeated = state.monsters.filter { !it.fled }
        val xp = Experience.award(state.party, defeated.sumOf { it.xpValue }, dice)
        val events = mutableListOf<GameEvent>(GameEvent.EncounterWon(defeated.map { it.id }, xp))
        finishCombat()
        if (state.sceneIndex == state.adventure!!.scenes.lastIndex) {
            state.phase = Phase.VICTORY
            events += GameEvent.AdventureCompleted
        }
        return events
    }

    private fun finishCombat() {
        state.monsters.clear()
        state.phase = Phase.EXPLORATION
        state.encounterDone = true
        state.round = 0
        state.turnOrder.clear()
        state.turnIndex = 0
        state.initiative = null
        state.currentActorId = null
        val combatOnly =
            setOf(
                ConditionType.BLESSED,
                ConditionType.SHIELDED,
                ConditionType.BLINDED,
                ConditionType.PARALYZED,
                ConditionType.COMMANDED,
                ConditionType.ASLEEP,
            )
        state.party.forEach { pc -> pc.conditions.removeAll { it.type in combatOnly } }
    }

    // ---------------------------------------------------------------- player actions

    fun legalOptions(actor: Combatant): LegalOptions {
        val inCombat = state.phase == Phase.COMBAT
        val types =
            buildList {
                if (inCombat) addAll(listOf("attack", "cast", "use_item", "defend", "flee", "narrative"))
                if (!inCombat) addAll(listOf("cast", "use_item", "check", "narrative", "advance"))
                if (!inCombat && !state.restedInScene) add("rest")
            }
        return LegalOptions(
            phase = state.phase,
            enemies = activeEnemies().map { it.id },
            allies = state.party.filter { it.isAlive }.map { it.id },
            spells = actor.memorized.map { it.id }.distinct(),
            items = actor.inventory.filterValues { it > 0 }.mapKeys { it.key.id },
            hasRangedWeapon = actor.weapons.any { it.missile },
            canRest = !inCombat && !state.restedInScene,
            canAdvance = !inCombat,
            actionTypes = types,
        )
    }

    private fun activeEnemies(): List<Combatant> = state.monsters.filter { it.isAlive && !it.fled }

    private fun enemyTarget(id: String?): Combatant? {
        val enemies = activeEnemies()
        if (id == null) return enemies.firstOrNull { it.isActiveThreat } ?: enemies.firstOrNull()
        return enemies.firstOrNull { it.id == id }
    }

    private fun allyTarget(
        actor: Combatant,
        id: String?,
    ): Combatant? = if (id == null) actor else state.party.firstOrNull { it.id == id }

    fun validate(
        actor: Combatant,
        action: ProposedAction,
    ): Rejection? {
        if (actor.id != state.currentActorId) return Rejection.NOT_YOUR_TURN
        if (!actor.canAct) return Rejection.CANNOT_ACT
        val inCombat = state.phase == Phase.COMBAT
        return when (action) {
            is ProposedAction.Attack -> {
                validateAttack(actor, action, inCombat)
            }

            is ProposedAction.Cast -> {
                validateCast(actor, action, inCombat)
            }

            is ProposedAction.UseItem -> {
                validateItem(actor, action)
            }

            ProposedAction.Flee -> {
                if (inCombat) null else Rejection.NOT_IN_COMBAT
            }

            ProposedAction.Rest -> {
                when {
                    inCombat -> Rejection.IN_COMBAT
                    state.restedInScene -> Rejection.ALREADY_RESTED
                    else -> null
                }
            }

            ProposedAction.Advance -> {
                if (inCombat) Rejection.IN_COMBAT else null
            }

            is ProposedAction.Check -> {
                if (inCombat) {
                    Rejection.IN_COMBAT
                } else if (parseAbility(action) == null) {
                    Rejection.UNKNOWN_ABILITY
                } else {
                    null
                }
            }

            ProposedAction.Defend, is ProposedAction.Narrative, is ProposedAction.Meta -> {
                null
            }
        }
    }

    private fun validateAttack(
        actor: Combatant,
        action: ProposedAction.Attack,
        inCombat: Boolean,
    ): Rejection? {
        if (!inCombat) return Rejection.NO_ENEMIES
        if (action.ranged && actor.weapons.none { it.missile }) return Rejection.NO_RANGED_WEAPON
        if (enemyTarget(action.target) != null) return null
        return if (state.find(action.target) != null) Rejection.INVALID_TARGET else Rejection.UNKNOWN_TARGET
    }

    private fun validateCast(
        actor: Combatant,
        action: ProposedAction.Cast,
        inCombat: Boolean,
    ): Rejection? {
        val spell = Spell.byId(action.spell) ?: return Rejection.UNKNOWN_SPELL
        if (!Spellcasting.knows(actor, spell)) return Rejection.SPELL_NOT_KNOWN
        if (spell !in actor.memorized) return Rejection.SPELL_NOT_MEMORIZED
        return when (spell.target) {
            SpellTarget.ENEMY -> {
                when {
                    spell == Spell.LIGHT && !inCombat -> null
                    !inCombat -> Rejection.NO_ENEMIES
                    enemyTarget(action.target) != null -> null
                    state.find(action.target) != null -> Rejection.INVALID_TARGET
                    else -> Rejection.UNKNOWN_TARGET
                }
            }

            SpellTarget.ENEMIES -> {
                if (inCombat) null else Rejection.NO_ENEMIES
            }

            SpellTarget.ALLY -> {
                val target = allyTarget(actor, action.target)
                when {
                    target == null -> if (state.find(action.target) != null) Rejection.INVALID_TARGET else Rejection.UNKNOWN_TARGET
                    !target.isAlive -> Rejection.INVALID_TARGET
                    else -> null
                }
            }

            SpellTarget.SELF, SpellTarget.PARTY, SpellTarget.NONE -> {
                null
            }
        }
    }

    private fun validateItem(
        actor: Combatant,
        action: ProposedAction.UseItem,
    ): Rejection? {
        val item = Item.byId(action.item) ?: return Rejection.UNKNOWN_ITEM
        if ((actor.inventory[item] ?: 0) <= 0) return Rejection.NO_ITEM
        if (item == Item.HEALING_POTION) {
            val target = allyTarget(actor, action.target) ?: return Rejection.UNKNOWN_TARGET
            if (!target.isAlive) return Rejection.INVALID_TARGET
        }
        return null
    }

    private fun parseAbility(action: ProposedAction.Check): Ability? {
        val prof = action.proficiency?.let { Proficiency.byId(it) }
        return Ability.entries.firstOrNull { it.name.equals(action.ability, ignoreCase = true) } ?: prof?.ability
    }

    /** Resolves a validated action and ends the actor's turn. */
    fun resolve(
        actor: Combatant,
        action: ProposedAction,
    ): List<GameEvent> {
        val rejection = validate(actor, action)
        require(rejection == null) { "Illegal action $action: $rejection" }
        val events = mutableListOf<GameEvent>()
        when (action) {
            is ProposedAction.Attack -> {
                val target = enemyTarget(action.target)!!
                val weapon =
                    if (action.ranged) actor.weapons.first { it.missile } else actor.weapons.firstOrNull { !it.missile }
                val attack = actor.attacks().firstOrNull { it.weapon == weapon } ?: actor.attacks().first()
                events += GameEvent.Attacked(Combat.attack(actor, target, attack, dice))
            }

            is ProposedAction.Cast -> {
                events += castSpell(actor, action)
            }

            is ProposedAction.UseItem -> {
                events += useItem(actor, action)
            }

            ProposedAction.Defend -> {
                events += GameEvent.Defended(actor.id)
            }

            ProposedAction.Flee -> {
                val roll = dice.d10()
                val success = roll <= FLEE_CHANCE
                events += GameEvent.FleeAttempt(actor.id, roll, success)
                if (success) {
                    finishCombat()
                    state.encounterDone = true
                    events += GameEvent.EncounterEscaped
                }
            }

            ProposedAction.Rest -> {
                events += rest(free = false)
            }

            ProposedAction.Advance -> {
                state.currentActorId = null
                if (state.sceneIndex >= state.adventure!!.scenes.lastIndex) {
                    state.phase = Phase.VICTORY
                    events += GameEvent.AdventureCompleted
                } else {
                    events += enterScene(state.sceneIndex + 1)
                }
            }

            is ProposedAction.Check -> {
                val prof = action.proficiency?.let { Proficiency.byId(it) }
                val ability = parseAbility(action)!!
                events += GameEvent.CheckMade(actor.id, ability, prof, Checks.check(actor, ability, prof, dice))
            }

            is ProposedAction.Narrative -> {
                events += GameEvent.NarrativeAction(actor.id, action.summary)
            }

            is ProposedAction.Meta -> {
                Unit
            }
        }
        if (state.phase == Phase.EXPLORATION) state.explorationTurns++
        return endTurn(events)
    }

    private fun castSpell(
        actor: Combatant,
        action: ProposedAction.Cast,
    ): GameEvent {
        val spell = Spell.byId(action.spell)!!
        val targets =
            when (spell.target) {
                SpellTarget.ENEMY -> listOfNotNull(enemyTarget(action.target))
                SpellTarget.ENEMIES -> activeEnemies()
                SpellTarget.ALLY -> listOf(allyTarget(actor, action.target)!!)
                SpellTarget.SELF -> listOf(actor)
                SpellTarget.PARTY -> state.party.filter { it.isAlive }
                SpellTarget.NONE -> emptyList()
            }
        if (spell == Spell.LIGHT && targets.isEmpty()) {
            // Plain light outside combat: illuminates the area, no target.
            actor.memorized.remove(spell)
            return GameEvent.SpellCast(cz.crabhi.dragons.rules.SpellResult(actor.id, spell, emptyList()))
        }
        val result = Spellcasting.cast(actor, spell, targets, dice, magicPresent = state.scene?.magicItem == true)
        return GameEvent.SpellCast(result)
    }

    private fun useItem(
        actor: Combatant,
        action: ProposedAction.UseItem,
    ): GameEvent {
        val item = Item.byId(action.item)!!
        actor.inventory[item] = (actor.inventory[item] ?: 1) - 1
        val target = if (item == Item.HEALING_POTION) allyTarget(actor, action.target)!! else actor
        val healed = if (item == Item.HEALING_POTION) Combat.heal(target, Item.HEALING_POTION_DICE.roll(dice)) else 0
        return GameEvent.ItemUsed(actor.id, item, target.id, healed, target.hp)
    }

    /** A night's rest: restores spells, heals 1 HP per level, unconscious characters wake with 1 HP. */
    private fun rest(free: Boolean): GameEvent {
        if (!free) state.restedInScene = true
        val healed = mutableMapOf<String, Int>()
        for (c in state.party.filter { it.isAlive }) {
            val before = c.hp
            if (c.hp <= 0) c.hp = 1
            if (!free) {
                Combat.heal(c, c.level)
                Spellcasting.rest(c)
            }
            healed[c.id] = c.hp - before
        }
        return GameEvent.Rested(healed)
    }

    // ---------------------------------------------------------------- monsters

    fun monsterOptions(monster: Combatant): List<MonsterOption> {
        val options = mutableListOf<MonsterOption>()
        state.party.filter { it.health == Health.OK }.forEach { options += MonsterOption.AttackTarget(it.id) }
        if (monster.hp * 2 <= monster.maxHp && monster.morale < MORALE_FEARLESS) options += MonsterOption.Flee
        if (options.isEmpty()) options += MonsterOption.Wait
        return options
    }

    fun resolveMonster(
        monster: Combatant,
        option: MonsterOption,
    ): List<GameEvent> {
        require(monster.id == state.currentActorId) { "Not ${monster.id}'s turn" }
        require(option in monsterOptions(monster)) { "Illegal monster option $option" }
        val events = mutableListOf<GameEvent>()
        when (option) {
            is MonsterOption.AttackTarget -> {
                val target = state.find(option.targetId)!!
                for (attack in monster.attacks()) {
                    if (target.health != Health.OK) break
                    events += GameEvent.Attacked(Combat.attack(monster, target, attack, dice))
                }
            }

            MonsterOption.Flee -> {
                monster.fled = true
                events += GameEvent.MonstersFled(listOf(monster.id))
            }

            MonsterOption.Wait -> {
                Unit
            }
        }
        return endTurn(events)
    }

    companion object {
        private const val MAX_TURN_SEARCH = 500
        const val FLEE_CHANCE = 5
        const val MORALE_FEARLESS = 20
    }
}
