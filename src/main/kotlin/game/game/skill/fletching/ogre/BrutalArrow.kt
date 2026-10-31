package game.skill.fletching.ogre

import com.google.common.collect.ImmutableList

/**
 * An enum representing a brutal arrow, made by hammering one nail into each flighted ogre arrow.
 *
 * @author lare96
 */
enum class BrutalArrow(val nails: Int, val id: Int, val level: Int, val exp: Double) {
    BRONZE(nails = 4819, id = 4773, level = 7, exp = 1.4),
    IRON(nails = 4820, id = 4778, level = 18, exp = 2.6),
    STEEL(nails = 1539, id = 4783, level = 33, exp = 5.1),
    BLACK(nails = 4821, id = 4788, level = 38, exp = 6.4),
    MITHRIL(nails = 4822, id = 4793, level = 49, exp = 7.5),
    ADAMANT(nails = 4823, id = 4798, level = 62, exp = 10.1),
    RUNE(nails = 4824, id = 4803, level = 77, exp = 12.5);

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES: ImmutableList<BrutalArrow> = ImmutableList.copyOf(entries)
    }
}
