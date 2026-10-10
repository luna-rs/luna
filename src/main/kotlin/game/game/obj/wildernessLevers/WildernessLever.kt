package game.obj.wildernessLevers

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Pulling a wall lever down.
 */
private val PULL_DOWN = Animation(2140)

/**
 * The levers that teleport players into and out of the deep Wilderness, the King Black Dragon's lair, the Mage Arena
 * bank and the Mage Arena.
 *
 * @property messageDelay Ticks after the pull until "You pull the lever...". The teleport starts a tick later.
 * @property leverAnimation The lever's own animation, for levers that spring back up. Without one, the lever is swapped
 * for a pulled-down lever for a few ticks.
 * @author TheLining
 */
enum class WildernessLever(val id: Int,
                           val destination: Position,
                           val arrivalMessage: String,
                           val messageDelay: Int,
                           val pullAnimation: Animation = PULL_DOWN,
                           val leverAnimation: Int? = null) {
    ARDOUGNE(id = 1814,
             destination = Position(3154, 3924),
             arrivalMessage = "...And teleport into the wilderness.",
             messageDelay = 2),
    DESERTED_KEEP(id = 1815,
                  destination = Position(2562, 3311),
                  arrivalMessage = "...And teleport out of the wilderness.",
                  messageDelay = 1),
    LAVA_MAZE_DUNGEON(id = 1816,
                      destination = Position(2271, 4680),
                      arrivalMessage = "... and teleport into the lair of the King Black Dragon!",
                      messageDelay = 1),
    KING_BLACK_DRAGON_LAIR(id = 1817,
                           destination = Position(3067, 10254),
                           arrivalMessage = "... and teleport out of the lair of the King Black Dragon!",
                           messageDelay = 1),
    MAGE_ARENA_HUT(id = 5959,
                   destination = Position(2539, 4712),
                   arrivalMessage = "...And teleport into the mage's cave.",
                   messageDelay = 2),
    MAGE_ARENA_BANK(id = 5960,
                    destination = Position(3090, 3956),
                    arrivalMessage = "...And teleport out of the mage's cave.",
                    messageDelay = 2),
    MAGE_ARENA_ENTRANCE(id = 9706,
                        destination = Position(3105, 3951),
                        arrivalMessage = "...And teleport into the arena.",
                        messageDelay = 2,
                        pullAnimation = Animation(2710),
                        leverAnimation = 2711),
    MAGE_ARENA_EXIT(id = 9707,
                    destination = Position(3105, 3956),
                    arrivalMessage = "...And teleport out of the arena.",
                    messageDelay = 2,
                    pullAnimation = Animation(2710),
                    leverAnimation = 2711);

    companion object {

        /**
         * If the player chose not to see the Ardougne lever's warning again.
         */
        var Player.skipWildernessLeverWarning by Attr.boolean().persist("skip_wilderness_lever_warning")
    }
}
