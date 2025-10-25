package api.predef.ext

import engine.interaction.follow.MobFollowAction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Animation
import game.player.Animations
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Forwards to [Mob.animation] with no animation priority.
 */
fun Mob.animation(animation: Animations) {
    animation(Animation(animation.id))
}

/**
 * Forwards to [Mob.animation] with [priority].
 */
fun Mob.animation(animation: Animations, priority: AnimationPriority) {
    animation(Animation(animation.id, priority))
}

/**
 * Submits a [MobFollowAction] to the mob's action queue.
 */
fun Mob.follow(target: Mob) {
    submitAction(MobFollowAction(this, target))
}