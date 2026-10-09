package game.item.degradable.jewellery

import api.predef.*
import api.predef.ext.*
import game.item.degradable.jewellery.TeleportJewellery.JewelleryDestination
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * Invoked when the player initially rubs the jewellery. Opens [openDialogue] once the jewellery's menu delay passes.
 */
fun rub(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    plr.sendMessage(jewellery.rub)
    if (jewellery.menuDelay == 0) {
        openDialogue(plr, event, index, jewellery)
        return
    }
    plr.lock()
    world.scheduleOnce(jewellery.menuDelay) {
        plr.unlock()
        openDialogue(plr, event, index, jewellery)
    }
}

/**
 * Opens the destination menu once the jewellery is rubbed. Potentially forwards to [teleport].
 */
fun openDialogue(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    val options = ArrayList<DialogueOption>(jewellery.destinations.size + 1)
    for (dest in jewellery.destinations) {
        options += DialogueOption(dest.option) { teleport(plr, dest, event, index, jewellery) }
    }
    options += DialogueOption("Nowhere.") {
        plr.resetDialogues()
        jewellery.nowhere?.let { plr.sendMessage(it) }
    }

    plr.newDialogue()
        .options(options)
        .title(jewellery.title)
        .open()
}

/**
 * Teleports the player once a destination is picked. The charge is only used once the player lands.
 */
fun teleport(plr: Player, destination: JewelleryDestination, event: ItemClickEvent,
             index: Int, jewellery: TeleportJewellery) {
    if (!holdsRubbedItem(plr, event, index, jewellery)) {
        return
    }
    val chargesLeft = jewellery.items.size - index - 1
    val landing = findLandingTile(destination.centre, destination.radius)
    plr.teleport(landing, TeleportStyle.REGULAR, jewellery.maxWildernessLevel, onLand = {
        useCharge(plr, event, index, jewellery)
        jewellery.landMessage(chargesLeft)?.let { plr.sendMessage(it) }
    }) {
        jewellery.castMessage(chargesLeft)?.let { plr.sendMessage(it) }
    }
}

/**
 * Checks that the inventory slot that was rubbed still holds the same jewellery.
 */
fun holdsRubbedItem(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) =
    plr.inventory[event.index]?.id == jewellery.items[index]

/**
 * Replaces the rubbed jewellery with its next charge, its empty form, or nothing when it crumbles.
 */
fun useCharge(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    if (!holdsRubbedItem(plr, event, index, jewellery)) {
        return
    }
    val nextId = jewellery.items.getOrNull(index + 1) ?: jewellery.emptyId
    plr.inventory[event.index] = if (nextId == null) null else Item(nextId)
}

for (jewellery in TeleportJewellery.values()) {
    for (index in 0 until jewellery.items.size) {
        item4(jewellery.items[index]) { rub(plr, this, index, jewellery) }
    }
    val emptyId = jewellery.emptyId
    val empty = jewellery.empty
    if (emptyId != null && empty != null) {
        item4(emptyId) { plr.sendMessage(empty) }
    }
}
