package world.player.skill.magic.lowHighAlch

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.inter.GameTabSet.TabIndex
import world.player.Animations
import world.player.Sounds
import world.player.skill.magic.Magic

/**
 * A [QueuedAction] that handles the process of players doing low and high alchemy.
 */
class AlchemyAction(plr: Player, private val type: AlchemyType, private val inventoryIndex: Int) :
    QueuedAction<Player>(plr, plr.alchemyDelay, 5) {

    companion object {

        /**
         * The time source attribute for alchemy.
         */
        val Player.alchemyDelay by Attr.timeSource()
    }

    override fun execute() {
        val removeItems = Magic.checkRequirements(mob, type.level, type.requirements)
        if (removeItems != null) {
            var item = mob.inventory[inventoryIndex] ?: return
            item = item.withAmount(1)
            if (item.id == 995) {
                mob.sendMessage("Coins are already made of gold.")
                return
            }
            val lowAlch = type == AlchemyType.LOW
            val itemValue = item.itemDef.value
            val goldAmount = (if (lowAlch) itemValue * 0.4 else itemValue * 0.6).toInt()
            if (!item.itemDef.isTradeable || itemValue == 0) {
                mob.sendMessage("You cannot use alchemy on that item.")
                return
            }
            mob.inventory.remove(inventoryIndex, Item(item.id))
            mob.inventory.removeAll(removeItems)
            mob.magic.addExperience(type.xp)
            mob.animation(if (lowAlch) Animations.LOW_ALCHEMY else Animations.HIGH_ALCHEMY)
            mob.graphic(if (lowAlch) Graphic(112, 75) else Graphic(113, 75))
            mob.playSound(if (lowAlch) Sounds.LOW_ALCHEMY else Sounds.HIGH_ALCHEMY)

            mob.inventory.add(Item(995, if (goldAmount == 0) 1 else goldAmount))
            mob.tabs.show(TabIndex.MAGIC)
        }
    }
}