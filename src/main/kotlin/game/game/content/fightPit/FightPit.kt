package game.content.fightPit

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * The TzHaar Fight Pit: a waiting room off the city and the pit beyond it, where the last player standing becomes
 * the champion.
 *
 * @author TheLining
 */
object FightPit {

    /**
     * The hot vent door between the city and the waiting room.
     */
    const val NORTH_DOOR = 9369

    /**
     * The hot vent door between the waiting room and the pit: the fire wall players leave the pit by.
     */
    const val SOUTH_DOOR = 9368

    /**
     * TzHaar-Mej-Kah, who runs the pit from outside the north door.
     */
    const val MEJ_KAH = 2618

    /**
     * The overlay that names the champion and counts the foes left.
     */
    const val OVERLAY = 2804

    /**
     * The overlay's champion line.
     */
    const val CHAMPION_TEXT = 2805

    /**
     * The layer holding the overlay's foes line.
     */
    const val FOES_LINE = 3208

    /**
     * The client variable the foes line shows. At 0 the line reads "You're the Winner!".
     */
    const val FOES_VARP = 560

    /**
     * The champion the overlay names until a player wins.
     */
    const val FIRST_CHAMPION = "TzHaar-Xil-Huz"

    /**
     * The waiting room, between the two doors.
     */
    val WAITING_ROOM = Area.of(2394, 5169, 2404, 5175)

    /**
     * The pit.
     */
    val PIT = Area.of(2375, 5129, 2418, 5167)

    /**
     * The city side of the north door, where players come out.
     */
    val OUTSIDE = Position(2399, 5177)

    /**
     * The waiting room side of the north door.
     */
    val NORTH_INSIDE = Position(2399, 5175)

    /**
     * The waiting room side of the south door.
     */
    val SOUTH_INSIDE = Position(2399, 5169)

    /**
     * The pit side of the south door.
     */
    val PIT_EXIT = Position(2399, 5167)

    /**
     * The TzHaar name of the last player to win, or [FIRST_CHAMPION].
     */
    var championName = FIRST_CHAMPION
        private set

    /**
     * Whether [position] is in the waiting room or the pit.
     */
    fun inside(position: Position) = WAITING_ROOM.contains(position) || PIT.contains(position)

    /**
     * Lets [plr], who just came into the waiting room, wait for the next fight.
     */
    fun enter(plr: Player) {
        if (plr.controllers.primary !is FightPitController) plr.controllers.register(FightPitController(plr))
    }

    /**
     * Names [name] as the new champion, and Mej-Kah announces them.
     */
    fun crown(name: String) {
        championName = name
        say("$name is the champion!")
    }

    /**
     * Has TzHaar-Mej-Kah say [text] overhead, by the north door.
     */
    fun say(text: String) {
        world.npcs.firstOrNull { it.id == MEJ_KAH }?.speak(text)
    }

    /**
     * Shows [plr] the overlay as it stands now.
     */
    fun showOverlay(plr: Player) {
        plr.overlays.open(WalkableInterface(OVERLAY))
        plr.sendText("Current Champion: $championName", CHAMPION_TEXT)
        plr.queue(WidgetVisibilityMessageWriter(FOES_LINE, true))
    }

    /**
     * Takes the overlay off [plr]'s screen.
     */
    fun hideOverlay(plr: Player) {
        plr.overlays.closeWalkable()
    }
}
