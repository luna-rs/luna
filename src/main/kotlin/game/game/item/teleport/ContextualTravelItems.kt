package game.item.teleport

import io.luna.game.model.Position
import io.luna.game.model.area.Area

/**
 * Native geography for owned whistle and bell travel. These interactions move locally through their existing
 * player dialogues and do not advance any quest. The same facts supply generated inventory-item crossings.
 *
 * @author lare96
 */
object ContextualTravelItems {
    const val WHISTLE = 16
    const val BELL = 17
    val WHISTLE_MAINLAND = Area.of(2740, 3234, 2743, 3237)
    val WHISTLE_REALM = Area.of(2624, 4672, 2815, 4735)
    val MAINLAND_LANDING = Position(2741, 3235)
    val REALM_LANDING = Position(2677, 4715)
    val BELL_BRICKS = listOf(Position(2761, 4693), Position(2762, 4693))
    val BELL_LANDING = Position(2761, 4692)
    const val BELL_RANGE = 4
}
