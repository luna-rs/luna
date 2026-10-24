package game.content.miningGuild

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Opens the guild dwarf's repeatable conversation choices.
 */
fun choices(plr: Player) {
    plr.newDialogue().options("What have you got in the guild?", {
        val dialogue = plr.newDialogue()
            .player("What have you got in the guild?")
            .npc(MiningGuild.DWARF, "Ooh, it's WONDERFUL! There are lots of coal rocks,",
                 "and even a few mithril rocks in the guild, all",
                 "exclusively for people with at least level 60 mining.")
            .npc(MiningGuild.DWARF, "There's no better mining site anywhere near here.")
        if (plr.mining.level >= 60) {
            dialogue.player("It's a good thing I have level ${plr.mining.level} Mining.")
                .npc(MiningGuild.DWARF, "Yes, that's amazing! Did you want anything else?")
        } else {
            dialogue.player("So you won't let me go in there?")
                .npc(MiningGuild.DWARF, "Sorry, but rules are rules.", "Go do some more training first.",
                     "Can I help you with anything else?")
        }
        dialogue.then { choices(it) }.open()
    }, "No thanks, I'm fine.", {
        plr.newDialogue().player("No thanks, I'm fine.").open()
    }).open()
}

npc1(MiningGuild.DWARF) {
    plr.newDialogue()
        .npc(MiningGuild.DWARF, "Welcome to the Mining Guild. Can I help you with anything?")
        .then { choices(it) }.open()
}

object1(MiningGuild.DOOR) {
    MiningGuild.openDoor(plr, gameObject)
}
