package world.player.skill.herblore.makeUnfPotion

import api.predef.herblore
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] that will make unfinished potions.
 */
class MakeUnfActionItem(
    plr: Player,
    val unfPotion: UnfPotion,
    makeTimes: Int
) : InventoryAction(plr, true, 2, makeTimes) {

    companion object {

        /**
         * The unfinished potion making animation.
         */
        val ANIMATION = Animation(363)
    }

    override fun executeIf(start: Boolean) =
        when {
            mob.herblore.level < unfPotion.level -> {
                mob.sendMessage("You need a Herblore level of ${unfPotion.level} to make this potion.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.sendMessage("You put the ${unfPotion.herbName} into the vial of water.")
        mob.animation(ANIMATION)
    }

    override fun add() = listOf(unfPotion.idItem)

    override fun remove() = listOf(unfPotion.herbItem, Item(UnfPotion.VIAL_OF_WATER))
}