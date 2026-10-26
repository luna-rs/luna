package game.skill.farming.flower

import game.skill.farming.*
import game.skill.farming.allotment.*
import game.skill.farming.patch.*
import io.luna.game.model.item.Item

/**
 * Data for flower seeds, planted in flower patches. A fully grown flower with a [protection] protects the allotments of
 * its farming area, see [AllotmentSeed.flowerProtection].
 *
 * The [baseDiseaseChance] is an unchecked placeholder, the wiki gives none for flowers. The limpwurt [amount] by level
 * is also unchecked: the wiki only gives 3 to 13 roots.
 *
 * @author hydrozoa
 */
enum class FlowerSeed(override val seedId: Int,
                      override val level: Int,
                      val configIndexOffset: Int,
                      override val produce: Item,
                      override val plantXp: Double,
                      override val harvestXp: Double,
                      val protection: FlowerProtection?) : Crop {

    MARIGOLD(5096, 2, 7, Item(6010), 8.5, 47.0, FlowerProtection.MARIGOLD),
    ROSEMARY(5097, 11, 12, Item(6014), 12.0, 66.5, FlowerProtection.ROSEMARY),
    NASTURTIUM(5098, 24, 17, Item(6012), 19.5, 111.0, FlowerProtection.NASTURTIUM),
    WOAD(5099, 25, 22, Item(1793), 20.5, 115.5, null),
    LIMPWURT(5100, 26, 27, Item(225), 21.5, 120.0, null);

    /**
     * Every flower is fully grown at stage 5: the seed, three growing stages, then fully grown.
     */
    override val stages = 5

    /**
     * The chance out of 128 of a flower becoming diseased in a growth cycle, before compost and watering.
     */
    override val baseDiseaseChance = 20

    /**
     * Returns how many of [produce] picking the fully grown flower gives at Farming [level]. Woad gives 3 leaves (wiki),
     * limpwurt 3 roots plus one for every 7 levels above 26, up to 13, and every other flower 1.
     */
    fun amount(level: Int): Int = when (this) {
        WOAD -> 3
        LIMPWURT -> minOf(13, 3 + maxOf(0, level - 26) / 7)
        else -> 1
    }

    companion object {
        private val seeds = values().associateBy(FlowerSeed::seedId)
        fun lookup(seedId: Int): FlowerSeed? = seeds[seedId]
    }
}
