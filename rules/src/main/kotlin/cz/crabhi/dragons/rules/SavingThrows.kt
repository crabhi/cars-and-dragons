package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
enum class SaveCategory {
    PARALYZATION_POISON_DEATH,
    ROD_STAFF_WAND,
    PETRIFICATION_POLYMORPH,
    BREATH_WEAPON,
    SPELL,
}

/** Saving throw targets: roll d20 + bonuses, success if the total is at least the target number. */
@Serializable
data class SaveTable(
    val paralyzation: Int,
    val rod: Int,
    val petrification: Int,
    val breath: Int,
    val spell: Int,
) {
    fun target(category: SaveCategory): Int =
        when (category) {
            SaveCategory.PARALYZATION_POISON_DEATH -> paralyzation
            SaveCategory.ROD_STAFF_WAND -> rod
            SaveCategory.PETRIFICATION_POLYMORPH -> petrification
            SaveCategory.BREATH_WEAPON -> breath
            SaveCategory.SPELL -> spell
        }

    companion object {
        // PHB table 60, the rows for levels 1..3.
        private val PRIEST_1_3 = SaveTable(10, 14, 13, 16, 15)
        private val ROGUE_1_4 = SaveTable(13, 14, 12, 16, 15)
        private val WARRIOR_0 = SaveTable(16, 18, 17, 20, 19)
        private val WARRIOR_1_2 = SaveTable(14, 16, 15, 17, 17)
        private val WARRIOR_3_4 = SaveTable(13, 15, 14, 16, 16)
        private val WARRIOR_5_6 = SaveTable(11, 13, 12, 13, 14)
        private val WIZARD_1_5 = SaveTable(14, 11, 13, 15, 12)

        fun forGroup(
            group: ClassGroup,
            level: Int,
        ): SaveTable =
            when (group) {
                ClassGroup.PRIEST -> PRIEST_1_3
                ClassGroup.ROGUE -> ROGUE_1_4
                ClassGroup.WIZARD -> WIZARD_1_5
                ClassGroup.WARRIOR -> warrior(level)
            }

        /** Monsters save as warriors of a level equal to their hit dice. */
        fun warrior(level: Int): SaveTable =
            when {
                level < 1 -> WARRIOR_0
                level <= 2 -> WARRIOR_1_2
                level <= 4 -> WARRIOR_3_4
                else -> WARRIOR_5_6
            }
    }
}

@Serializable
data class SaveResult(
    val category: SaveCategory,
    val roll: Int,
    val bonus: Int,
    val target: Int,
) {
    val success: Boolean get() = roll + bonus >= target
}

object SavingThrows {
    /**
     * Rolls a saving throw for [who]. [mental] marks mind-affecting magic (charm, command, sleep),
     * which adds the wisdom magical defense adjustment.
     */
    fun roll(
        who: Combatant,
        category: SaveCategory,
        dice: Dice,
        mental: Boolean = false,
        extraBonus: Int = 0,
    ): SaveResult = SaveResult(category, dice.d20(), bonus(who, category, mental) + extraBonus, who.saves().target(category))

    fun bonus(
        who: Combatant,
        category: SaveCategory,
        mental: Boolean,
    ): Int {
        var bonus = 0
        val abilities = who.abilities
        if (abilities != null) {
            if (mental) bonus += abilities.wisMagicDefense
            val stoutCategory =
                category == SaveCategory.ROD_STAFF_WAND ||
                    category == SaveCategory.SPELL ||
                    category == SaveCategory.PARALYZATION_POISON_DEATH
            if (who.race?.stout == true && stoutCategory) bonus += abilities.stoutSaveBonus
        }
        if (who.has(ConditionType.PROTECTED_FROM_EVIL)) bonus += 2
        return bonus
    }
}
