package game.skill.fletching.bolts

import com.google.common.collect.ImmutableList
import io.luna.game.model.mob.block.Animation

/**
 * A bolt tip shaped with a chisel. One use shapes one gem or pearl into [amount] tips.
 *
 * @param source The cut gem or pearl the chisel is used on.
 * @param tip The bolt tips made.
 * @param level The Fletching level needed.
 * @param exp The Fletching experience for one use.
 * @param amount The tips made from one [source].
 * @param animation The animation played, if any.
 * @author TheLining
 */
enum class BoltTip(val source: Int,
                   val tip: Int,
                   val level: Int,
                   val exp: Double,
                   val amount: Int = 2,
                   val animation: Animation? = null) {

    OPAL(source = 1609,
         tip = 45,
         level = 17,
         exp = 3.2,
         animation = Animation(890)),
    OYSTER_PEARL(source = 411,
                 tip = 46,
                 level = 34,
                 exp = 6.3),
    OYSTER_PEARLS(source = 413,
                  tip = 46,
                  level = 34,
                  exp = 6.3);

    companion object {

        /**
         * The chisel identifier.
         */
        const val CHISEL = 1755

        /**
         * An immutable copy of [values].
         */
        val VALUES: ImmutableList<BoltTip> = ImmutableList.copyOf(entries)
    }
}
