package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activation
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.EXCALIBUR

/**
 * The animation played during the special attack.
 */
val ANIMATION = Animation(1057, AnimationPriority.HIGH)

/**
 * The graphic played during the special attack.
 */
val GRAPHIC = Graphic(247, 0, 0)

activation(type = EXCALIBUR, drain = 100) {
    animation(ANIMATION)
    graphic(GRAPHIC)
    defence.boost(8)
    speak("For Camelot!")
}