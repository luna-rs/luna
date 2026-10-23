package game.content.barrows

import io.luna.game.model.item.Item
import kotlin.random.Random

/**
 * Rolls the reconstructed early reward table, excluding later diary bonuses and reward additions.
 * The independent quantity roll follows Lost City's draft; its exact 2005 distribution is unverified.
 *
 * @author lare96
 */
object BarrowsRewards {
    private val minimumQuantities = mapOf(995 to 2, 558 to 253, 562 to 112, 560 to 70,
                                         565 to 37, 4740 to 35, 985 to 1, 987 to 1, 1149 to 1)

    fun quantityRange(band: BarrowsRewardData.CommonBand, potential: Int): IntRange {
        require(potential >= band.rolls.first)
        val minimum = minimumQuantities.getValue(band.itemIds.first())
        val maximum = (band.quantityCeiling * minOf(potential, band.rolls.last) / band.rolls.last)
            .coerceAtLeast(minimum)
        return minimum..maximum
    }

    fun roll(killsParent: Int, random: Random = Random.Default): List<Item> {
        val parameters = BarrowsRewardData.parameters(killsParent)
        val pool = parameters.uniquePool.toMutableList()
        val rewards = linkedMapOf<Int, Int>()
        repeat(parameters.rolls) {
            if (random.nextInt(parameters.uniqueDenominator) == 0 && pool.isNotEmpty()) {
                rewards[pool.removeAt(random.nextInt(pool.size))] = 1
            } else {
                val band = BarrowsRewardData.commonBand(random.nextInt(parameters.potential + 1))
                    ?: return@repeat
                val id = band.itemIds.random(random)
                val quantity = quantityRange(band, parameters.potential).random(random)
                rewards[id] = rewards.getOrDefault(id, 0) + quantity
            }
        }
        return rewards.map { (id, quantity) -> Item(id, quantity) }
    }
}
