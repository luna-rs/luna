package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activation
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_AXE

/**
 * The animation played during the special attack.
 */
val ANIMATION = Animation(2876, AnimationPriority.HIGH)

/**
 * The graphic played during the special attack.
 */
val GRAPHIC = Graphic(479, 120, 0)

activation(type = DRAGON_AXE, drain = 100) {
    animation(ANIMATION)
    graphic(GRAPHIC)
    speak("Chop chop!")
    woodcutting.boost(3)
}
