package world.player.command.generic

import api.event.Matcher
import api.inter.QuestJournalInterface
import api.predef.*
import com.google.common.collect.HashMultimap
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.inter.NameInputInterface
import world.player.command.cmd

/**
 * A command that clears the inventory, bank, or equipment of a player.
 */
cmd("empty", RIGHTS_PLAYER) {
    plr.newDialogue().options(
            "Empty inventory.", { it.inventory.clear() },
            "Empty bank.", { it.bank.clear() },
            "Empty equipment.", { it.equipment.clear() }).open()
}

/**
 * A command that changes the password of a player.
 */
cmd("changepass", RIGHTS_PLAYER) {
    // TODO Is there a general purpose "enter string" packet that can be used instead?
    plr.interfaces.open(object : NameInputInterface() {
        override fun onNameInput(player: Player?, value: String?) {
            plr.password = value
            plr.sendMessage("Your password has been changed to $value.")
        }
    })
}

/**
 * A command that automatically generates a list of commands.
 */
cmd("commands", RIGHTS_PLAYER) {

    // Load commands from matcher.
    val commandMap = HashMultimap.create<PlayerRights, String>()
    for (key in Matcher.get<CommandEvent, CommandKey>().keys()) {
        commandMap.put(key.rights, key.name)
    }

    // Display them on the quest journal.
    val questJournal = QuestJournalInterface("@dbl@Luna ~ Commands")
    for (rights in PlayerRights.values()) {
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
 * A command that tell how many players are currently online.
 */
cmd("players", RIGHTS_PLAYER) {
    plr.sendMessage("Player online: ${world.players.size()}.")
}

