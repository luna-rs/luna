package game.content.barrows

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Checks reward quantity boundaries, unique sampling without replacement, and empty common rolls.
 *
 * @author lare96
 */
class BarrowsRewardsTest {
    private val first = object : Random() { override fun nextBits(bitCount: Int) = 0 }

    @Test
    fun uniqueRollsNeverRepeatAndOnlyUseKilledBrothers() {
        for (flags in 1..63) {
            val parameters = BarrowsRewardData.parameters(flags)
            val rewards = BarrowsRewards.roll(flags, first)
            assertEquals(parameters.rolls, rewards.size)
            assertEquals(rewards.size, rewards.map { it.id }.distinct().size)
            assertTrue(rewards.all { it.id in parameters.uniquePool && it.amount == 1 })
        }
        assertTrue(BarrowsRewards.roll(0, first).isEmpty())
    }

    @Test
    fun correctedQuantityBoundsArePositiveAtEveryPotentialAndReachKnownCeilings() {
        val minima = listOf(2, 253, 112, 70, 37, 35, 1, 1)
        for ((band, minimum) in BarrowsRewardData.commonBands.zip(minima)) {
            for (potential in band.rolls.first..1012) {
                val range = BarrowsRewards.quantityRange(band, potential)
                assertEquals(minimum, range.first)
                assertTrue(range.last in minimum..band.quantityCeiling)
                if (potential >= band.rolls.last) assertEquals(band.quantityCeiling, range.last)
            }
        }
    }

    @Test
    fun repeatedCommonRewardsMergeAndMaximumPotentialCanRollDragonMed() {
        val last = object : Random() {
            override fun nextBits(bitCount: Int) = error("Unexpected bit roll")
            override fun nextInt(until: Int) = until - 1
            override fun nextInt(from: Int, until: Int) = until - 1
        }
        val rewards = BarrowsRewards.roll(BarrowsVarbit.MONSTER_SCORE.write(63, 1000), last)
        assertEquals(1, rewards.size)
        assertEquals(1149, rewards.single().id)
        assertEquals(7, rewards.single().amount)
    }

    @Test
    fun ordinaryRunsCannotRollBandsAboveTheirPotential() {
        val random = Random(688)
        val parent = BarrowsVarbit.MONSTER_SCORE.write(63, 656)
        repeat(2000) {
            assertTrue(BarrowsRewards.roll(parent, random).none { it.id in listOf(565, 4740, 985, 987, 1149) })
        }
    }
}
