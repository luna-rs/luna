package world.player.skill.herblore.makePotion

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that will make potions.
 */
class MakePotionActionItem(plr: Player,
                           val potion: FinishedPotion,
                           makeTimes: Int) : InventoryAction(plr, true, 2, makeTimes) {

    companion object {

        /**
         * Potion making animation.
         */
        val ANIMATION = Animation(363)
    }


    override fun executeIf(start: Boolean) =
        when {
            mob.herblore.level < potion.level -> {
                mob.sendMessage("You need a Herblore level of ${potion.level} to make this potion.")
                false
            }
            else -> true
        }

    override fun execute() {
        mob.sendMessage("You mix the ${itemName(potion.secondary)} into your potion.")
        mob.animation(ANIMATION)
        mob.herblore.addExperience(potion.exp)
    }

    override fun add() = listOf(potion.idItem)

    override fun remove() = listOf(potion.unfItem, potion.secondaryItem)

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is MakePotionActionItem -> potion == other.potion
            else -> false
        }
}