package cz.crabhi.dragons.rules

import cz.crabhi.dragons.rules.MonsterTag.ANIMAL
import cz.crabhi.dragons.rules.MonsterTag.EVIL
import cz.crabhi.dragons.rules.MonsterTag.HUMANOID
import cz.crabhi.dragons.rules.MonsterTag.LARGE
import cz.crabhi.dragons.rules.MonsterTag.UNDEAD

/** A Monstrous Manual style stat block. */
data class MonsterTemplate(
    val id: String,
    /** Hit dice used for saves, sleep and THAC0. Creatures below 1 HD use 0. */
    val hitDice: Int,
    /** Hit point dice, e.g. `2d8+2` for HD 2+2 or `1d4` for HD 1/2. */
    val hp: DiceExpr,
    val ac: Int,
    val thac0: Int,
    val attacks: List<Attack>,
    val morale: Int,
    val xp: Int,
    val tags: Set<MonsterTag> = emptySet(),
)

object Bestiary {
    private fun atk(
        id: String,
        dice: String,
        effect: AttackEffect? = null,
    ) = Attack(id, d(dice), effect = effect)

    private val poison = { dice: String -> AttackEffect(AttackEffectType.POISON, d(dice)) }
    private val paralysis = AttackEffect(AttackEffectType.PARALYSIS, d("1d6+2"))

    val all: List<MonsterTemplate> =
        listOf(
            MonsterTemplate("kobold", 0, d("1d4"), 7, 20, listOf(atk("weapon", "1d6")), 6, 7, setOf(HUMANOID, EVIL)),
            MonsterTemplate("goblin", 0, d("1d8-1"), 6, 20, listOf(atk("weapon", "1d6")), 10, 15, setOf(HUMANOID, EVIL)),
            MonsterTemplate("giant_rat", 0, d("1d4"), 7, 20, listOf(atk("bite", "1d3")), 5, 15, setOf(ANIMAL)),
            MonsterTemplate("giant_centipede", 0, d("1d3"), 9, 20, listOf(atk("bite", "1", poison("1d4"))), 5, 35, setOf(ANIMAL)),
            MonsterTemplate("bandit", 1, d("1d6+1"), 7, 20, listOf(atk("short_sword", "1d6")), 9, 15, setOf(HUMANOID, EVIL)),
            MonsterTemplate("orc", 1, d("1d8"), 6, 19, listOf(atk("weapon", "1d8")), 11, 15, setOf(HUMANOID, EVIL)),
            MonsterTemplate("skeleton", 1, d("1d8"), 7, 19, listOf(atk("weapon", "1d6")), 20, 65, setOf(UNDEAD, EVIL)),
            MonsterTemplate("hobgoblin", 1, d("1d8+1"), 5, 19, listOf(atk("weapon", "1d8")), 11, 35, setOf(HUMANOID, EVIL)),
            MonsterTemplate("stirge", 1, d("1d8+1"), 8, 17, listOf(atk("proboscis", "1d3")), 8, 175, setOf(ANIMAL)),
            MonsterTemplate("fire_beetle", 1, d("1d8+2"), 4, 19, listOf(atk("bite", "2d4")), 10, 35, setOf(ANIMAL)),
            MonsterTemplate("large_spider", 1, d("1d8+1"), 8, 19, listOf(atk("bite", "1", poison("2d4"))), 7, 175, setOf(ANIMAL)),
            MonsterTemplate("zombie", 2, d("2d8"), 8, 19, listOf(atk("fist", "1d8")), 20, 65, setOf(UNDEAD, EVIL)),
            MonsterTemplate("gnoll", 2, d("2d8"), 5, 19, listOf(atk("weapon", "2d4")), 11, 35, setOf(HUMANOID, EVIL, LARGE)),
            MonsterTemplate(
                "ghoul",
                2,
                d("2d8"),
                6,
                19,
                listOf(atk("claw", "1d3", paralysis), atk("bite", "1d6")),
                20,
                175,
                setOf(UNDEAD, EVIL),
            ),
            MonsterTemplate(
                "troglodyte",
                2,
                d("2d8"),
                5,
                19,
                listOf(atk("claw", "1d2"), atk("bite", "1d4+1")),
                11,
                120,
                setOf(HUMANOID, EVIL),
            ),
            MonsterTemplate("lizard_man", 2, d("2d8+1"), 5, 19, listOf(atk("claw", "1d2"), atk("bite", "1d6")), 11, 65, setOf(HUMANOID)),
            MonsterTemplate("wolf", 2, d("2d8+2"), 7, 19, listOf(atk("bite", "2d4")), 10, 120, setOf(ANIMAL)),
            MonsterTemplate("bugbear", 3, d("3d8+1"), 5, 17, listOf(atk("weapon", "2d4")), 11, 120, setOf(HUMANOID, EVIL, LARGE)),
            MonsterTemplate(
                "black_bear",
                3,
                d("3d8+3"),
                7,
                17,
                listOf(atk("claw", "1d3"), atk("bite", "1d6")),
                9,
                175,
                setOf(ANIMAL, LARGE),
            ),
            MonsterTemplate("wild_boar", 3, d("3d8+3"), 7, 17, listOf(atk("tusk", "3d4")), 12, 175, setOf(ANIMAL)),
            MonsterTemplate("ogre", 4, d("4d8+1"), 5, 17, listOf(atk("club", "2d6")), 12, 270, setOf(HUMANOID, EVIL, LARGE)),
        )

    private val byId = all.associateBy { it.id }

    val ids: Set<String> get() = byId.keys

    fun get(id: String): MonsterTemplate? = byId[id.lowercase()]

    fun spawn(
        templateId: String,
        instanceId: String,
        dice: Dice,
    ): Combatant {
        val t = get(templateId) ?: throw IllegalArgumentException("Unknown monster: $templateId")
        val hp = maxOf(1, t.hp.roll(dice))
        return Combatant(
            id = instanceId,
            name = t.id,
            side = Side.MONSTERS,
            level = t.hitDice,
            hp = hp,
            maxHp = hp,
            monsterId = t.id,
            naturalAc = t.ac,
            monsterThac0 = t.thac0,
            monsterAttacks = t.attacks,
            hitDice = t.hitDice,
            morale = t.morale,
            xpValue = t.xp,
            tags = t.tags,
        )
    }
}
