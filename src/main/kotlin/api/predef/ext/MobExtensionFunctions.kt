package api.predef.ext

import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Animation
import world.player.Animations

fun Mob.animation(animation: Animations) {
    animation(Animation(animation.id))
}