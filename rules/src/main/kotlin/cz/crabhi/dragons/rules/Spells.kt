package cz.crabhi.dragons.rules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpellTarget {
    /** Affects the caster only. */
    SELF,

    /** One member of the party (or the caster). */
    ALLY,

    /** One enemy. */
    ENEMY,

    /** A group of enemies chosen by the rules. */
    ENEMIES,

    /** The whole party. */
    PARTY,

    /** No combat target (utility spell). */
    NONE,
}

@Serializable
enum class Spell(
    val casters: Set<CasterType>,
    val target: SpellTarget,
    val usableOutsideCombat: Boolean = true,
) {
    SLEEP(setOf(CasterType.WIZARD), SpellTarget.ENEMIES),
    MAGIC_MISSILE(setOf(CasterType.WIZARD), SpellTarget.ENEMY),
    SHIELD(setOf(CasterType.WIZARD), SpellTarget.SELF),
    CHARM_PERSON(setOf(CasterType.WIZARD), SpellTarget.ENEMY),
    LIGHT(setOf(CasterType.WIZARD, CasterType.PRIEST), SpellTarget.ENEMY),
    DETECT_MAGIC(setOf(CasterType.WIZARD, CasterType.PRIEST), SpellTarget.NONE),
    PROTECTION_FROM_EVIL(setOf(CasterType.WIZARD, CasterType.PRIEST), SpellTarget.ALLY),
    CURE_LIGHT_WOUNDS(setOf(CasterType.PRIEST), SpellTarget.ALLY),
    BLESS(setOf(CasterType.PRIEST), SpellTarget.PARTY),
    COMMAND(setOf(CasterType.PRIEST), SpellTarget.ENEMY),
    ;

    val id: String get() = name.lowercase()

    /** Spells that only make sense against enemies cannot be cast when no fight is going on. */
    val needsEnemy: Boolean get() = target == SpellTarget.ENEMY || target == SpellTarget.ENEMIES

    companion object {
        fun byId(id: String): Spell? = entries.firstOrNull { it.id == id.lowercase().replace('-', '_').replace(' ', '_') }
    }
}

@Serializable
sealed interface SpellEffect {
    val targetId: String

    @Serializable
    @SerialName("damaged")
    data class Damaged(
        override val targetId: String,
        val amount: Int,
        val hpAfter: Int,
        val healthAfter: Health,
    ) : SpellEffect

    @Serializable
    @SerialName("healed")
    data class Healed(
        override val targetId: String,
        val amount: Int,
        val hpAfter: Int,
        val healthAfter: Health,
    ) : SpellEffect

    @Serializable
    @SerialName("condition_applied")
    data class ConditionApplied(
        override val targetId: String,
        val condition: ConditionType,
        val rounds: Int?,
    ) : SpellEffect

    /** The target saved, was immune or resisted by race. */
    @Serializable
    @SerialName("resisted")
    data class Resisted(
        override val targetId: String,
        val reason: ResistReason,
        val save: SaveResult? = null,
    ) : SpellEffect

    @Serializable
    @SerialName("detected")
    data class Detected(
        override val targetId: String,
        val magicPresent: Boolean,
    ) : SpellEffect
}

@Serializable
enum class ResistReason { SAVED, IMMUNE, RACIAL_RESISTANCE, NOT_A_PERSON, TOO_POWERFUL }

@Serializable
data class SpellResult(
    val casterId: String,
    val spell: Spell,
    val effects: List<SpellEffect>,
)

object Spellcasting {
    /** Fills [Combatant.prepared] with spells from the spellbook in priority order until the slots are full. */
    fun prepareDefault(
        caster: Combatant,
        priority: List<Spell>,
    ) {
        val cls = caster.characterClass ?: return
        val abilities = caster.abilities ?: return
        val slots = cls.firstLevelSlots(caster.level, abilities)
        val available = priority.filter { it in caster.spellbook }
        caster.prepared = if (available.isEmpty()) emptyList() else List(slots) { available[it % available.size] }
        caster.memorized = caster.prepared.toMutableList()
    }

    fun knows(
        caster: Combatant,
        spell: Spell,
    ): Boolean = spell in caster.spellbook && caster.characterClass?.casterType in spell.casters

    /** Restores memorized spells after a rest. */
    fun rest(caster: Combatant) {
        caster.memorized = caster.prepared.toMutableList()
    }

    /**
     * Casts [spell]. The caller must already have validated legality (memorized, target types).
     * [targets] are the chosen target(s); for [SpellTarget.ENEMIES] they are the candidate enemies.
     */
    fun cast(
        caster: Combatant,
        spell: Spell,
        targets: List<Combatant>,
        dice: Dice,
        magicPresent: Boolean = false,
    ): SpellResult {
        check(caster.memorized.remove(spell)) { "${caster.id} has not memorized $spell" }
        val level = caster.level
        val effects: List<SpellEffect> =
            when (spell) {
                Spell.SLEEP -> sleep(targets, dice)
                Spell.MAGIC_MISSILE -> magicMissile(targets.single(), level, dice)
                Spell.SHIELD -> listOf(apply(caster, ConditionType.SHIELDED, 5 * level))
                Spell.CHARM_PERSON -> charm(targets.single(), dice)
                Spell.LIGHT -> light(targets.single(), level, dice)
                Spell.DETECT_MAGIC -> listOf(SpellEffect.Detected(caster.id, magicPresent))
                Spell.PROTECTION_FROM_EVIL -> listOf(apply(targets.single(), ConditionType.PROTECTED_FROM_EVIL, 3 * level))
                Spell.CURE_LIGHT_WOUNDS -> cure(targets.single(), dice)
                Spell.BLESS -> targets.filter { it.isAlive }.map { apply(it, ConditionType.BLESSED, 6) }
                Spell.COMMAND -> command(targets.single(), dice)
            }
        return SpellResult(caster.id, spell, effects)
    }

    private fun apply(
        target: Combatant,
        type: ConditionType,
        rounds: Int?,
    ): SpellEffect {
        target.addCondition(Condition(type, rounds))
        return SpellEffect.ConditionApplied(target.id, type, rounds)
    }

    /** Sleep affects 2d4 hit dice of creatures, weakest first; 4+ HD creatures and undead are immune. */
    private fun sleep(
        candidates: List<Combatant>,
        dice: Dice,
    ): List<SpellEffect> {
        var budget = dice.roll(2, 4)
        val effects = mutableListOf<SpellEffect>()
        for (target in candidates.filter { it.canAct }.sortedBy { it.hitDice }) {
            val cost = maxOf(1, target.hitDice)
            when {
                MonsterTag.UNDEAD in target.tags -> {
                    effects += SpellEffect.Resisted(target.id, ResistReason.IMMUNE)
                }

                target.hitDice >= 5 -> {
                    effects += SpellEffect.Resisted(target.id, ResistReason.TOO_POWERFUL)
                }

                cost > budget -> {
                    continue
                }

                target.race != null && dice.percent(target.race.sleepCharmResistance) -> {
                    budget -= cost
                    effects += SpellEffect.Resisted(target.id, ResistReason.RACIAL_RESISTANCE)
                }

                else -> {
                    budget -= cost
                    effects += apply(target, ConditionType.ASLEEP, null)
                }
            }
        }
        return effects
    }

    private fun magicMissile(
        target: Combatant,
        level: Int,
        dice: Dice,
    ): List<SpellEffect> {
        val missiles = 1 + (level - 1) / 2
        val total = (1..missiles).sumOf { d("1d4+1").roll(dice) }
        Combat.applyDamage(target, total)
        return listOf(SpellEffect.Damaged(target.id, total, target.hp, target.health))
    }

    private fun charm(
        target: Combatant,
        dice: Dice,
    ): List<SpellEffect> {
        val isPerson = target.isPc || (MonsterTag.HUMANOID in target.tags && !target.isLarge)
        if (!isPerson || MonsterTag.UNDEAD in target.tags) return listOf(SpellEffect.Resisted(target.id, ResistReason.NOT_A_PERSON))
        if (target.race != null && dice.percent(target.race.sleepCharmResistance)) {
            return listOf(SpellEffect.Resisted(target.id, ResistReason.RACIAL_RESISTANCE))
        }
        val save = SavingThrows.roll(target, SaveCategory.SPELL, dice, mental = true)
        if (save.success) return listOf(SpellEffect.Resisted(target.id, ResistReason.SAVED, save))
        return listOf(apply(target, ConditionType.CHARMED, null))
    }

    /** Light cast at a creature's eyes blinds it unless it saves. */
    private fun light(
        target: Combatant,
        level: Int,
        dice: Dice,
    ): List<SpellEffect> {
        val save = SavingThrows.roll(target, SaveCategory.SPELL, dice)
        if (save.success) return listOf(SpellEffect.Resisted(target.id, ResistReason.SAVED, save))
        return listOf(apply(target, ConditionType.BLINDED, 10 + level))
    }

    private fun cure(
        target: Combatant,
        dice: Dice,
    ): List<SpellEffect> {
        if (!target.isAlive || MonsterTag.UNDEAD in target.tags) return listOf(SpellEffect.Resisted(target.id, ResistReason.IMMUNE))
        val healed = Combat.heal(target, d("1d8").roll(dice))
        return listOf(SpellEffect.Healed(target.id, healed, target.hp, target.health))
    }

    /** Command: the target loses its next action. Undead are immune, 6+ HD creatures may save. */
    private fun command(
        target: Combatant,
        dice: Dice,
    ): List<SpellEffect> {
        if (MonsterTag.UNDEAD in target.tags) return listOf(SpellEffect.Resisted(target.id, ResistReason.IMMUNE))
        if (target.hitDice >= 6 || (target.abilities?.int ?: 0) >= 13) {
            val save = SavingThrows.roll(target, SaveCategory.SPELL, dice, mental = true)
            if (save.success) return listOf(SpellEffect.Resisted(target.id, ResistReason.SAVED, save))
        }
        return listOf(apply(target, ConditionType.COMMANDED, null))
    }
}
