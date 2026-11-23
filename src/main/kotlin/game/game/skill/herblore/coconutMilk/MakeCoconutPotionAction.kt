package game.skill.herblore.coconutMilk

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] that puts an ingredient into a vial of coconut milk.
 *
 * @author TheLining
 */
class MakeCoconutPotionAction(plr: Player,
                              val potion: CoconutPotion,
                              makeTimes: Int) : InventoryAction(plr, true, 2, makeTimes) {

    companion object {

        /**
         * The potion making animation.
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
        mob.sendMessage("You put the ${potion.ingredientName} into the coconut milk.")
        mob.animation(ANIMATION)
        mob.playSound(Sound.GRIND)
    }

    override fun add() = listOf(potion.idItem)

    override fun remove() = listOf(potion.ingredientItem, Item(CoconutPotion.COCONUT_MILK))
}
