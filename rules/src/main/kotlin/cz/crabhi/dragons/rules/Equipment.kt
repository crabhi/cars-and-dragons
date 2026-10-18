package cz.crabhi.dragons.rules

import kotlinx.serialization.Serializable

@Serializable
enum class Weapon(
    private val damageSmallMedium: String,
    private val damageLarge: String,
    val missile: Boolean = false,
    val blunt: Boolean = false,
) {
    LONG_SWORD("1d8", "1d12"),
    SHORT_SWORD("1d6", "1d8"),
    BATTLE_AXE("1d8", "1d8"),
    SPEAR("1d6", "1d8"),
    MACE("1d6+1", "1d6", blunt = true),
    WARHAMMER("1d4+1", "1d4", blunt = true),
    QUARTERSTAFF("1d6", "1d6", blunt = true),
    DAGGER("1d4", "1d3"),
    SLING("1d4+1", "1d6+1", missile = true, blunt = true),
    SHORT_BOW("1d6", "1d6", missile = true),
    LIGHT_CROSSBOW("1d4", "1d4", missile = true),
    ;

    val id: String get() = name.lowercase()

    fun damage(targetLarge: Boolean): DiceExpr = d(if (targetLarge) damageLarge else damageSmallMedium)
}

@Serializable
enum class Armor(
    val baseAc: Int,
) {
    NONE(10),
    LEATHER(8),
    STUDDED_LEATHER(7),
    CHAIN_MAIL(5),
    BANDED_MAIL(4),
    PLATE_MAIL(3),
    ;

    val id: String get() = name.lowercase()
}

@Serializable
enum class Item(
    val magical: Boolean,
) {
    HEALING_POTION(magical = true),
    TORCH(magical = false),
    ROPE(magical = false),
    ;

    val id: String get() = name.lowercase()

    companion object {
        fun byId(id: String): Item? = entries.firstOrNull { it.id == id.lowercase() }

        val HEALING_POTION_DICE = d("2d4+2")
    }
}
