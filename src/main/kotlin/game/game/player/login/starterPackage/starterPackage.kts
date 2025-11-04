package game.player.login.starterPackage

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface
import game.player.login.firstLogin

/**
 * Inventory starter items.
 */
val inventoryStarter = listOf(
    item("Coins", 10_000),
    item("Air rune", 250),
    item("Water rune", 250),
    item("Fire rune", 250),
    item("Earth rune", 250),
    item("Mind rune", 500),
    item("Shortbow"))

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
    item("Iron full helm"),
    item("Iron platebody"),
    item("Iron platelegs"),
    item("Iron scimitar"),
    item("Iron kiteshield"),
    item("Amulet of power"),
    item("Iron boots"),
    item("Leather vambraces"),
    item("Ring of life"),
    Item(1019), // Black cape
    item("Bronze arrow", 750))

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    plr.inventory.addAll(inventoryStarter)
    plr.equipment.addAll(equipmentStarter)
    plr.overlays.open(DesignPlayerInterface())
    plr.firstLogin = false
}

on(LoginEvent::class)
    .filter { plr.firstLogin }
    .then { firstLogin(plr) }