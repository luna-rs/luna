package game.obj.entrances

import api.predef.ext.*
import engine.obj.LadderDestination
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.action.DeathInterruptible
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobDeathAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The military hangars' native teleportation devices, returning to the Stronghold near the Gnomeball pitch.
 * Lost City's revision 289 Monkey Madness reconstruction supplies the first-option handlers, landing and fade
 * sequence; revision 377 supplies the device placements and native presentation assets.
 *
 * @author lare96
 */
object HangarExit {

    val IDS = setOf(4868, 4869, 4870)
    val DESTINATION = Position(2393, 3466)
    const val POWERED_DEVICE = 4869
    val POWERED_POSITION = Position(2653, 4495)
    val POWERED_APPROACH = Position(2652, 4495)
    private const val FADE_OVERLAY = 11124
    private const val FADE_WIDGET = 11125
    private const val LANDING_TICK = 4
    private const val FINISH_TICK = 7

    /**
     * One native device part, including its cache placement, type and rotation.
     *
     * @author lare96
     */
    private data class Device(val id: Int, val position: Position, val type: ObjectType, val rotation: Int)

    private val devices = buildList {
        add(Device(4868, Position(2589, 4494), ObjectType.DEFAULT, 1))
        for (origin in listOf(Position(2397, 9870), Position(2653, 4494))) {
            add(Device(4869, origin.translate(0, 1), ObjectType.GROUND_DECORATION, 1))
            add(Device(4869, origin.translate(1, 0), ObjectType.GROUND_DECORATION, 0))
            add(Device(4869, origin.translate(2, 1), ObjectType.GROUND_DECORATION, 3))
            add(Device(4870, origin, ObjectType.GROUND_DECORATION, 0))
            add(Device(4870, origin.translate(0, 2), ObjectType.GROUND_DECORATION, 1))
            add(Device(4870, origin.translate(2, 0), ObjectType.GROUND_DECORATION, 3))
            add(Device(4870, origin.translate(2, 2), ObjectType.GROUND_DECORATION, 2))
        }
    }

    /**
     * Starts only from an ordinary reachable first-option click on the current native device.
     */
    fun use(player: Player, obj: GameObject) {
        if (canUse(player, obj)) player.submitAction(ExitAction(player, obj))
    }

    private fun alive(player: Player) = player.state == EntityState.ACTIVE && player.isAlive &&
        player.actions.getAll(MobDeathAction::class.java).none { !it.isFinished }

    private fun validObject(player: Player, obj: GameObject) = obj.state == EntityState.ACTIVE && !obj.isHidden &&
        obj.isVisibleTo(player) && devices.any {
            it.id == obj.id && it.position == obj.position && it.type == obj.objectType && it.rotation == obj.direction.id
        } && player.world.objects.findAll(obj.position).anyMatch { it === obj }

    private fun canUse(player: Player, obj: GameObject) = alive(player) && !player.isLocked &&
        !player.walking.isLocked && !player.status.isImmobilized() && validObject(player, obj) &&
        player.position.z == obj.position.z &&
        player.world.collisionManager.reached(player, obj, InteractionPolicy.STANDARD_SIZE)

    /**
     * Holds the departure tile until teleportation and releases only the hard lock acquired by this exit.
     * Death, removal, a displaced player or a replaced device cancels without a delayed move.
     *
     * @author lare96
     */
    internal class ExitAction(player: Player, private val obj: GameObject) :
        Action<Player>(player, ActionType.SOFT, false, 1), DeathInterruptible {

        private val origin = player.position
        private var lockRevision: Long? = null
        private var elapsed = 0
        private var arrived = false
        private var fade: WalkableInterface? = null

        override fun onSubmit() {
            if (!canUse(mob, obj)) { complete(); return }
            mob.overlays.closeWindows()
            mob.walking.clear()
            mob.lock()
            lockRevision = mob.actionLockRevision
            mob.animation(Animation(714))
            mob.graphic(Graphic(111, 92))
            mob.playSound(Sound.TELEPORT_ALL)
            showFade(1377)
        }

        override fun run(): Boolean {
            if (!travelling()) return true
            ++elapsed
            if (!arrived && elapsed >= LANDING_TICK) {
                if (!LadderDestination.canLand(DESTINATION)) return true
                mob.move(DESTINATION)
                arrived = true
                showFade(1378)
            }
            if (arrived && elapsed == LANDING_TICK + 1) mob.animation(Animation.CANCEL)
            return elapsed >= FINISH_TICK
        }

        private fun travelling() = lockRevision != null && mob.isActionLocked &&
            mob.actionLockRevision == lockRevision && alive(mob) &&
            mob.position == (if (arrived) DESTINATION else origin) && (arrived || validObject(mob, obj))

        private fun showFade(sequence: Int) {
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, -1))
            fade = WalkableInterface(FADE_OVERLAY).also { mob.overlays.open(it) }
            mob.queue(WidgetItemModelMessageWriter(FADE_WIDGET, 1000, 4032))
            mob.queue(WidgetAnimationMessageWriter(FADE_WIDGET, sequence))
        }

        override fun onFinished() {
            val revision = lockRevision ?: return
            lockRevision = null
            if (fade != null && mob.overlays.getOverlay(WalkableInterface::class.java) === fade) {
                mob.overlays.closeWalkable()
            }
            if (mob.isActionLocked && mob.actionLockRevision == revision) {
                mob.animation(Animation.CANCEL)
                mob.unlock()
            }
        }
    }
}
