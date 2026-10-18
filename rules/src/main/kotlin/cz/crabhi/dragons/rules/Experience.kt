package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
data class LevelUp(
    val characterId: String,
    val newLevel: Int,
    val hpGained: Int,
)

@Serializable
data class XpAward(
    val total: Int,
    val perCharacter: Int,
    val recipients: List<String>,
    val levelUps: List<LevelUp>,
)

object Experience {
    /** Splits [total] XP evenly among living party members and applies level ups (max level 3). */
    fun award(
        party: List<Combatant>,
        total: Int,
        dice: Dice,
    ): XpAward {
        val recipients = party.filter { it.isAlive }
        if (recipients.isEmpty() || total <= 0) return XpAward(total, 0, emptyList(), emptyList())
        val share = total / recipients.size
        val levelUps = mutableListOf<LevelUp>()
        for (c in recipients) {
            val cls = c.characterClass ?: continue
            c.xp += share
            while (c.level < CharacterClass.MAX_LEVEL && c.xp >= cls.xpForLevel(c.level + 1)) {
                levelUps += levelUp(c, dice)
            }
        }
        return XpAward(total, share, recipients.map { it.id }, levelUps)
    }

    fun levelUp(
        c: Combatant,
        dice: Dice,
    ): LevelUp {
        val cls = requireNotNull(c.characterClass)
        val abilities = requireNotNull(c.abilities)
        c.level += 1
        val gained = maxOf(1, dice.roll(cls.hitDie) + abilities.conHp(cls.isWarrior))
        c.maxHp += gained
        c.hp += gained
        if (cls.casterType != null) {
            val memorizedBefore = c.memorized.size
            val preparedBefore = c.prepared.size
            Spellcasting.prepareDefault(c, Pregens.spellPriority(c))
            // Newly gained slots are available right away; already used slots stay used until rest.
            val used = preparedBefore - memorizedBefore
            repeat(used.coerceAtMost(c.memorized.size)) { c.memorized.removeAt(0) }
        }
        return LevelUp(c.id, c.level, gained)
    }
}
