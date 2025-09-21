package world.player.item.jewelleryTeleport

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.util.StringUtils
import world.player.skill.magic.Magic.teleport


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
    plr.teleport(location) { plr.sendMessage("You teleport to ${StringUtils.capitalize(name)}.") }
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

for (jewellery in TeleportJewellery.VALUES) {
    for (index in 0 until jewellery.items.size) {
        item4(jewellery.items[index]) { rub(plr, this, index, jewellery) }
    }
}
