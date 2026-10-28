package game.skill.prayer.buryBone

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import game.content.randomEvents.RandomEvents
import game.skill.prayer.Bone
import io.luna.game.action.impl.ThrottledAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Performs the player's existing bone burial with a shared three-tick cooldown.
 *
 * The carried bone is removed before experience or success effects are awarded. Delayed actions whose
 * bone disappeared, or whose player became inactive or died, complete without awarding experience.
 * [isReady] allows bot callers to respect the same cooldown without resetting it or bypassing gameplay.
 *
 * @param plr The player burying one bone.
 * @param bone The existing Prayer recipe to consume and award experience for.
 * @author lare96
 */
class BuryBoneAction(plr: Player, val bone: Bone) : ThrottledAction<Player>(plr, plr.lastBury, DELAY) {
    companion object {
        /**
         * Existing player cooldown, in game ticks.
         */
        const val DELAY = 3
        /**
         * Shared clock for all bone types buried by a player.
         */
        private val Player.lastBury by Attr.timeSource()
        /**
         * Existing bone-burying animation.
         */
        private val ANIMATION = Animation(827)

        /**
         * Checks the player's cooldown without consuming the next allowed execution.
         */
        fun isReady(plr: Player): Boolean = plr.lastBury.let { !it.isRunning || it.durationTicks >= DELAY }
    }

    override fun execute() {
        if (mob.state != EntityState.ACTIVE || mob.health <= 0 || !mob.inventory.remove(bone.boneItem)) return
        mob.animation(ANIMATION)
        mob.prayer.addExperience(bone.exp)
        mob.playSound(Sound.BONES_DOWN)
        mob.sendMessage("You dig a hole in the ground...")
        mob.sendMessage("You bury the bones.")
        RandomEvents.offerActivity(mob, RandomEvents.Activity.PRAYER)
    }
}
