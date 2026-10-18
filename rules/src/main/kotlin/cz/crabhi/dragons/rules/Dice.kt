package cz.crabhi.dragons.rules

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** Source of randomness for all game math. Always injected so games and tests are reproducible. */
interface Dice {
    /** Rolls a single die with [sides] faces, returning 1..[sides]. */
    fun roll(sides: Int): Int

    fun roll(count: Int, sides: Int): Int = (1..count).sumOf { roll(sides) }

    fun d20(): Int = roll(20)

    fun d10(): Int = roll(10)

    /** Returns true with the given percentage chance (1..100). */
    fun percent(chance: Int): Boolean = roll(100) <= chance
}

/**
 * SplitMix64 generator. The whole state is a single Long, so it can be stored in a save file
 * and a resumed game continues the exact same random sequence.
 */
@Serializable
class SeededDice(
    var state: Long,
) : Dice {
    override fun roll(sides: Int): Int {
        require(sides > 0) { "Die must have at least one side" }
        return ((nextLong() ushr 1) % sides).toInt() + 1
    }

    private fun nextLong(): Long {
        state += -0x61C8864680B583EBL
        var z = state
        z = (z xor (z ushr 30)) * -0x40A7B892E31B1A47L
        z = (z xor (z ushr 27)) * -0x6B2FB644ECCEEE15L
        return z xor (z ushr 31)
    }
}

/** Dice that return scripted values in order. Values are clamped to the die size. For tests and demos. */
class FixedDice(
    vararg values: Int,
) : Dice {
    private val queue = ArrayDeque(values.toList())

    fun remaining(): Int = queue.size

    override fun roll(sides: Int): Int {
        val value = queue.removeFirstOrNull() ?: error("FixedDice ran out of scripted values")
        return value.coerceIn(1, sides)
    }
}

/** A dice expression such as `2d4+1`, `1d8` or a constant `3`. */
@Serializable(with = DiceExprSerializer::class)
data class DiceExpr(
    val count: Int,
    val sides: Int,
    val bonus: Int = 0,
) {
    fun roll(dice: Dice): Int = (if (count > 0 && sides > 0) dice.roll(count, sides) else 0) + bonus

    val min: Int get() = count + bonus
    val max: Int get() = count * sides + bonus
    val average: Double get() = count * (sides + 1) / 2.0 + bonus

    override fun toString(): String {
        val base = if (count > 0) "${count}d$sides" else ""
        return when {
            base.isEmpty() -> bonus.toString()
            bonus > 0 -> "$base+$bonus"
            bonus < 0 -> "$base$bonus"
            else -> base
        }
    }

    companion object {
        private val pattern = Regex("""^\s*(\d*)d(\d+)\s*([+-]\s*\d+)?\s*$""")

        fun parse(text: String): DiceExpr {
            text.trim().toIntOrNull()?.let { return DiceExpr(0, 0, it) }
            val match = pattern.matchEntire(text) ?: throw IllegalArgumentException("Bad dice expression: $text")
            val count = match.groupValues[1].ifEmpty { "1" }.toInt()
            val sides = match.groupValues[2].toInt()
            val bonus = match.groupValues[3].replace(" ", "").ifEmpty { "0" }.toInt()
            return DiceExpr(count, sides, bonus)
        }
    }
}

fun d(text: String): DiceExpr = DiceExpr.parse(text)

object DiceExprSerializer : KSerializer<DiceExpr> {
    override val descriptor = PrimitiveSerialDescriptor("DiceExpr", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: DiceExpr,
    ) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): DiceExpr = DiceExpr.parse(decoder.decodeString())
}
