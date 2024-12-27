package world.player.skill.smithing.smithBar

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.mob.Player
import world.player.skill.smithing.BarType
import world.player.skill.smithing.Smithing

/**
 * Looks up the first [BarType] found in the player's inventory.
 */
fun lookupBar(player: Player): BarType? {
    for (item in player.inventory) {
        if (item != null) {
            val bar = BarType.ID_TO_BAR[item.id]
            /*if(bar == Bar.SLIVER || bar == Bar.GOLD) {
                continue
            }*/
            if (bar != null && player.smithing.level >= bar.level) {
                return bar
            }
        }
    }
    player.sendMessage("You don't have any metal bars which you have the required level to use.")
    return null
}

/**
 * Starts the [SmithAction] based on the supplied [id] and [amount] of actions.
 */
fun smith(plr: Player, id: Int, amount: Int) {
    val smithItem = SmithingTable.ID_TO_ITEM[id] ?: throw IllegalStateException("Invalid smithing item ID [$id]")
    val smithTable = SmithingTable.ID_TO_TABLE[id] ?: throw IllegalStateException("Invalid smithing item ID [$id]")
    plr.submitAction(SmithAction(plr, smithTable, smithItem, amount))
}

/* Intercept widget clicks to start smithing actions. */
on(WidgetItemFirstClickEvent::class)
    .filter { plr.interfaces.isOpen(SmithingInterface::class) }
    .then { smith(plr, itemId, 1) }

on(WidgetItemSecondClickEvent::class)
    .filter { plr.interfaces.isOpen(SmithingInterface::class) }
    .then { smith(plr, itemId, 5) }

on(WidgetItemThirdClickEvent::class)
    .filter { plr.interfaces.isOpen(SmithingInterface::class) }
    .then { smith(plr, itemId, 10) }

/* Intercept events to use hammer and bars on anvils. */
for (anvil in Smithing.ANVIL_OBJECTS) {
    useItem(Smithing.HAMMER).onObject(anvil) {
        val bar = lookupBar(plr)
        if (bar != null) {
            plr.interfaces.open(SmithingInterface(bar))
        }
    }
    for (bar in BarType.VALUES) {
        useItem(bar.id).onObject(anvil) { plr.interfaces.open(SmithingInterface(bar)) }
    }
}