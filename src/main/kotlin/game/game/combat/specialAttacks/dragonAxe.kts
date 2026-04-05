package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.activate
import api.predef.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.DRAGON_AXE

// Lumber up!
activate(type = DRAGON_AXE, drain = 100) {
    // TODO Really weird timing on the graphic + animation. Tried to use the delay values with no success.
    //  would be nice to do this without a LockedAction.
    actions.submit(object : LockedAction(this, false, 2) {
        override fun onLock() {
            graphic(Graphic(479, 0, 100))
        }
        override fun run(): Boolean {
            animation(Animation(409, AnimationPriority.HIGH))
            speak("Chop chop!")
            woodcutting.level += 3
            return true
        }
    })
}