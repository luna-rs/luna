package game.obj.entrances

import api.predef.*
import api.predef.ext.*

/**
 * A rope.
 */
val ROPE = 954

/**
 * How many ticks a rope stays tied to a Kalphite Lair tunnel entrance.
 */
val ROPE_TICKS = 200

/**
 * The tunnel entrance into the Kalphite Lair, and the same entrance with a rope tied to it.
 */
val KALPHITE_LAIR = 3827
val KALPHITE_LAIR_TIED = 3828

/**
 * The tunnel entrance into the Kalphite Queen's chamber, and the same entrance with a rope tied to it.
 */
val KALPHITE_QUEEN = 3830
val KALPHITE_QUEEN_TIED = 3831

/**
 * The danger signs outside the Fremennik Slayer Dungeon and the Slayer Tower.
 */
val DANGER_SIGN = 5127

for (id in Entrance.BY_ID.keys) {
    object1(id) {
        val entrance = Entrance.forObject(id, gameObject.position)
        if (entrance == null) {
            plr.sendMessage("Nothing interesting happens.")
        } else {
            Entrances.use(plr, entrance)
        }
    }
}

// The Kalphite Lair's tunnel entrances, which a rope has to be tied to before they can be climbed down. The rope is
// used up and stays tied for a while.
for ((entrance, tied) in listOf(KALPHITE_LAIR to KALPHITE_LAIR_TIED, KALPHITE_QUEEN to KALPHITE_QUEEN_TIED)) {
    useItem(ROPE).onObject(entrance) {
        if (plr.inventory[usedItemIndex]?.id == ROPE && Entrances.replace(gameObject, tied, ROPE_TICKS)) {
            plr.inventory.set(usedItemIndex, null)
        }
    }
    useItem(ROPE).onObject(tied) { plr.sendMessage("There's already a rope attached to this entrance.") }
}

// The Slayer areas' danger signs.
object1(DANGER_SIGN) {
    plr.newDialogue()
        .text("@red@WARNING!", "This area contains very dangerous creatures!", "Do not pass unless properly prepared!")
        .open()
}
