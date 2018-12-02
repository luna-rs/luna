package world.player.items.fillWater

import io.luna.game.model.item.Item

/**
 * An enum representing items that can be filled with water.
 */
enum class Fill(val empty: Int, val filled: Int) {
    BOWL(empty = 1923,
         filled = 1921),
    VIAL(empty = 229,
         filled = 227),
    BUCKET(empty = 1925,
           filled = 1929),
    CUP(empty = 1980,
        filled = 4458),
    JUG(empty = 1935,
        filled = 1937);

    companion object {

        /**
         * Mappings of [Fill.emptyItem] to [Fill].
         */
        val EMPTY_TO_FILL = Fill.values().map { it.empty to it }.toMap()
    }

    /**
     * The empty item.
     */
    val emptyItem = arrayOf(Item(empty))

    /**
     * The filled item.
     */
    val filledItem = arrayOf(Item(filled))
}

