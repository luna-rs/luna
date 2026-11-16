package game.player.login.starterPackage

import api.predef.*
import game.player.login.firstLogin
import io.luna.game.event.impl.LoginEvent
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
 * Basic skilling tools for new human players.
 */
val playerStarterTools = listOf(
    item("Bronze axe"),
    item("Tinderbox"),
    item("Bronze pickaxe"),
    item("Small fishing net"))

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
    item("Bronze arrow", 150))

/**
 * Inventory starter items for bots. Skilling supplies are obtained through normal acquisition.
 */
val botInventoryStarter = listOf(
    item("Coins", 10_000),
    item("Iron scimitar"),
    item("Shortbow"),
    item("Staff"))

/**
 * Equipment starter items for bots.
 */
val botEquipmentStarter = listOf(item("Bronze arrow", 100))

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    if (plr.isBot) {
        plr.inventory.addAll(botInventoryStarter)
        plr.equipment.addAll(botEquipmentStarter)
    } else {
        plr.inventory.addAll(inventoryStarter)
        plr.equipment.addAll(equipmentStarter)
        plr.inventory.addAll(playerStarterTools)
    }
    plr.overlays.open(DesignPlayerInterface())
    plr.firstLogin = false
}

on(LoginEvent::class)
    .filter { plr.firstLogin }
    .then { firstLogin(plr) }
