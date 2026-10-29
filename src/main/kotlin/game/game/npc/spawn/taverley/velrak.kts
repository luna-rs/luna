package game.npc.spawn.taverley

import api.predef.*
import game.obj.doors.TaverleyJailDoor
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Velrak the explorer, jailed by the Black Knights in Taverley Dungeon. Once freed he gives the dusty key.
 */
val VELRAK = 798

/**
 * The dusty key that Velrak gives.
 */
val DUSTY_KEY = TaverleyJailDoor.DUSTY_KEY_GATE.key

/**
 * [plr] asks Velrak where to explore, and is offered the dusty key.
 */
fun askWhereToExplore(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "So... do you know anywhere good to explore?")
        .npc(VELRAK, Expression.SAD, "Well, this dungeon was quite good to explore",
             "...until I got captured, anyway.", "I was given a key to an inner part of this dungeon",
             "by a mysterious cloaked stranger!")
        .npc(VELRAK, Expression.SAD, "It's rather tough for me to get that far into the",
             "dungeon however... I just keep getting captured!", "Would you like to give it a go?")
        .options("Yes please!", { takeKey(it) }, "No, it's too dangerous for me too.", {
            it.newDialogue()
                .player(Expression.CONFUSED, "No, it's too dangerous for me too.")
                .npc(VELRAK, Expression.NEUTRAL, "I don't blame you!")
                .open()
        })
        .open()
}

/**
 * Velrak hands [plr] the dusty key.
 */
fun takeKey(plr: Player) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Yes please!")
        .then {
            if (it.inventory.add(Item(DUSTY_KEY))) {
                it.newDialogue().text("Velrak reaches somewhere mysterious and passes you a key.").open()
            } else {
                it.sendMessage("You don't have enough inventory space.")
            }
        }
        .open()
}

npc1(VELRAK) {
    if (plr.inventory.contains(DUSTY_KEY)) {
        plr.newDialogue()
            .player(Expression.QUIZZICAL, "Are you still here?")
            .npc(VELRAK, Expression.SAD, "Yes... I'm still plucking up the courage",
                 "to run out past those Black Knights..")
            .open()
        return@npc1
    }
    plr.newDialogue()
        .npc(VELRAK, Expression.HAPPY, "Thank you for rescuing me!", "It isn't very comfy in this cell!")
        .options("So... do you know anywhere good to explore?", { askWhereToExplore(it) }, "Do I get a reward?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Do I get a reward? For freeing you and all...")
                .npc(VELRAK, Expression.SAD, "Well... not really... The Black Knights took all of my",
                     "stuff before throwing me in here to rot!")
                .open()
        })
        .open()
}
