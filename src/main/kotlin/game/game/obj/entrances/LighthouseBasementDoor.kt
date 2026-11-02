package game.obj.entrances

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The Lighthouse basement's separate one-way entry and exit walls from Lost City's quest_horror script.
 * TODO Restore the six Horror from the Deep puzzle flags on entry when that quest exists. As with the existing
 * Lighthouse entrances, no quest progress or completed-puzzle state is manufactured in the meantime.
 *
 * @author lare96
 */
object LighthouseBasementDoor {

    /**
     * Each side uses its source door, inactive replacement and exact direction across the wall.
     *
     * @author lare96
     */
    enum class Wall(val id: Int, open: Int, val position: Position, val from: Position, val to: Position) {
        ENTRY(4545, 4549, Position(2516, 10003, 1), Position(2516, 10002, 1), Position(2516, 10003, 1)),
        EXIT(4546, 4548, Position(2513, 10003, 1), Position(2513, 10003, 1), Position(2513, 10002, 1));

        internal val type = DoorType(id, open, null, Sound.STRANGEDOOR_OPEN, Sound.STRANGEDOOR_CLOSE, 3)
    }

    /**
     * Only the source placement can open this passage; a wrong-side click cannot reverse its direction.
     */
    fun use(player: Player, obj: GameObject) {
        val wall = Wall.entries.firstOrNull { it.id == obj.id && it.position == obj.position } ?: return
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.isLocked) return
        player.submitAction(Passage(player, obj, wall))
    }

    /**
     * Reuses ordinary native alignment and the per-player temporary wall while enforcing the source's allowed side.
     *
     * @author lare96
     */
    internal class Passage(player: Player, obj: GameObject, private val wall: Wall) :
        DoorPassageAction(player, obj, wall.type) {

        override fun checkAccess(entering: Boolean): Boolean {
            if (mob.state != EntityState.ACTIVE || !mob.isAlive || mob.isLocked) return false
            if (mob.position != wall.from) {
                mob.sendMessage("The door cannot be opened from this side.")
                return false
            }
            return true
        }

        override fun onPassage(entering: Boolean) {
            world.scheduleOnce(2) {
                if (mob.state == EntityState.ACTIVE) mob.playSound(Sound.STRANGEDOOR_CLOSE)
            }
        }
    }
}
