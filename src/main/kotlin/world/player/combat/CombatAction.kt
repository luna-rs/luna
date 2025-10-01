package world.player.combat

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Mob

class CombatAction(mob: Mob) : Action<Mob>(mob, ActionType.NORMAL, true, 1) {
    override fun run(): Boolean {
        TODO("Not yet implemented")
    }
}