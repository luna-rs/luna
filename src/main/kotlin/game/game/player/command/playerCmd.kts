package game.player.command

import api.event.Matcher
import api.predef.*
import com.google.common.collect.HashMultimap
import io.luna.Luna
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.inter.NameInputInterface
import game.player.QuestJournalInterface
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import game.skill.magic.teleportSpells.TeleportStyle

/**
 * A command that changes the password of a player.
 */
cmd("changepass") {
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
