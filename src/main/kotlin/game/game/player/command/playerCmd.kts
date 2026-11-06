package game.player.command

import api.bot.isRealBot

import api.bot.zone.Zone
import api.event.Matcher
import api.predef.*
import com.google.common.collect.HashMultimap
import game.player.QuestJournalInterface
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.Luna
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerCredentials
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.overlay.TextInput
import java.lang.ref.WeakReference


//todo temporary until ship travel,etc. are added
cmd("piscatoris") {
    if ((plr.isBot || plr.rights >= RIGHTS_ADMIN) && !plr.status.isTeleBlocked() && plr.teleportDelay.ready(2)) {
        plr.teleport(Zone.PISCATORIS_FISHING_COLONY.anchor, TeleportStyle.REGULAR)
    }
}

/**
 * A command that changes the password of a player.
 */
cmd("changepass") {
    plr.overlays.open(object : TextInput() {
        override fun input(player: Player, value: String) {
            val password = PlayerCredentials.normalizePassword(value)
            if (password.isEmpty() || password.length > 20) {
                player.sendMessage("Your password must be between 1 and 20 characters.")
                return
            }
            // Text prompts carry letters and digits only; any other character arrives as '_'.
            if ('_' in password) {
                player.sendMessage("Your new password can only use letters and numbers.")
                return
            }
            val target = WeakReference(player)
            world.loginService.changePassword(player, password).whenCompleteAsync({ changed, error ->
                val current = target.get()
                if (current != null && current.state == EntityState.ACTIVE &&
                    world.playerMap[current.username] === current) {
                    if (error != null) {
                        current.sendMessage("Unable to confirm your password was saved. Please try again.")
                    } else if (changed == true) {
                        current.sendMessage("Your password has been changed.")
                    }
                }
            }, gameService.gameExecutor)
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
    }

    // And finally, open the quest journal!
    plr.overlays.open(questJournal)
}

/**
 * A command that tells how many players are currently online.
 */
cmd("players") {
    plr.sendMessage("Real players online: ${world.players.count { it != null && !it.isRealBot() }}.")
    plr.sendMessage("Bots online: ${world.bots.onlineCount}.")
}

/**
 * A command that teleports the player home.
 */
cmd("home") {
    if (!plr.status.isTeleBlocked() && plr.teleportDelay.ready(2)) {
        plr.teleport(Luna.settings().game().startingPosition(), TeleportStyle.REGULAR)
    }
}
