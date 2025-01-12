package world.player.skill.magic.enchantJewellery

import api.attr.Attr
import api.predef.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.GameTabSet.TabIndex
import io.luna.net.msg.out.SoundMessageWriter
import world.player.skill.magic.Magic

/**
 * A [QueuedAction] that handles enchanting rings, necklaces, and amulets.
 */
class EnchantJewelleryAction(plr: Player, private val itemId: Int, private val type: EnchantJewelleryType) :
    QueuedAction<Player>(plr, plr.enchantJewelleryDelay, 3) {

    companion object {

        /**
         * The time source attribute for this action.
         */
        val Player.enchantJewelleryDelay by Attr.timeSource()
    }

    override fun execute() {
        if (Magic.checkRequirements(mob, type.level, type.requirements)) {
            val enchantItem = type.enchantMap[itemId]
            if (enchantItem == null) {
                // todo proper message
                mob.sendMessage("You cannot use this spell on this item.")
                return
            }
            if (mob.inventory.replace(itemId, enchantItem.id)) {
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
}