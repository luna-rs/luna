package game.player.login.starterPackage

import api.predef.*
import game.player.login.firstLogin
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.crafting.jewelleryMaking.SilverJewelleryTable
import game.skill.fishing.Tool
import game.skill.magic.Rune
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface

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
    item("Iron 2h sword"),
    item("Shortbow"))

/**
 * Equipment starter items.
 */
val equipmentStarter = listOf(
    item("Iron full helm"),
    item("Iron platebody"),
    item("Iron platelegs"),
    item("Iron longsword"),
    item("Iron kiteshield"),
    item("Amulet of strength"),
    item("Iron boots"),
    item("Leather vambraces"),
    item("Ring of life"),
    Item(1019), // Black cape
    item("Bronze arrow", 750))

/**
 * Bank starter items. Only applicable to [Bot] types.
 */
val bankStarter = listOf(
    item("Anti-dragon shield"),
    item("Staff of air"),
    item("Staff of water"),
    item("Staff of earth"),
    item("Staff of fire"),
    item("Chisel"),
    item("Knife"),
    item("Needle"),
    item("Thread", 10_000),
    item("Iron pickaxe"),
    item("Iron axe"),
    item("Hammer"),
    item("Tinderbox")
    // TODO Farming starter bot items.
)

/**
 * Adds starter items specific to bots.
 */
fun addBotItems(plr: Player) {
    plr.bank.addAll(bankStarter)
    Rune.ID_TO_RUNE.keys.forEach { plr.bank.add(Item(it, 500)) }
    Tool.ALL_IDS.forEach { plr.bank.add(Item(it)) }
    SilverJewelleryTable.MOULDS.forEach { plr.bank.add(Item(it)) }
    GoldJewelleryTable.MOULDS.forEach { plr.bank.add(Item(it)) }
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