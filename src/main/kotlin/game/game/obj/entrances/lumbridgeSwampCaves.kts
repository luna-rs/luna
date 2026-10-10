package game.obj.entrances

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.obj.entrances.EntranceLanding.Companion.shift
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.Varbit

/**
 * The dark hole down into the Lumbridge Swamp Caves.
 */
val DARK_HOLE = 5947

/**
 * A rope.
 */
val ROPE = 954

/**
 * The varbit that shows the rope tied to the dark hole.
 */
val ROPED_VARBIT = 279

/**
 * Whether the player has tied a rope to the dark hole. It only has to be done once.
 */
var Player.swampCavesRoped by Attr.boolean().persist("swamp_caves_roped")

/**
 * Climbs [plr] down the dark hole, tying a rope to it first if they haven't yet.
 */
fun climbDown(plr: Player) {
    if (!plr.swampCavesRoped) {
        if (!plr.inventory.remove(ROPE)) {
            plr.sendMessage("There is a sheer drop below the hole. You will need a rope.")
            return
        }
        plr.swampCavesRoped = true
        plr.sendVarbit(Varbit(ROPED_VARBIT, 1))
        plr.sendMessage("You tie the rope to the top of the entrance and throw it down.")
    }
    Entrances.travel(plr, shift(0, 6400).from(plr.position), delay = 1, animation = 827)
}

object1(DARK_HOLE) { climbDown(plr) }
useItem(ROPE).onObject(DARK_HOLE) { climbDown(plr) }

on(LoginEvent::class) {
    if (plr.swampCavesRoped) {
        plr.sendVarbit(Varbit(ROPED_VARBIT, 1))
    }
}
