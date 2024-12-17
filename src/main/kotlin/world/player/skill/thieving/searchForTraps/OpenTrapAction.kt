package world.player.skill.thieving.searchForTraps

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.ThrottledAction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Hit
import io.luna.game.model.mob.block.Hit.HitType
import io.luna.game.model.`object`.GameObject
import world.player.Animations

/**
 * A [ThrottledAction] that handles opening trapped chests.
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
        if (mob.thieving.level < thievable.level) {
            mob.sendMessage("You need a Thieving level of ${thievable.level} to search for traps here.")
            return
        } else if (obj.state == EntityState.INACTIVE) {
            return
        }
        mob.interact(obj)
        mob.animation(Animations.PICKPOCKET)
        mob.damage(computeDamage())
    }

    /**
     * Determines how much damage will be done when a trap is opened.
     */
    private fun computeDamage(): Hit {
        val maxDamage = (thievable.level / 2).coerceAtMost(mob.health)
        val damage = rand(1, maxDamage)
        return when {
            maxDamage == 0 -> Hit(0, HitType.BLOCKED)
            else -> Hit(damage, HitType.NORMAL)
        }
    }
}