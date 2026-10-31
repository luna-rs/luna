package game.skill.fletching.cutLog

import com.google.common.collect.ImmutableList
import game.skill.fletching.stringBow.Bow
import game.skill.fletching.stringBow.Bow.*

/**
 * An enum representing an item that can be cut into a [Bow].
 *
 * @author lare96
 */
enum class Log(val id: Int, val bows: List<Bow>, val labels: Array<String>? = null) {
    NORMAL(id = 1511,
           bows = listOf(ARROW_SHAFT, SHORTBOW, LONGBOW),
           labels = arrayOf("15 Arrow Shafts", "Short Bow", "Long Bow")),
    OAK(id = 1521,
        bows = listOf(OAK_SHORTBOW, OAK_LONGBOW),
        labels = arrayOf("Oak Short Bow", "Oak Long Bow")),
    WILLOW(id = 1519,
           bows = listOf(WILLOW_SHORTBOW, WILLOW_LONGBOW),
           labels = arrayOf("Willow Short Bow", "Willow Long Bow")),
    MAPLE(id = 1517,
          bows = listOf(MAPLE_SHORTBOW, MAPLE_LONGBOW),
          labels = arrayOf("Maple Short Bow", "Maple Long Bow")),
    YEW(id = 1515,
        bows = listOf(YEW_SHORTBOW, YEW_LONGBOW),
        labels = arrayOf("Yew Short Bow", "Yew Long Bow")),
    MAGIC(id = 1513,
          bows = listOf(MAGIC_SHORTBOW, MAGIC_LONGBOW),
          labels = arrayOf("Magic Short Bow", "Magic Long Bow"));

    companion object {

        /**
         * The knife identifier.
         */
        const val KNIFE = 946

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())
    }

    /**
     * An array of unstrung identifiers made from this log.
     */
    val unstrungIds = bows.map { it.unstrung }.toIntArray()
}