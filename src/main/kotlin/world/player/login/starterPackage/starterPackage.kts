package world.player.login.starterPackage

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface
import world.player.login.firstLogin

/**
 * Inventory starter items.
 */
val inventoryStarter = listOf(
        Item(995, 10000), // Coins
        Item(556, 250), // Air runes
        Item(555, 250), // Water runes
        Item(554, 250), // Fire runes
        Item(557, 250), // Earth runes
        Item(558, 500), // Mind runes
        Item(841)) // Shortbow

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
        Item(1153), // Iron full helm
        Item(1115), // Iron platebody
        Item(1067), // Iron platelegs
        Item(1323), // Iron scimitar
        Item(1191), // Iron kiteshield
        Item(1731), // Amulet of power
        Item(4121), // Iron boots
        Item(1063), // Leather vambraces
        Item(2570), // Ring of life
        Item(1019), // Black cape
        Item(882, 750)) // Bronze arrows

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    plr.inventory.addAll(inventoryStarter)
    plr.equipment.addAll(equipmentStarter)
    plr.interfaces.open(DesignPlayerInterface())
    plr.firstLogin = false
}

on(LoginEvent::class)
    .filter { plr.firstLogin }
    .then { firstLogin(plr) }