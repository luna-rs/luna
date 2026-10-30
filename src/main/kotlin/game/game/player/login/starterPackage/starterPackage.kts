package game.player.login.starterPackage

import api.predef.*
import game.player.login.firstLogin
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.crafting.jewelleryMaking.SilverJewelleryTable
import game.skill.farming.Farming
import game.skill.fishing.catchFish.Tool
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface

/**
 * Inventory starter items.
 */
val inventoryStarter = listOf(
    item("Coins", 25_000),
    item("Iron scimitar"),
    item("Shortbow"))

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
    item("Bronze arrow", 150))

/**
 * Bank starter items. Only applicable to [Bot] types.
 */
val bankStarter = listOf(
    item("Anti-dragon shield", 1),
    item("Air rune", 250),
    item("Water rune", 250),
    item("Fire rune", 250),
    item("Earth rune", 250),
    item("Mind rune", 500),
    item("Cake", 100),
    item("Chisel", 5),
    item("Knife", 5),
    item("Needle", 5),
    item("Thread", 500),
    item("Bronze pickaxe", 5),
    item("Bronze axe", 5),
    item("Hammer", 5),
    item("Tinderbox", 5),
    item("Fishing bait", 500),
    item("Feather", 250)
)

/**
 * All farming bot starter items.
 */
val farmingItems = setOf(Farming.RAKE, Farming.SPADE, Farming.SEED_DIBBER, Farming.SECATEURS, Farming.EMPTY_WATERING_CAN, Farming.GARDENING_TROWEL).map { Item(it) }

/**
 * Adds starter items specific to bots.
 */
fun addBotItems(plr: Player) {
    plr.bank.addAll(bankStarter)
    Tool.ALL_IDS.forEach { plr.bank.add(Item(it)) }
    SilverJewelleryTable.MOULDS.forEach { plr.bank.add(Item(it)) }
    GoldJewelleryTable.MOULDS.forEach { plr.bank.add(Item(it)) }
    Altar.entries.forEach { plr.bank.add(Item(it.talisman)) }
    plr.bank.addAll(farmingItems)
}

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    plr.inventory.addAll(inventoryStarter)
    plr.equipment.addAll(equipmentStarter)
    if (plr.isBot) {
        addBotItems(plr)
    }
    plr.overlays.open(DesignPlayerInterface())
    plr.firstLogin = false
}

on(LoginEvent::class)
    .filter { plr.firstLogin }
    .then { firstLogin(plr) }