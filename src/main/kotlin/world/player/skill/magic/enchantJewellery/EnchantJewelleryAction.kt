package world.player.skill.magic.enchantJewellery

import api.attr.Attr
import api.predef.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.GameTabSet.TabIndex
import world.player.skill.magic.Magic

/**
 * A [QueuedAction] that handles enchanting rings, necklaces, and amulets.
 */
class EnchantJewelleryAction(plr: Player, private val itemIndex: Int, private val type: EnchantJewelleryType) :
    QueuedAction<Player>(plr, plr.enchantJewelleryDelay, 3) {

    companion object {

        /**
         * The time source attribute for this action.
         */
        val Player.enchantJewelleryDelay by Attr.timeSource()
    }

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements)
        if (removeItems != null) {
            val itemId = mob.inventory.computeIdForIndex(itemIndex).orElse(-1)
            val enchantItem = type.enchantMap[itemId]
            if (enchantItem == null) {
                // todo proper message
                mob.sendMessage("You cannot use this spell on this item.")
                return
            }
            mob.inventory.set(itemIndex, Item(enchantItem.id))
            mob.inventory.removeAll(removeItems)
            mob.magic.addExperience(type.xp)
            mob.animation(enchantItem.animation)
            mob.graphic(enchantItem.graphic)
            mob.playSound(enchantItem.sound)
            if (itemId == 1702) {
                mob.sendMessage("You successfully enchant the dragonstone amulet.");
            } else {
                mob.sendMessage("You successfully enchant the ${itemName(itemId).toLowerCase()}.");
            }
            mob.tabs.show(TabIndex.MAGIC)
        }
    }
}