package world.player.command.generic

import api.event.Matcher
import api.predef.*
import com.google.common.collect.HashMultimap
import io.luna.Luna
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.inter.NameInputInterface
import world.player.QuestJournalInterface
import world.player.skill.magic.Magic.teleport
import world.player.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import world.player.skill.magic.teleportSpells.TeleportStyle

/**
 * A command that clears the inventory, bank, or equipment of a player.
 */
cmd("empty") {
    plr.newDialogue().options(
        "Empty inventory.", { it.inventory.clear() },
        "Empty bank.", { it.bank.clear() },
        "Empty equipment.", { it.equipment.clear() }).open()
}


fun sendDialogue1(plr: Player) {
    plr.newDialogue().options("Mining", { sendMiningOption(it) },
                              "Smithing", { sendSmithingOption(it) },
                              "Fishing", { sendFishingOption(it) },
                              "Woodcutting", { sendWoodcuttingOption(it) },
                              "Next", { sendDialogue2(it) })

}

fun sendDialogue2(plr: Player) {
    plr.newDialogue().options(
        "Crafting", { },
        "Thieving", {},
        "Agility", {},
        "Previous", { sendDialogue1(it) },
        "Next", { sendDialogue3(it) }
    )
}

fun sendDialogue3(plr: Player) {
    plr.newDialogue().options(
        "Runecrafting", {},
        "Farming", {},
        "Slayer", {},
        "Previous", { sendDialogue2(it) }
    )
}

fun sendMiningOption(plr: Player) {

}

fun sendSmithingOption(plr: Player) {

}

fun sendFishingOption(plr: Player) {

}

fun sendWoodcuttingOption(plr: Player) {

}

fun sendCraftingOption(plr: Player) {

}

fun sendThievingOption(plr: Player) {

}

fun sendAgilityOption(plr: Player) {

}
// TODO range at home, anvil at home, furnace,
cmd("teleport") {
    sendDialogue1(plr)
}

/**
 * A command that changes the password of a player.
 */
cmd("changepass") {
    // TODO Is there a general purpose "enter string" packet that can be used instead?
    plr.interfaces.open(object : NameInputInterface() {
        override fun onNameInput(player: Player?, value: String?) {
            plr.password = value
            plr.sendMessage("Your password has been changed to $value.")
            plr.save()
        }
    })
}

/**
 * A command that automatically generates a list of commands.
 */
cmd("commands") {

    // Load commands from matcher.
    val commandMap = HashMultimap.create<PlayerRights, String>()
    for (key in Matcher.get<CommandEvent, CommandKey>().keys()) {
        commandMap.put(key.rights, key.name)
    }

    // Display them on the quest journal.
    val questJournal = QuestJournalInterface("@dbl@Luna ~ Commands")
    for (rights in PlayerRights.values()) {
        if (rights > plr.rights) {
            break
        }
        questJournal.addLine("@dbl@${rights.formattedName} commands")

        for (name in commandMap[rights]) {
            questJournal.addLine("::$name")
        }
        questJournal.newLine()
    }

    // And finally, open the quest journal!
    plr.interfaces.open(questJournal)
}

/**
 * A command that tells how many players are currently online.
 */
cmd("players") {
    plr.sendMessage("Players online: ${world.players.size()}.")
}

/**
 * A command that teleports the player home.
 */
cmd("home") {
    if (plr.teleportDelay.ready(2)) {
        plr.teleport(Luna.settings().game().startingPosition(), TeleportStyle.REGULAR)
    }
}
