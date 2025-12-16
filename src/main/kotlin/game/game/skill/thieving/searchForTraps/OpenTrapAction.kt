package game.skill.thieving.searchForTraps

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ThrottledAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.block.Hit.HitType
import io.luna.game.model.`object`.GameObject
import game.player.Animations

/**
 * A [ThrottledAction] that handles opening trapped chests.
 *
 * @author lare96
 */
class OpenTrapAction(plr: Player, val obj: GameObject, val thievable: ThievableChest) :
    ThrottledAction<Player>(plr, plr.openTrappedChest, 2) {

    companion object {

        /**
         * The time source for this action.
         */
        val Player.openTrappedChest by Attr.timeSource()
    }

    override fun execute() {
        if (obj.state == EntityState.INACTIVE) {
            return
        }
        mob.interact(obj)
        mob.animation(Animations.PICKPOCKET)
        mob.damage(computeDamage())
    }

    /**
     * Determines how much damage will be done when a trap is opened.
     */
    private fun computeDamage(): Int {
        val maxDamage = (thievable.level / 2).coerceAtMost(mob.health)
        return rand(0, maxDamage)
    }
}