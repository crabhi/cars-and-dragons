package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
enum class Side { PARTY, MONSTERS }

@Serializable
enum class Health { OK, UNCONSCIOUS, DEAD }

@Serializable
enum class ConditionType {
    ASLEEP,
    CHARMED,
    BLESSED,
    SHIELDED,
    PROTECTED_FROM_EVIL,
    BLINDED,
    PARALYZED,

    /** Obeys a one-word command and loses its next action. */
    COMMANDED,
}

@Serializable
data class Condition(
    val type: ConditionType,
    /** Remaining rounds; decremented at the end of each combat round. Null = lasts until removed. */
    val roundsLeft: Int? = null,
)

@Serializable
enum class AttackEffectType { POISON, PARALYSIS }

@Serializable
data class AttackEffect(
    val type: AttackEffectType,
    /** Extra damage on a failed save (poison) or duration in rounds (paralysis). */
    val dice: DiceExpr,
    val saveBonus: Int = 0,
)

@Serializable
data class Attack(
    val id: String,
    val damage: DiceExpr,
    val missile: Boolean = false,
    val effect: AttackEffect? = null,
    /** Weapon used, for player characters. Damage then depends on target size and strength. */
    val weapon: Weapon? = null,
)

@Serializable
enum class MonsterTag { UNDEAD, HUMANOID, ANIMAL, EVIL, LARGE }

/**
 * Anything that fights: a player character or a monster. Mutable on purpose; the engine owns the
 * single game state and resolves actions against it.
 */
@Serializable
class Combatant(
    val id: String,
    val name: String,
    val side: Side,
    var level: Int = 1,
    var hp: Int,
    var maxHp: Int,
    // --- player characters ---
    val characterClass: CharacterClass? = null,
    val race: Race? = null,
    val abilities: Abilities? = null,
    val armor: Armor = Armor.NONE,
    val shield: Boolean = false,
    val weapons: List<Weapon> = emptyList(),
    var xp: Int = 0,
    val spellbook: List<Spell> = emptyList(),
    var prepared: List<Spell> = emptyList(),
    var memorized: MutableList<Spell> = mutableListOf(),
    val inventory: MutableMap<Item, Int> = mutableMapOf(),
    val proficiencies: List<Proficiency> = emptyList(),
    // --- monsters ---
    val monsterId: String? = null,
    val naturalAc: Int? = null,
    val monsterThac0: Int? = null,
    val monsterAttacks: List<Attack> = emptyList(),
    val hitDice: Int = level,
    val morale: Int = 20,
    val xpValue: Int = 0,
    val tags: Set<MonsterTag> = emptySet(),
    // --- state ---
    val conditions: MutableList<Condition> = mutableListOf(),
    var fled: Boolean = false,
) {
    val isPc: Boolean get() = characterClass != null

    val isLarge: Boolean get() = MonsterTag.LARGE in tags

    val health: Health
        get() =
            when {
                isPc && hp <= DEATH_HP -> Health.DEAD
                !isPc && hp <= 0 -> Health.DEAD
                hp <= 0 -> Health.UNCONSCIOUS
                else -> Health.OK
            }

    val isAlive: Boolean get() = health != Health.DEAD

    /** Can take actions this round. */
    val canAct: Boolean
        get() =
            health == Health.OK &&
                !fled &&
                !has(ConditionType.ASLEEP) &&
                !has(ConditionType.PARALYZED) &&
                !has(ConditionType.CHARMED)

    /** Still a threat in combat (for monsters: not dead, fled, asleep, paralyzed or charmed). */
    val isActiveThreat: Boolean get() = canAct || (health == Health.OK && !fled && has(ConditionType.COMMANDED))

    /** Defenseless targets are hit automatically. */
    val isHelpless: Boolean
        get() = health == Health.UNCONSCIOUS || has(ConditionType.ASLEEP) || has(ConditionType.PARALYZED)

    fun has(type: ConditionType): Boolean = conditions.any { it.type == type }

    fun addCondition(condition: Condition) {
        conditions.removeAll { it.type == condition.type }
        conditions.add(condition)
    }

    fun removeCondition(type: ConditionType) {
        conditions.removeAll { it.type == type }
    }

    fun armorClass(vsMissile: Boolean = false): Int {
        var ac = naturalAc ?: armor.baseAc
        if (shield) ac -= 1
        abilities?.let { ac += it.dexDefense }
        if (has(ConditionType.SHIELDED)) ac = minOf(ac, if (vsMissile) 2 else 4)
        return ac
    }

    fun thac0(): Int = monsterThac0 ?: characterClass?.thac0(level) ?: 20

    fun saves(): SaveTable = characterClass?.saves(level) ?: SaveTable.warrior(hitDice)

    fun attacks(): List<Attack> =
        if (isPc) {
            weapons.map { Attack(it.id, it.damage(false), missile = it.missile, weapon = it) }
                .ifEmpty { listOf(Attack("unarmed", d("1d2"))) }
        } else {
            monsterAttacks
        }

    fun canCast(spell: Spell): Boolean = canAct && spell in memorized

    companion object {
        const val DEATH_HP = -10
    }
}
