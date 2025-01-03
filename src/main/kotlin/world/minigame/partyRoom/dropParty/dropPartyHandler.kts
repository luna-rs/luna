package world.minigame.partyRoom.dropParty

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.*
import io.luna.game.model.EntityState
import io.luna.game.model.item.Inventory
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import world.minigame.partyRoom.dropParty.DropPartyOption.BalloonObject
import world.minigame.partyRoom.dropParty.DropPartyOption.depositItems

/**
 * Deposits an item into the party chest.
 */
fun deposit(plr: Player, event: WidgetItemClickEvent, amount: Int) {
    val def = itemDef(event.itemId)
    var newAmount = amount
    if (amount > 8 && !def.isStackable) {
        newAmount = 8
    }

    if(!def.isTradeable) {
        plr.sendMessage("Untradeable items cannot be deposited here.")
        return
    }
    val invAmt = plr.inventory.computeAmountForId(event.itemId)
    val finalAmt = if (newAmount > invAmt) invAmt else newAmount

    val removeItem = Item(event.itemId, finalAmt)
    if(!plr.depositItems.hasSpaceFor(removeItem)) {
        plr.sendMessage("The party room chest does not have enough space for you to deposit this.")
        return
    }
    if (plr.inventory.remove(removeItem)) {
        plr.depositItems.add(removeItem)
    }
}

/**
 * Withdraws an item from the party chest.
 */
fun withdraw(plr: Player, event: WidgetItemClickEvent, amount: Int) {
    val chestAmt = plr.depositItems.computeAmountForId(event.itemId)
    val finalAmt = if (amount > chestAmt) chestAmt else amount
    val removeItem = Item(event.itemId, finalAmt)
    if(!plr.inventory.hasSpaceFor(removeItem)) {
        plr.sendMessage(Inventory.INVENTORY_FULL_MESSAGE)
        return
    }
    if (plr.depositItems.remove(removeItem)) {
        plr.inventory.add(removeItem)
    }
}

// Balloon stomping.
DropPartyOption.BALLOON_IDS.forEach {
    object1(it) {
        val balloon = gameObject as? BalloonObject
        if (balloon != null && gameObject.state == EntityState.ACTIVE) {
            balloon.stomp(plr)
        }
    }
}

// Confirm items
button(2246) {
    if (plr.interfaces.isOpen(DropPartyInterface::class)) {
        if (plr.depositItems.size() == 0) {
            plr.sendMessage("You do not have any items to deposit.")
        } else {
            DropPartyOption.chest.items.addAll(plr.depositItems)
            plr.depositItems.clear()
            for (otherPlr in world.players) {
                if (otherPlr.interfaces.isOpen(DropPartyInterface::class)) {
                    DropPartyOption.chest.items.refreshPrimary(otherPlr)
                }
            }
        }
    }
}

// Open the drop party chest interface.
object1(2417) { plr.interfaces.open(DropPartyInterface()) }

// Deposit
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 5064 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { deposit(plr, this, 1) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 5064 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { deposit(plr, this, 5) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 5064 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { deposit(plr, this, 10) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 5064 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { deposit(plr, this, plr.inventory.computeAmountForId(itemId)) }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 5064 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) {
                var count = plr.inventory.computeAmountForId(itemId)
                count = if (value > count) count else value
                deposit(plr, this@then, count)
            }
        })
    }

// Withdraw
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 2274 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { withdraw(plr, this, 1) }

on(WidgetItemSecondClickEvent::class)
    .filter { widgetId == 2274 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { withdraw(plr, this, 5) }

on(WidgetItemThirdClickEvent::class)
    .filter { widgetId == 2274 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { withdraw(plr, this, 10) }

on(WidgetItemFourthClickEvent::class)
    .filter { widgetId == 2274 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then { withdraw(plr, this, plr.depositItems.computeAmountForId(itemId)) }

on(WidgetItemFifthClickEvent::class)
    .filter { widgetId == 2274 && plr.interfaces.isOpen(DropPartyInterface::class) }
    .then {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player, value: Int) {
                var count = plr.depositItems.computeAmountForId(itemId)
                count = if (value > count) count else value
                withdraw(plr, this@then, count)
            }
        })
    }