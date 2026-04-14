package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activation
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_BATTLE_AXE

/**
 * The animation played during the special attack.
 */
val ANIMATION = Animation(1056, AnimationPriority.HIGH)

/**
 * The graphic played during the special attack.
 */
val GRAPHIC = Graphic(246, 0, 0)

activation(type = DRAGON_BATTLE_AXE, drain = 100) {
    animation(ANIMATION)
    graphic(GRAPHIC)
    speak("Raarrrrrgggggghhhhhhh!")
    val attackDrain = (attack.level * 0.1).toInt()
    val defenceDrain = (defence.level * 0.1).toInt()
    val rangedDrain = (ranged.level * 0.1).toInt()
    val magicDrain = (magic.level * 0.1).toInt()
    val strengthBoost = 10 + ((attackDrain + defenceDrain + rangedDrain + magicDrain) / 4)

    attack.level -= attackDrain
    defence.level -= defenceDrain
    ranged.level -= rangedDrain
    magic.level -= magicDrain

    strength.boost(strengthBoost)
}