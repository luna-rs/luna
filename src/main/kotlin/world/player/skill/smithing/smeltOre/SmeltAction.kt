package world.player.skill.smithing.smeltOre

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.Animations
import world.player.Sounds
import world.player.skill.smithing.BarType

/**
 * An [InventoryAction] implementation that handles the smelting action.
 */
class SmeltAction(plr: Player, val barType: BarType, times: Int) : InventoryAction(plr, true, 3, times) {

    override fun execute() {
        val wearingGoldsmithGauntlet = mob.equipment.computeIdForIndex(Equipment.HANDS).orElse(-1) == 776
        val xp = if (wearingGoldsmithGauntlet) barType.xp * 2.5 else barType.xp

        mob.playSound(Sounds.SMELTING)
        mob.animation(Animations.SMELT)
        if (currentAdd.isNotEmpty()) {
            // Only add XP if we're getting a bar (for Iron ore).
            mob.smithing.addExperience(xp)
            mob.sendMessage("You smelt the ore to create ${addArticle(itemName(barType.id))}.")
        }
    }

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is SmeltAction -> barType == other.barType
            else -> false
        }

    override fun add(): List<Item> {
        val barList = arrayListOf(Item(barType.id))
        if (barType == BarType.IRON) {
            // Iron has 50% chance of actually getting an ore.
            return if (rand().nextBoolean()) barList else emptyList()
        }
        return barList
    }

    override fun remove(): List<Item> = barType.oreRequired
}