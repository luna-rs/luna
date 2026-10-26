package game.skill.farming.compostBin

import game.skill.farming.*
import game.skill.farming.herb.*
import game.skill.farming.tree.*

/**
 * What a compost bin is filled with, and what it rots into. A filling bin is drawn as [fillValue] plus its items, and an
 * open bin as [productValue] plus the products left, both 1 to [CompostBin.MAX_ITEMS].
 *
 * @author hydrozoa
 */
enum class CompostBinType(val fillValue: Int, val product: Int, val experience: Double) {

    /**
     * Compostable items, rotting into compost.
     */
    COMPOST(fillValue = 0, product = 6032, experience = 4.5),

    /**
     * Supercompostable items, rotting into supercompost.
     */
    SUPERCOMPOST(fillValue = 32, product = 6034, experience = 8.5),

    /**
     * Tomatoes, rotting into rotten tomatoes.
     */
    TOMATOES(fillValue = 128, product = 2518, experience = 4.5);

    /**
     * The value an open bin of this type's product is drawn from, see the class description.
     */
    val productValue = fillValue + CompostBin.MAX_ITEMS

    companion object {

        /**
         * The tomato item id.
         */
        const val TOMATO = 1982

        /**
         * The produce that can't be composted: cactus spines and nightshade (wiki).
         */
        private val NOT_COMPOSTABLE = setOf(6016, 2398)

        /**
         * The lowest Farming level a crop is grown at for its produce to be supercompostable (wiki).
         */
        private const val SUPERCOMPOST_LEVEL = 47

        /**
         * Every item that can go in a compost bin, built from the crops of every farming patch: the produce of crops grown
         * at [SUPERCOMPOST_LEVEL] or higher, toadflax and tree roots are supercompostable, tomatoes rot into rotten
         * tomatoes, and weeds and the produce of every other crop are compostable. Logs, cactus spines and nightshade are
         * not compostable.
         */
        private val items: Map<Int, CompostBinType> by lazy {
            val map = HashMap<Int, CompostBinType>()
            map[Farming.WEEDS] = COMPOST
            Farming.LOCATIONS.flatMap { it.createPatch().crops }.distinct().forEach { crop ->
                when {
                    crop is TreeSapling -> map[crop.roots] = SUPERCOMPOST
                    crop.produce.id in NOT_COMPOSTABLE -> Unit
                    crop.level >= SUPERCOMPOST_LEVEL || crop == HerbSeed.TOADFLAX -> map[crop.produce.id] = SUPERCOMPOST
                    else -> map[crop.produce.id] = COMPOST
                }
            }
            map[TOMATO] = TOMATOES
            map
        }

        /**
         * Returns what [itemId] fills a compost bin with, or `null` if it can't go in one.
         */
        fun lookup(itemId: Int): CompostBinType? = items[itemId]

        /**
         * Every item that can go in a compost bin.
         */
        fun itemIds(): Set<Int> = items.keys
    }
}
