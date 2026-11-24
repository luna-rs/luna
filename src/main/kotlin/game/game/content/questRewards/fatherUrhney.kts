package game.content.questRewards

import api.predef.*
import game.content.questRewards.QuestRewards.owns
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Father Urhney, in his hut in the Lumbridge Swamp.
 */
val FATHER_URHNEY = 458

/**
 * The amulet that lets players talk to ghosts.
 */
val AMULET_OF_GHOSTSPEAK = 552

npc1(FATHER_URHNEY) {
    // TODO The Restless Ghost: before the quest he only offers the first and last options, and while it is started
    //  "Father Aereck sent me to talk to you." is where he first hands over the amulet. Until the quest exists every
    //  player has finished it.
    val dialogue = plr.newDialogue().npc(FATHER_URHNEY, Expression.ANGRY, "Go away! I'm meditating!")
    if (owns(plr, AMULET_OF_GHOSTSPEAK)) {
        dialogue.options("Well, that's friendly.", { friendly(it) },
                         "I've come to repossess your house.", { repossess(it) })
    } else {
        dialogue.options("Well, that's friendly.", { friendly(it) },
                         "I've lost the amulet.", { lostAmulet(it) },
                         "I've come to repossess your house.", { repossess(it) })
    }
    dialogue.open()
}

/**
 * Urhney sends [plr] away.
 */
fun friendly(plr: Player) {
    plr.newDialogue()
        .player("Well, that's friendly.")
        .npc(FATHER_URHNEY, Expression.ANGRY, "I SAID go AWAY.")
        .player("Okay, okay...")
        .open()
}

/**
 * Urhney hands [plr] his spare amulet of ghostspeak.
 */
fun lostAmulet(plr: Player) {
    plr.newDialogue()
        .player(Expression.HAPPY, "I've lost the amulet.")
        .text("Father Urhney sighs.")
        .npc(FATHER_URHNEY, Expression.ANGRY, "How careless can you get?",
             "Those things aren't easy to come by you know!", "It's a good job I've got a spare.")
        .then {
            if (owns(it, AMULET_OF_GHOSTSPEAK) || !QuestRewards.give(it, AMULET_OF_GHOSTSPEAK)) return@then
            it.newDialogue()
                .text("Father Urhney hands you an amulet.")
                .npc(FATHER_URHNEY, Expression.ANGRY, "Be more careful this time.")
                .player("Okay, I'll try to be.")
                .open()
        }
        .open()
}

/**
 * [plr] tries to take Urhney's house.
 */
fun repossess(plr: Player) {
    plr.newDialogue()
        .player("I've come to repossess your house.")
        .npc(FATHER_URHNEY, Expression.SHOCKED, "Under what grounds???")
        .options("Repeated failure on mortgage payments.", {
            it.newDialogue()
                .player("Repeated failure on mortgage payments.")
                .npc(FATHER_URHNEY, Expression.ANGRY, "What?")
                .npc(FATHER_URHNEY, Expression.ANGRY, "But... I don't have a mortgage!", "I built this house myself!")
                .player("Sorry. I must have got the wrong address.", "All the houses look the same around here.")
                .open()
        }, "I don't know, I just wanted this house.", {
            it.newDialogue()
                .player(Expression.SAD, "I don't know. I just wanted this house...")
                .npc(FATHER_URHNEY, Expression.ANGRY, "Oh... go away and stop wasting my time!")
                .open()
        })
        .open()
}
