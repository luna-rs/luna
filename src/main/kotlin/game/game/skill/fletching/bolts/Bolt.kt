package game.skill.fletching.bolts

import com.google.common.collect.ImmutableList

/**
 * A tipped bolt, made by attaching bolt tips to plain bolts.
 *
 * @param tip The bolt tips attached.
 * @param id The tipped bolts made.
 * @param level The Fletching level needed.
 * @param exp The Fletching experience for each bolt.
 * @author TheLining
 */
enum class Bolt(val tip: Int,
                val id: Int,
                val level: Int,
                val exp: Double) {

    OPAL_BOLTS(tip = 45,
               id = 879,
               level = 17,
               exp = 3.2),
    PEARL_BOLTS(tip = 46,
                id = 880,
                level = 34,
                exp = 6.3),
    BARBED_BOLTS(tip = 47,
                 id = 881,
                 level = 51,
                 exp = 9.5);

    companion object {

        /**
         * The plain bolts identifier.
         */
        const val BOLTS = 877

        /**
         * The amount to make in one set.
         */
        const val SET_AMOUNT = 10

        /**
         * An immutable copy of [values].
         */
        val VALUES: ImmutableList<Bolt> = ImmutableList.copyOf(entries)
    }
}
