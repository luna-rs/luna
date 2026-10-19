package game.skill.thieving.pickpocketNpc

import api.predef.ext.*
import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic

/**
 * The stun from a failed pickpocket: birds over the player's head and no moving or pickpocketing for [ticks].
 */
class PickpocketStun(plr: Player, ticks: Int) :
    BasicStatusEffect<Player>(plr, StatusEffectType.STUNNED, ticks.ticks) {

    companion object {

        /**
         * The thieving stun birds.
         */
        val BIRDS = Graphic(245, 100, 0)
    }

    override fun onStart(restored: Boolean) {
        mob.graphic(BIRDS)
        mob.walking.clear()
    }
}
