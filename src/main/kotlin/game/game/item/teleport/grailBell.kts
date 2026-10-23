package game.item.teleport

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The Grail bell item id.
 */
val GRAIL_BELL = 17

/**
 * The Grail Maiden npc id.
 */
val GRAIL_BELL_MAIDEN = 210

/**
 * The confused chathead animation for a three-line chat.
 */
val GRAIL_BELL_MAIDEN_EXPRESSION = 577

/**
 * The dismantled bricks that fill the breach in the Grail castle's north wall, in the dilapidated Fisher Realm.
 */
val GRAIL_BELL_BRICKS = listOf(Position(2761, 4693), Position(2762, 4693))

/**
 * How many tiles from the bricks the bell can be rung.
 */
val GRAIL_BELL_RANGE = 4

/**
 * Where the bell puts the player, inside the castle just south of the bricks.
 */
val GRAIL_BELL_LANDING = Position(2761, 4692)

/**
 * Rings the Grail bell. Once the player continues, a Grail Maiden lets them into the castle if they rang it on the
 * ground floor near the bricks; anywhere else nothing happens. The bell is never used up.
 *
 * @param plr The player ringing the bell.
 */
fun ringGrailBell(plr: Player) {
    plr.newDialogue().text("Ting-a-ling-a-ling!").then {
        if (GRAIL_BELL_BRICKS.none { it.isWithinDistance(plr.position, GRAIL_BELL_RANGE) }) {
            plr.sendMessage("Nothing happens.")
            return@then
        }
        plr.newDialogue()
            .npc(GRAIL_BELL_MAIDEN, GRAIL_BELL_MAIDEN_EXPRESSION,
                 "Welcome to the Grail castle.", "You should come inside,", "it's cold out here.")
            .then {
                plr.move(GRAIL_BELL_LANDING)
                plr.newDialogue().text("Somehow you are now inside the castle.").open()
            }.open()
    }.open()
}

item1(GRAIL_BELL) { ringGrailBell(plr) }
