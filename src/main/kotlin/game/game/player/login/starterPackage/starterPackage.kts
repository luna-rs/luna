package game.player.login.starterPackage

import api.predef.*
import game.player.login.firstLogin
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface

/**
 * Inventory starter items.
 */
val starter = listOf(
    item("Coins", 10_000),
    item("Iron scimitar"),
    item("Shortbow"),
    item("Bronze arrow", 100),
    item("Staff"))

/**
 * Called when the player logs in for the first time.
 */
fun firstLogin(plr: Player) {
    plr.inventory.addAll(starter)
    plr.overlays.open(DesignPlayerInterface())
    plr.firstLogin = false
}

on(LoginEvent::class)
    .filter { plr.firstLogin }
    .then { firstLogin(plr) }
