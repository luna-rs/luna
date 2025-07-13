package world.player.skill.crafting.armorCrafting

import api.attr.Attr
import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] used for crafting armor from tanned hides.
 */
class CraftArmorActionItem(
    private val plr: Player,
    private val armor: HideArmor,
    amount: Int
) : InventoryAction(plr, true, 4, amount) {

    companion object {

        /**
         * The crafting animation.
         */
        val ANIM = Animation(1249)

        /**
         * The needle identifier.
         */
        val NEEDLE_ID = 1733

        /**
         * The thread identifier.
         */
        val THREAD_ID = 1734
    }

    /**
     * When a spool of thread will be consumed.
     */
    private var Player.threadLeft by Attr.int()

    override fun executeIf(start: Boolean) =
        when {
            plr.crafting.level < armor.level -> {
                plr.sendMessage("You need a Crafting level of ${armor.level} to make this.")
                false
            }

            !plr.inventory.containsAll(NEEDLE_ID, THREAD_ID) -> {
                plr.sendMessage("You need a needle and thread in order to craft armor.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.animation(ANIM)
        mob.sendMessage("You make ${articleItemName(armor.armorItem.id)}.")
    }

    override fun add(): List<Item> = listOf(armor.armorItem)

    override fun remove(): List<Item> {
        val rem = armor.hidesItem!!
        return if (mob.threadLeft <= 0) {
            // We have no thread left, remove one from inventory and reset counter.
            mob.threadLeft = rand(4) + 1
            plr.sendMessage("You use up one of your reels of thread.")
            listOf(rem, Item(THREAD_ID))
        } else {
            // Decrement thread counter.
            mob.threadLeft--
            listOf(rem)
        }
    }
}
