package game.skill.fletching.darts

import com.google.common.collect.ImmutableList

/**
 * An enum representing a dart made by adding a feather to a dart tip.
 *
 * @property level The Fletching level required.
 * @property exp The experience gained per dart.
 * @property tip The dart tip identifier.
 * @property id The dart identifier.
 */
enum class Dart(val level: Int,
                val exp: Double,
                val tip: Int,
                val id: Int) {

    BRONZE_DART(level = 1,
                exp = 1.8,
                tip = 819,
                id = 806),
    IRON_DART(level = 22,
              exp = 3.8,
              tip = 820,
              id = 807),
    STEEL_DART(level = 37,
               exp = 7.5,
               tip = 821,
               id = 808),
    MITHRIL_DART(level = 52,
                 exp = 11.2,
                 tip = 822,
                 id = 809),
    ADAMANT_DART(level = 67,
                 exp = 15.0,
                 tip = 823,
                 id = 810),
    RUNE_DART(level = 81,
              exp = 18.8,
              tip = 824,
              id = 811);

    companion object {

        /**
         * The feather identifier.
         */
        const val FEATHER = 314

        /**
         * The amount of darts made in one batch.
         */
        const val BATCH_AMOUNT = 10

        /**
         * An immutable copy of [entries].
         */
        val VALUES: ImmutableList<Dart> = ImmutableList.copyOf(entries)
    }
}
