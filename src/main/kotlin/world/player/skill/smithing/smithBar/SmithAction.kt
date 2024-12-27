package world.player.skill.smithing.smithBar

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.Animations
import world.player.Sounds
import world.player.skill.smithing.Smithing

/**
 * An [InventoryAction] implementation that handles the smithing action.
 */
class SmithAction(plr: Player, val table: SmithingTable, val makeItem: SmithingItem, times: Int) :
    InventoryAction(plr, true, 5, times) {

    /**
     * The item that will be made.
     */
    private val item = makeItem.item

    /**
     * The bar that will be used to make the item.
     */
    private val barType = makeItem.barType

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is SmithAction -> makeItem.item.id == other.makeItem.item.id
            else -> false
        }

    override fun executeIf(start: Boolean): Boolean = when {
        mob.smithing.level < makeItem.level -> {
            mob.sendMessage("You need a Smithing level of ${makeItem.level} to make this.")
            false
        }

        !mob.inventory.contains(Smithing.HAMMER) -> {
            mob.sendMessage("You need a hammer to smith items.")
            false
        }

        else -> true
    }

    override fun execute() {
        mob.playSound(Sounds.SMITHING)
        mob.animation(Animations.SMITH)
        mob.sendMessage("You make the ${makeItem.item.itemDef.name}.")
        mob.smithing.addExperience(barType.xp * table.bars)
    }

    override fun add(): MutableList<Item> = arrayListOf(item)
    override fun remove(): MutableList<Item> = arrayListOf(Item(barType.id, table.bars))
}