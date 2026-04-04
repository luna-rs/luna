package engine.combat.weapons.specials

import api.combat.weapons.SpecialAttackHandler.special
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_AXE

/**
 * Will increase the Woodcutting level by this amount on activation.
 */
val INCREASE_LEVELS = 3

// Lumber up!
special(DRAGON_AXE) {
    //todo graphics/animation timing needs tweaking
    type = null // No combat type.
    drain = 100
    activate = {
        it.animation(Animation(409, AnimationPriority.HIGH))
        it.graphic(Graphic(479, -10, 100))
        it.speak("Chop chop!")
        it.woodcutting.level += INCREASE_LEVELS
    }
}