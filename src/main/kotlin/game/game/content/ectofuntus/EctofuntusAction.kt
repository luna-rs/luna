package game.content.ectofuntus

import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * One manually requested step. Cache-backed animations are used where known; tick cadences are reconstructed.
 * Supplies and rewards change only on successful completion, so cancellation leaves the saved grinder intact.
 *
 * @author lare96
 */
class EctofuntusAction(player: Player, private val target: GameObject, private val step: Step,
                      private val index: Int = -1, private val bone: EctofuntusBone? = null) :
    LockedAction(player, false, 1) {

    /**
     * Separate manual operations; slime filling has no proven player animation in the native sequence metadata.
     *
     * @author lare96
     */
    enum class Step(val animation: Int, val ticks: Int, val sound: Sound?) {
        LOAD(1649, 2, Sound.FILL_GRINDER),
        GRIND(1648, 4, Sound.GRINDER_WIND),
        COLLECT(1650, 2, Sound.GRINDER_EMPTY),
        FILL(-1, 2, Sound.FILL_ECTOPLASM),
        WORSHIP(1651, 3, null)
    }

    private val origin = player.position
    private var ticks = 0
    private var committed = false

    override fun beforeLock(): Boolean = !mob.isLocked &&
            Ectofuntus.ready(mob, target, step, index, bone, true)

    override fun onLock() {
        if (step.animation >= 0) mob.animation(Animation(step.animation))
        step.sound?.let { mob.playSound(it) }
    }

    override fun run(): Boolean {
        if (committed || mob.position != origin || !Ectofuntus.ready(mob, target, step, index, bone, false)) return true
        if (++ticks < step.ticks) return false
        committed = true
        Ectofuntus.finish(mob, target, step, index, bone)
        return true
    }

    override fun onUnlock() {
        if (step.animation >= 0) mob.animation(Animation(-1))
    }
}
