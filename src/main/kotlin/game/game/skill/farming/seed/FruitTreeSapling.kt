package game.skill.farming.seed

import io.luna.game.model.item.Item

/**
 * Data for the saplings planted in fruit tree patches, and the fruit trees they grow into. [seedId] is the sapling's
 * item id, not the fruit tree seed, which is first grown into a sapling in a plant pot.
 *
 * Every fruit tree is drawn from a block of values starting at its [configIndexOffset], see
 * [game.skill.farming.patch.FruitTreePatch]. [plantXp] is given when the sapling is planted, [harvestXp] for every fruit
 * picked, and [checkHealthXp] when the tree's health is checked. The wiki only gives the total of all three for six
 * fruit, which every tree's values add up to.
 *
 * @author hydrozoa
 */
enum class FruitTreeSapling(override val seedId: Int,
                            override val level: Int,
                            val configIndexOffset: Int,
                            override val produce: Item,
                            override val plantXp: Double,
                            override val harvestXp: Double,
                            override val checkHealthXp: Double) : Crop {

    APPLE(5496, 27, 8, Item(1955), 22.0, 8.5, 1199.5),
    BANANA(5497, 33, 35, Item(1963), 28.0, 10.5, 1750.5),
    ORANGE(5498, 39, 72, Item(2108), 35.5, 13.5, 2470.2),
    CURRY(5499, 42, 99, Item(5970), 40.0, 15.0, 2906.9),
    PINEAPPLE(5500, 51, 136, Item(2114), 57.0, 21.5, 4605.7),
    PAPAYA(5501, 57, 163, Item(5972), 72.0, 27.0, 6146.4),
    PALM(5502, 68, 200, Item(5974), 110.5, 41.5, 10150.1);

    /**
     * Every fruit tree is fully grown at stage 7: the seedling, five growing stages, then fully grown.
     */
    override val stages = 7

    /**
     * The chance out of 128 of a fruit tree becoming diseased in a growth cycle (wiki).
     */
    override val baseDiseaseChance = 18

    companion object {
        private val saplings = values().associateBy(FruitTreeSapling::seedId)
        fun lookup(saplingId: Int): FruitTreeSapling? = saplings[saplingId]
    }
}
