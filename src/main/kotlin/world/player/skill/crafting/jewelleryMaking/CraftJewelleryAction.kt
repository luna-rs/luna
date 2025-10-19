package world.player.skill.crafting.jewelleryMaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.Animations
import world.player.Sounds
import world.player.skill.smithing.BarType

/**
 * An [InventoryAction] that enables crafting silver and gold jewellery for players.
 *
 * @author lare96
 */
class CraftJewelleryAction(plr: Player, private val barType: BarType,
                           private val jewellery: JewelleryItem, times: Int) :
    InventoryAction(plr, true, 3, times) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            barType != BarType.GOLD && barType != BarType.SILVER -> false
            mob.crafting.level < jewellery.level -> {
                mob.sendMessage("You need a Crafting level of ${jewellery.level} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.playSound(Sounds.SMELTING)
        mob.animation(Animations.SMELT)
        mob.crafting.addExperience(jewellery.xp)
    }

    override fun add(): List<Item> = listOf(jewellery.item)

    override fun remove(): List<Item> {
        val items = ArrayList<Item>()
        items += Item(barType.id) // Gold bar.
        if (jewellery.requiredItem != null) {
            items += jewellery.requiredItem
        }
        return items
    }
}