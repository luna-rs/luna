package game.content.abyss

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import engine.combat.status.StatusEffectType
import engine.combat.status.hooks.SkullStatusEffect
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varbit
import kotlin.time.Duration.Companion.minutes

/**
 * Takes players into the Abyss, the Runecrafting shortcut reached through the Mage of Zamorak in the Wilderness.
 *
 * @author TheLining
 */
object Abyss {

    /**
     * The varbit that picks which obstacle each outer ring passage shows. Values 0 to 11 are the layouts, one per
     * [ENTRIES] point. The values above 11 show the obstacles giving way, for every passage at once.
     */
    const val LAYOUT_VARBIT = 625

    /**
     * The varp that tracks the Enter the Abyss miniquest. It also decides which Mage of Zamorak the client shows.
     */
    const val MINIQUEST_VARP = 492

    /**
     * The [MINIQUEST_VARP] value once Enter the Abyss is complete.
     */
    const val MINIQUEST_COMPLETE = 4

    /**
     * How long the Abyss skull lasts.
     */
    val SKULL_DURATION = 10.minutes

    /**
     * Where players arrive in the outer ring, by layout. Each point is beside the passage that its layout blocks.
     */
    val ENTRIES = listOf(Position(3041, 4808), Position(3024, 4811), Position(3015, 4825), Position(3015, 4836),
                         Position(3018, 4846), Position(3028, 4853), Position(3040, 4856), Position(3052, 4852),
                         Position(3062, 4841), Position(3064, 4831), Position(3062, 4821), Position(3054, 4812))

    /**
     * The layout the outer ring shows this player. Saved so it survives a logout in the Abyss.
     */
    var Player.abyssLayout by Attr.int().persist("abyss_layout")

    /**
     * Shows [plr] the outer ring with [value] for [LAYOUT_VARBIT].
     */
    fun showLayout(plr: Player, value: Int) {
        plr.sendVarbit(Varbit(LAYOUT_VARBIT, value))
    }

    /**
     * Sets up the outer ring for [plr], who has just arrived beside the entry point of [layout]. Their prayer is
     * drained and they are skulled.
     */
    fun arrive(plr: Player, layout: Int) {
        plr.abyssLayout = layout
        showLayout(plr, layout)
        plr.prayer.level = 0
        plr.combat.prayers.deactivateAll()
        // Entering resets the skull to its full duration, even when the old one had longer left.
        plr.status.remove(StatusEffectType.SKULLED)
        plr.status.add(SkullStatusEffect(plr, SKULL_DURATION))
    }
}
