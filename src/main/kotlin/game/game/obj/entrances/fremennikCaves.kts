package game.obj.entrances

import api.predef.*
import api.predef.ext.*
import game.obj.entrances.EntranceLanding.Companion.near
import io.luna.game.model.mob.Player

/**
 * Lalli, the troll whose cave is near Rellekka.
 */
val LALLI = 1270

/**
 * The iron ladder in the Waterbirth Island Dungeon, on the ground floor and its top half on the floor above.
 */
val WATERBIRTH_IRON_LADDERS = listOf(10177, 8956)

/**
 * Where the iron ladder leads: up to Askeladden's hill, and down to the dungeon's second sublevel.
 */
val ASKELADDENS_HILL = near(2545, 3743)
val WATERBIRTH_SUBLEVEL = near(1799, 4406, 3)

/**
 * Climbs [plr] up or down the Waterbirth Island Dungeon's iron ladder.
 */
fun climbIronLadder(plr: Player, up: Boolean) {
    plr.overlays.closeWindows()
    val destination = (if (up) ASKELADDENS_HILL else WATERBIRTH_SUBLEVEL).from(plr.position)
    Entrances.travel(plr, destination, if (up) "You climb up the ladder." else "You climb down the ladder.",
                     delay = 1, animation = if (up) 828 else 827)
}

// Lalli's cave, which Lalli won't let anyone into.
object1(4147) {
    plr.newDialogue()
        .npc(LALLI, "Hey human! You not go in my house! It where me", "keep all my stuff!")
        .open()
}

for (ladder in WATERBIRTH_IRON_LADDERS) {
    object1(ladder) {
        plr.newDialogue()
            .options("Climb Up.", { climbIronLadder(it, true) }, "Climb Down.", { climbIronLadder(it, false) })
            .title("Climb up or down the ladder?")
            .open()
    }
    object2(ladder) { climbIronLadder(plr, true) }
    object3(ladder) { climbIronLadder(plr, false) }
}
