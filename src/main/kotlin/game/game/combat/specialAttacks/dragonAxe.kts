package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activate
import api.predef.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_AXE

// Lumber up!
activate(type = DRAGON_AXE, drain = 100) {
    // TODO Really weird timing on the graphic. Tried to use the delay values with no success.
    animation(Animation(409, AnimationPriority.HIGH))
    speak("Chop chop!")
    woodcutting.level += 3
    // graphic(Graphic(479, 0, 100))
}