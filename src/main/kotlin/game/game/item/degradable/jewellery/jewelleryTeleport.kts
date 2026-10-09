package game.item.degradable.jewellery

import api.predef.*
import api.predef.ext.*
import game.skill.magic.Magic.teleport
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.util.StringUtils

/**
 * Invoked when the player initially rubs the jewellery. Forwards to [openDialogue].
 */
fun rub(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    val lastIndex = jewellery.items.size - 1
    if (index == lastIndex) {
        // Last index, prepare jewellery for crumble or send message.
        if (!jewellery.crumbles) {
            plr.sendMessage(jewellery.lastCharge)
            return
        }
    }

    plr.sendMessage(jewellery.rub)
    plr.lock()
    world.scheduleOnce(1) {
        plr.unlock()
        openDialogue(plr, event, index, jewellery)
    }
}

/**
 * Opens the dialogue options once the jewellery is rubbed. Potentially forwards to [teleport].
 */
fun openDialogue(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    val options = ArrayList<DialogueOption>(jewellery.destinations.size + 1)
    for (dest in jewellery.destinations) {
        options += DialogueOption(dest.first) { teleport(plr, dest, event, index, jewellery) }
    }
    options += DialogueOption("Nowhere") { plr.resetDialogues() }

    plr.newDialogue()
        .options(options)
        .open()
}

/**
 * Teleports the player and degrades jewellery once a teleport option is clicked. The final step in the script.
 */
fun teleport(plr: Player, destination: Pair<String, Position>, event: ItemClickEvent,
             index: Int, jewellery: TeleportJewellery) {
    val (name, location) = destination
    if (!holdsRubbedItem(plr, event, index, jewellery)) {
        return
    }
    plr.teleport(location) {
        plr.sendMessage("You teleport to ${StringUtils.capitalize(name)}.")
        useCharge(plr, event, index, jewellery)
    }
}

/**
 * Checks that the inventory slot that was rubbed still holds the same jewellery.
 */
fun holdsRubbedItem(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) =
    plr.inventory[event.index]?.id == jewellery.items[index]

/**
 * Replaces the rubbed jewellery with its next charge, or removes it when it crumbles. Only called once the teleport
 * has passed every check.
 */
fun useCharge(plr: Player, event: ItemClickEvent, index: Int, jewellery: TeleportJewellery) {
    if (!holdsRubbedItem(plr, event, index, jewellery)) {
        return
    }
    plr.inventory[event.index] = null

    val lastIndex = jewellery.items.size - 1
    if (lastIndex == index) {
        // We know for sure at this point jewellery will crumble.
        plr.sendMessage(jewellery.lastCharge)
        return
    }
    val nextId = jewellery.items[index + 1]
    plr.inventory[event.index] = Item(nextId)
}

for (jewellery in TeleportJewellery.values()) {
    for (index in 0 until jewellery.items.size) {
        item4(jewellery.items[index]) { rub(plr, this, index, jewellery) }
    }
}
