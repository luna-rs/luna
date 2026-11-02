package game.obj.entrances

import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * The dramen-staff entrance to Zanaris and its unrestricted fairy-ring return to Lumbridge swamp.
 *
 * @author lare96
 */
object Zanaris {

    const val DOOR = 2406
    const val STAFF = 772
    const val RETURN_RING = 12094
    val SHED_OUTSIDE = Position(3201, 3169)
    val LANDING = Position(2452, 4471)
    val RETURN_APPROACH = Position(2452, 4472)
    private val doorType = DoorType(DOOR, 1532, null, null, null, 3)

    /**
     * Opens the shed normally; entering with the staff equipped triggers its magic.
     */
    fun openDoor(plr: Player, door: GameObject) {
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Uses the native return ring without requiring a staff or casting a teleport spell.
     */
    fun returnToSwamp(plr: Player, ring: GameObject) {
        if (plr.isLocked || !canArrive(plr) || plr.z != ring.position.z ||
            !plr.position.isWithinDistance(ring.position, 2)) {
            return
        }
        Entrances.sequence(plr, { plr.animation(Animation(3265)) }, {
            if (canArrive(plr)) {
                plr.move(SHED_OUTSIDE)
                plr.animation(Animation(3266))
            }
        })
    }

    /**
     * Prevents travel from moving a dead player or bypassing an unfinished death sequence.
     */
    private fun canArrive(plr: Player): Boolean = plr.isAlive &&
        plr.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    /**
     * Checks the worn staff only after stepping into the shed.
     *
     * @author lare96
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = true

        override fun onPassage(entering: Boolean) {
            if (!entering && mob.equipment[Equipment.WEAPON]?.id == STAFF) {
                // TODO Complete Lost City here once its staff-making stage and quest rewards are implemented.
                mob.submitAction(ShedTeleport(mob))
            }
        }
    }

    /**
     * Uses the native normal teleport sequence and item effects without spell admission checks.
     *
     * @author lare96
     */
    private class ShedTeleport(plr: Player) : TeleportAction(plr, destination = LANDING, style = TeleportStyle.REGULAR) {
        override fun beforeLock(): Boolean = !mob.isLocked && canArrive(mob)

        override fun onLock() {
            mob.overlays.closeWindows()
            mob.sendMessage("The world starts to shimmer...")
        }

        override fun run(): Boolean {
            // The queued door step runs before this action's first tick.
            if (executions == 0) mob.walking.clear()
            return super.run()
        }
    }
}
