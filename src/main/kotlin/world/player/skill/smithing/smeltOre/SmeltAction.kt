package world.player.skill.smithing.smeltOre

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.Animations
import world.player.Sounds
import world.player.skill.smithing.BarType

/**
 * An [InventoryAction] implementation that handles the smelting action.
 *
 * @author lare96
 */
class SmeltAction(plr: Player, val barType: BarType, times: Int) : InventoryAction(plr, true, 3, times) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.smithing.level < barType.level -> {
                mob.sendMessage("You need a Smithing level of ${barType.level}.")
                false
            }

            else -> true
        }

    override fun execute() {
        val wearingGoldsmithGauntlet = mob.equipment.computeIdForIndex(Equipment.HANDS).orElse(-1) == 776
        val xp = if (wearingGoldsmithGauntlet) barType.xp * 2.5 else barType.xp

        mob.playSound(Sounds.SMELTING)
        mob.animation(Animations.SMELT)
        if (currentAdd.isNotEmpty()) { // Only add XP if we're getting a bar (for Iron ore).
            val oreRequired = barType.oreRequired
            val baseOre = name(oreRequired.first).lowercase()
            val secondOre = if(oreRequired.second == null) null else name(oreRequired.second!!).lowercase()
            val oreMessage = if (secondOre == null) baseOre else "$baseOre and $secondOre together"
            mob.smithing.addExperience(xp)
            mob.sendMessage("You smelt the $oreMessage in the furnace.")
            mob.sendMessage("You retrieve a bar of ${barType.lowercaseName}.")
        }
    }

    override fun add(): List<Item> {
        val barList = arrayListOf(Item(barType.id))
        if (barType == BarType.IRON) {
            // Iron has 50% chance of actually getting an ore.
            return if (rand().nextBoolean()) barList else emptyList()
        }
        return barList
    }

    override fun remove(): List<Item> = barType.oreList
}