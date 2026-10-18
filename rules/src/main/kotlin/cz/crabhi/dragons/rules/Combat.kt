package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
data class EffectResult(
    val type: AttackEffectType,
    val save: SaveResult?,
    /** Extra damage dealt (poison) or rounds of paralysis applied. Zero when resisted. */
    val amount: Int,
    val immune: Boolean = false,
)

@Serializable
data class AttackResult(
    val attackerId: String,
    val targetId: String,
    val attackId: String,
    val roll: Int,
    val bonus: Int,
    val needed: Int,
    val hit: Boolean,
    val autoHit: Boolean,
    val damage: Int,
    val targetHpAfter: Int,
    val healthBefore: Health,
    val healthAfter: Health,
    val effect: EffectResult? = null,
)

@Serializable
data class InitiativeResult(
    val partyRoll: Int,
    val monsterRoll: Int,
    val rerolls: Int,
) {
    /** Lower d10 acts first. */
    val first: Side get() = if (partyRoll < monsterRoll) Side.PARTY else Side.MONSTERS
}

object Combat {
    /** Attack roll modifiers other than THAC0 and AC. */
    fun toHitBonus(
        attacker: Combatant,
        attack: Attack,
        target: Combatant,
    ): Int {
        var bonus = 0
        val abilities = attacker.abilities
        if (abilities != null) {
            bonus += if (attack.missile) abilities.dexMissile else abilities.strengthHit
        }
        if (attacker.race == Race.HALFLING && attack.weapon == Weapon.SLING) bonus += 1
        if (attacker.has(ConditionType.BLESSED)) bonus += 1
        if (attacker.has(ConditionType.BLINDED)) bonus -= 4
        if (target.has(ConditionType.PROTECTED_FROM_EVIL) && MonsterTag.EVIL in attacker.tags) bonus -= 2
        return bonus
    }

    fun damageDice(
        attacker: Combatant,
        attack: Attack,
        target: Combatant,
    ): DiceExpr {
        val weapon = attack.weapon ?: return attack.damage
        val base = weapon.damage(target.isLarge)
        val strength = if (!weapon.missile || weapon == Weapon.SLING) attacker.abilities?.strengthDamage ?: 0 else 0
        return base.copy(bonus = base.bonus + strength)
    }

    /**
     * Resolves one attack. Hit if `d20 + bonus >= THAC0 - target AC`. A natural 20 always hits and a
     * natural 1 always misses. Helpless targets are hit automatically.
     */
    fun attack(
        attacker: Combatant,
        target: Combatant,
        attack: Attack,
        dice: Dice,
    ): AttackResult {
        val healthBefore = target.health
        val needed = attacker.thac0() - target.armorClass(attack.missile)
        val bonus = toHitBonus(attacker, attack, target)
        val autoHit = target.isHelpless
        val roll = if (autoHit) 20 else dice.d20()
        val hit = autoHit || roll == 20 || (roll != 1 && roll + bonus >= needed)
        var damage = 0
        var effect: EffectResult? = null
        if (hit) {
            damage = maxOf(1, damageDice(attacker, attack, target).roll(dice))
            applyDamage(target, damage)
            if (target.isAlive && attack.effect != null) {
                effect = applyEffect(target, attack.effect, dice)
            }
        }
        return AttackResult(
            attackerId = attacker.id,
            targetId = target.id,
            attackId = attack.id,
            roll = roll,
            bonus = bonus,
            needed = needed,
            hit = hit,
            autoHit = autoHit,
            damage = damage + (effect?.takeIf { it.type == AttackEffectType.POISON }?.amount ?: 0),
            targetHpAfter = target.hp,
            healthBefore = healthBefore,
            healthAfter = target.health,
            effect = effect,
        )
    }

    private fun applyEffect(
        target: Combatant,
        effect: AttackEffect,
        dice: Dice,
    ): EffectResult {
        if (effect.type == AttackEffectType.PARALYSIS && (target.race == Race.ELF || MonsterTag.UNDEAD in target.tags)) {
            return EffectResult(effect.type, null, 0, immune = true)
        }
        val save = SavingThrows.roll(target, SaveCategory.PARALYZATION_POISON_DEATH, dice, extraBonus = effect.saveBonus)
        if (save.success) return EffectResult(effect.type, save, 0)
        val amount = maxOf(1, effect.dice.roll(dice))
        when (effect.type) {
            AttackEffectType.POISON -> applyDamage(target, amount)
            AttackEffectType.PARALYSIS -> target.addCondition(Condition(ConditionType.PARALYZED, amount))
        }
        return EffectResult(effect.type, save, amount)
    }

    /** Applies damage. Sleeping creatures wake up when hurt. Player characters die at -10 HP. */
    fun applyDamage(
        target: Combatant,
        amount: Int,
    ) {
        target.hp -= amount
        if (!target.isPc && target.hp < 0) target.hp = 0
        if (target.isPc && target.hp < Combatant.DEATH_HP) target.hp = Combatant.DEATH_HP
        target.removeCondition(ConditionType.ASLEEP)
        if (!target.isAlive) target.conditions.clear()
    }

    /** Heals up to max HP. The dead cannot be healed. Returns the HP actually restored. */
    fun heal(
        target: Combatant,
        amount: Int,
    ): Int {
        if (!target.isAlive) return 0
        val before = target.hp
        target.hp = minOf(target.maxHp, target.hp + amount)
        return target.hp - before
    }

    /** Group initiative: each side rolls d10, lower acts first, ties are rerolled. */
    fun rollInitiative(dice: Dice): InitiativeResult {
        var rerolls = 0
        while (true) {
            val party = dice.d10()
            val monsters = dice.d10()
            if (party != monsters) return InitiativeResult(party, monsters, rerolls)
            rerolls++
        }
    }

    /** Morale check on 2d10: the group keeps fighting if the roll is at most its morale. */
    fun moraleHolds(
        morale: Int,
        dice: Dice,
    ): Boolean = dice.roll(2, 10) <= morale

    /** Ticks down condition durations at the end of a round. Returns conditions that expired. */
    fun endRound(combatants: List<Combatant>): List<Pair<Combatant, ConditionType>> {
        val expired = mutableListOf<Pair<Combatant, ConditionType>>()
        for (c in combatants) {
            val updated =
                c.conditions.mapNotNull { cond ->
                    val left = cond.roundsLeft ?: return@mapNotNull cond
                    if (left <= 1) {
                        expired += c to cond.type
                        null
                    } else {
                        cond.copy(roundsLeft = left - 1)
                    }
                }
            c.conditions.clear()
            c.conditions.addAll(updated)
        }
        return expired
    }
}
