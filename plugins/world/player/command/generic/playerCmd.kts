import api.event.Matcher
import api.inter.QuestJournalInterface
import api.predef.*
import com.google.common.collect.HashMultimap
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.PlayerRights

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
    for (rights in PlayerRights.ALL) {
        questJournal.addLine("@dbl@${rights.formattedName} commands")

        for (name in commandMap[rights]) {
            questJournal.addLine("::$name")
        }
        questJournal.newLine()
    }

    // And finally, open the quest journal!
    plr.interfaces.open(questJournal)
}