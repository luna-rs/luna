package engine.obj

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varbit

/**
 * The trapdoor into the H.A.M. Hideout, west of Lumbridge. Its lock has to be picked, and it only opens for whoever
 * picked it: the map has [ID] on [POSITION], which shows each player closed (5490) or open (5491) by [VARBIT].
 *
 * trapdoors.kts handles the trapdoor's options, and [api.bot.webwalk.walk.crossing.HamTrapdoorCrossing] takes bots
 * through it.
 *
 * @author Hydrozoa
 */
object HamTrapdoor {

    /**
     * The trapdoor.
     */
    const val ID = 5492

    /**
     * The varbit that shows [ID] open.
     */
    const val VARBIT = 235

    /**
     * Where the trapdoor is.
     */
    val POSITION = Position(3166, 3252)

    /**
     * The tile the trapdoor is used from, which the ladder back up lands on too.
     */
    val USE_FROM = Position(3165, 3252)

    /**
     * Where the trapdoor leads: the tile the ladder back up is climbed from.
     */
    val HIDEOUT = Position(3148, 9653)

    /**
     * A lockpick, which makes picking the lock more likely to work.
     */
    const val LOCKPICK = 1523

    /**
     * The chance of picking the lock by hand, 4% at level 1 and 98% at level 99.
     */
    val CHANCE = 10 to 250

    /**
     * The chance of picking the lock with a [LOCKPICK], 24% at level 1 and 99% at level 99.
     */
    val LOCKPICK_CHANCE = 61 to 253

    /**
     * The Thieving experience for picking the lock.
     */
    const val XP = 3.8

    /**
     * The Thieving experience a failed attempt at the lock sometimes gives.
     */
    const val FAIL_XP = 1.0

    /**
     * The chance of a failed attempt at the lock giving [FAIL_XP].
     */
    val FAIL_XP_CHANCE = 1 of 3

    /**
     * Whether the player has the trapdoor open.
     */
    private var Player.hamTrapdoorOpen by Attr.boolean()

    /**
     * How many times the player has opened the trapdoor, so a timer can tell if it has been opened again since.
     */
    private var Player.hamTrapdoorOpenings by Attr.int()

    /**
     * Determines if [plr] has the trapdoor open.
     */
    fun isOpen(plr: Player): Boolean = plr.hamTrapdoorOpen

    /**
     * Shows [plr] the trapdoor open or closed. Like a door, an opened one closes again by itself after
     * [DoorType.DEFAULT_DURATION] ticks, unless it has been closed or opened again since.
     */
    fun show(plr: Player, open: Boolean) {
        plr.hamTrapdoorOpen = open
        plr.sendVarbit(Varbit(VARBIT, if (open) 1 else 0))
        if (open) {
            val opening = ++plr.hamTrapdoorOpenings
            world.scheduleOnce(DoorType.DEFAULT_DURATION) {
                if (plr.state == EntityState.ACTIVE && plr.hamTrapdoorOpen && plr.hamTrapdoorOpenings == opening) {
                    show(plr, false)
                }
            }
        }
    }
}
