package game.skill.magic.teleOther

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import game.skill.magic.Magic
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.LocalSound
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.varp.PersistentVarp

/**
 * A strong action that owns both participants' locks while [source] teleports [target]. Rejected admission never
 * locks or charges either actor. Cleanup releases only unchanged locks actually acquired here and preserves a
 * replacement lock or strong action, including death; interruption after payment does not repeat the accepted cast.
 *
 * @author lare96
 */
class TeleOtherAction(private val source: Player, private val target: Player, private val type: TeleOtherType) :
    Action<Player>(source, ActionType.STRONG) {

    companion object {

        /**
         * An attribute representing all recent teleother requests sent by the player.
         */
        val Player.teleOtherRequests by Attr.map<Long, Long>()
    }

    private var sourceLockAcquired = false
    private var targetLockAcquired = false
    private var sourceLockRevision = -1L
    private var targetLockRevision = -1L
    private var paid = false
    private var sourceOrigin: Position? = null
    private var targetOrigin: Position? = null

    override fun onSubmit() {
        if (source === target || !alive(source) || !alive(target) || source.isLocked || target.isLocked ||
            hasOtherStrong(source) || hasOtherStrong(target) || !source.position.isViewable(target) ||
            target.varpManager.getValue(PersistentVarp.ACCEPT_AID) == 0) {
            complete()
            return
        }
        val removeItems = Magic.checkRequirements(source, type.level, type.requirements)
        if (removeItems == null || !source.inventory.containsAll(removeItems)) {
            complete()
            return
        }
        sourceOrigin = source.position
        targetOrigin = target.position
        source.lock()
        sourceLockAcquired = true
        sourceLockRevision = source.actionLockRevision
        target.lock()
        targetLockAcquired = true
        targetLockRevision = target.actionLockRevision
        if (removeItems.isNotEmpty() && !source.inventory.removeAll(removeItems)) {
            complete()
            return
        }
        paid = true
        source.magic.addExperience(type.xp)
    }

    override fun run(): Boolean {
        if (!paid || !alive(source) || !alive(target) || hasOtherStrong(source) || hasOtherStrong(target) ||
            source.actionLockRevision != sourceLockRevision || target.actionLockRevision != targetLockRevision ||
            source.position != sourceOrigin || target.position != targetOrigin) return true
        return when (executions) {
            0 -> {
                source.playSound(Sound.TELE_OTHER_CAST)
                source.animation(Animations.CAST_TELEOTHER)
                source.graphic(Graphic(343, 50))
                false
            }

            1, 2 -> false
            3 -> {
                val sound = LocalSound.of(ctx,
                                          Sound.TELEPORT_ALL,
                                          target.position,
                                          ChunkUpdatableView.globalView())
                sound.display()
                target.graphic(Graphic(342))
                target.animation(Animations.RECEIVE_TELEOTHER)
                false
            }

            4, 5 -> false
            6 -> {
                target.move(type.destination)
                source.sendMessage("You teleport ${target.username} to ${type.location}.")
                target.sendMessage("You are teleported to ${type.location} by {${source.username}.")
                target.animation(Animation.CANCEL)
                true
            }

            else -> true
        }
    }

    override fun onFinished() {
        if (targetLockAcquired) {
            targetLockAcquired = false
            if (target.actionLockRevision == targetLockRevision && !hasOtherStrong(target)) target.unlock()
        }
        if (sourceLockAcquired) {
            sourceLockAcquired = false
            if (source.actionLockRevision == sourceLockRevision && !hasOtherStrong(source)) source.unlock()
        }
    }

    /**
     * A lock acquired by a later queued strong action supersedes this cast's cleanup responsibility.
     */
    private fun hasOtherStrong(player: Player): Boolean =
        player.actions.getAll<Action<*>>(ActionType.STRONG).any { it !== this }

    private fun alive(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive
}
