package game.skill.farming.seed

/**
 * Data for growing tree and fruit tree seeds into saplings in plant pots. A [seed] planted in a filled plant pot becomes
 * a [seedling], watering it makes it a [wateredSeedling], and that grows into the [sapling] that is planted in a patch.
 * Planting the seed needs the Farming level of the tree, taken from [tree].
 *
 * Spirit and calquat seedlings are in the cache too, but are left out until there are patches to plant their saplings
 * in.
 *
 * @author hydrozoa
 */
enum class TreeSeedling(val seed: Int, val seedling: Int, val wateredSeedling: Int, val tree: Crop) {
    OAK(5312, 5358, 5364, TreeSapling.OAK),
    WILLOW(5313, 5359, 5365, TreeSapling.WILLOW),
    MAPLE(5314, 5360, 5366, TreeSapling.MAPLE),
    YEW(5315, 5361, 5367, TreeSapling.YEW),
    MAGIC(5316, 5362, 5368, TreeSapling.MAGIC),
    APPLE(5283, 5480, 5488, FruitTreeSapling.APPLE),
    BANANA(5284, 5481, 5489, FruitTreeSapling.BANANA),
    ORANGE(5285, 5482, 5490, FruitTreeSapling.ORANGE),
    CURRY(5286, 5483, 5491, FruitTreeSapling.CURRY),
    PINEAPPLE(5287, 5484, 5492, FruitTreeSapling.PINEAPPLE),
    PAPAYA(5288, 5485, 5493, FruitTreeSapling.PAPAYA),
    PALM(5289, 5486, 5494, FruitTreeSapling.PALM);

    /**
     * The sapling the watered seedling grows into.
     */
    val sapling = tree.seedId

    /**
     * The Farming level needed to plant the seed.
     */
    val level = tree.level

    companion object {
        private val seeds = values().associateBy(TreeSeedling::seed)
        fun lookup(seedId: Int): TreeSeedling? = seeds[seedId]
    }
}
