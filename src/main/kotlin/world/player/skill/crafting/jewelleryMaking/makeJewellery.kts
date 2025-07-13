package world.player.skill.crafting.jewelleryMaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.smithing.BarType
import world.player.skill.smithing.Smithing

/**
 * The symbol.
 */
val symbol = SilverJewelleryTable.SARADOMIN_SYMBOL.jewelleryItem.item.id

/**
 * The emblem.
 */
val emblem = SilverJewelleryTable.ZAMORAK_SYMBOL.jewelleryItem.item.id

/**
 * The string id.
 */
val BALL_OF_WOOL = 1759

/**
 * All unstrung items to strung items.
 */
val STRING = mapOf(
    1673 to 1692,
    1675 to 1694,
    1677 to 1696,
    1679 to 1698,
    1681 to 1700,
    1683 to 1702,
    6579 to 6581,
    1714 to 1716,
    1720 to 1722
)

/**
 * Attempts to make a piece of gold jewellery.
 */
fun makeGoldJewellery(plr: Player, itemId: Int, times: Int) {
    val jewelleryItem = GoldJewelleryTable.ID_TO_JEWELLERY[itemId]
    if (jewelleryItem != null) {
        plr.submitAction(CraftJewelleryAction(plr, BarType.GOLD, jewelleryItem, times))
    }
}

/**
 * Attempts to make a piece of silver jewellery.
 */
fun makeSilverJewellery(plr: Player, itemId: Int, times: Int) {
    val table = SilverJewelleryTable.ID_TO_TABLE[itemId]
    if (table != null) {
        plr.submitAction(CraftJewelleryAction(plr, BarType.SILVER, table.jewelleryItem, times))
    } else if (SilverJewelleryTable.MOULDS.contains(itemId)) {
        // todo https://github.com/luna-rs/luna/issues/361
        plr.sendMessage("You need ${addArticle(itemId)} to make this.")
    }
}

/**
 * Opens the gold jewellery interface.
 */
fun openGold(plr: Player): Boolean {
    val hasMould = plr.inventory.any { it != null && GoldJewelleryTable.MOULDS.contains(it.id) }
    if (hasMould) {
        plr.interfaces.open(GoldJewelleryInterface())
        return true
    }
    return false
}

/**
 * Opens the silver jewellery interface.
 */
fun openSilver(plr: Player): Boolean {
    val hasMould = plr.inventory.any { it != null && SilverJewelleryTable.MOULDS.contains(it.id) }
    if (hasMould) {
        plr.interfaces.open(SilverJewelleryInterface())
        return true
    }
    return false
}

/**
 * Opens the make <x> interface for stringing jewellery.
 */
fun stringJewellery(plr: Player, usedId: Int, targetId: Int) {
    val newId = run {
        val used = STRING[usedId]
        if (used != null) {
            return@run used
        }
        return@run STRING[targetId]
    }
    if (newId != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(newId) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) {
                plr.submitAction(StringJewelleryAction(plr, forAmount, usedId, targetId, newId))
            }
        })
    }
}

/* Furnace interactions. */
for (furnaceId in Smithing.FURNACE_OBJECTS) {
    // Use mould item on furnace.
    for (table in GoldJewelleryTable.VALUES) {
        useItem(table.mouldId).onObject(furnaceId) { openGold(plr) }
    }
    for (table in SilverJewelleryTable.VALUES) {
        useItem(table.mouldId).onObject(furnaceId) { openSilver(plr) }
    }

    // Use bar item on furnace.
    useItem(2357).onObject(furnaceId) { openGold(plr) }
    useItem(2355).onObject(furnaceId) { openSilver(plr) }

    // Click furnace directly.
    object2(furnaceId) {
        if (!openSilver(plr)) {
            openGold(plr)
        }
    }
}

/* Interactions for gold jewelery. */
on(WidgetItemFirstClickEvent::class).filter { plr.interfaces.isOpen(GoldJewelleryInterface::class) }
    .then { makeGoldJewellery(plr, itemId, 1) }

on(WidgetItemClickEvent.WidgetItemSecondClickEvent::class).filter { plr.interfaces.isOpen(GoldJewelleryInterface::class) }
    .then { makeGoldJewellery(plr, itemId, 5) }

on(WidgetItemClickEvent.WidgetItemThirdClickEvent::class).filter { plr.interfaces.isOpen(GoldJewelleryInterface::class) }
    .then { makeGoldJewellery(plr, itemId, 10) }

/* Interactions for silver jewelery. */
on(WidgetItemFirstClickEvent::class).filter { plr.interfaces.isOpen(SilverJewelleryInterface::class) }
    .then { makeSilverJewellery(plr, itemId, 1) }

on(WidgetItemClickEvent.WidgetItemSecondClickEvent::class).filter { plr.interfaces.isOpen(SilverJewelleryInterface::class) }
    .then { makeSilverJewellery(plr, itemId, 5) }

on(WidgetItemClickEvent.WidgetItemThirdClickEvent::class).filter { plr.interfaces.isOpen(SilverJewelleryInterface::class) }
    .then { makeSilverJewellery(plr, itemId, 10) }

/* String symbols, emblems, and amulets. */
useItem(BALL_OF_WOOL).onItem(emblem) { stringJewellery(plr, usedItemId, targetItemId) }
useItem(BALL_OF_WOOL).onItem(symbol) { stringJewellery(plr, usedItemId, targetItemId) }

for (jewellery in GoldJewelleryTable.AMULETS.jewelleryItems) {
    useItem(BALL_OF_WOOL).onItem(jewellery.item.id) { stringJewellery(plr, usedItemId, targetItemId) }
}