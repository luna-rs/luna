package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activation
import api.predef.*
import io.luna.game.model.mob.block.*
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_AXE

activation(type = DRAGON_AXE, drain = 100) {
    animation(Animation(2876, AnimationPriority.HIGH))
    graphic(Graphic(479, 120, 0))
    speak("Chop chop!")
    woodcutting.boost(3)
}
