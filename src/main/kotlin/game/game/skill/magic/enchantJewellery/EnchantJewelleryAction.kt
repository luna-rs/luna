package game.skill.magic.enchantJewellery

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import game.content.randomEvents.RandomEvents
import game.skill.magic.Magic
import io.luna.game.action.impl.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Enchants jewellery through the native queued action. Successful conversions offer Lost City's
 * general random-event pool one tick later, after the item replacement, rune cost and experience.
 * The supplied source handles enchantment levels one through five; onyx retains its general timer.
 *
 * @author lare96
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
            val itemId = mob.inventory.computeIdForIndex(itemIndex)
            val enchantItem = type.enchantMap[itemId]
            if (enchantItem == null) {
                mob.sendMessage("Nothing interesting happens.")
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
                mob.sendMessage("You successfully enchant the ${itemName(itemId).lowercase()}.");
            }
            mob.tabs.show(TabIndex.MAGIC)
            if (type != EnchantJewelleryType.LVL_6) {
                world.scheduleOnce(1) { RandomEvents.offerGeneral(mob) }
            }
        }
    }
}
