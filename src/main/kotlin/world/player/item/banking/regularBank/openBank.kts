package world.player.item.banking.regularBank

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.Player;

/**
 * A set of banking objects.
 */
val bankObjects: Set<Int> = hashSetOf(3193, 2213, 3095)

/**
 * A set of banking NPC's.
 */
val bankNpcs: Set<Int> = hashSetOf(494, 495, 496, 497, 498, 499)

/**
 * Open the banking dialogue or interface for objects.
 */
bankObjects.forEach {id ->
    object1(id) {
        bankingDialogue(plr)
    }
    object2(id) {
        plr.bank.open()
    }
}

/**
 * Open the banking dialogue or bank for npc's.
 */
bankNpcs.forEach { npcId ->
    npc1(npcId) {
        bankingDialogue(plr)
    }
    npc2(npcId) {
        plr.bank.open()
    }
}


fun bankingDialogue(plr: Player) {
    plr.newDialogue()
        .npc(494, Expression.SKEPTICAL, "Good day, how may I help you?")
        .options(
            "I'd like to access my bank account, please.", {
                plr.newDialogue()
                    .player("I'd like to access my bank account, please.")
                    .then { plr.bank.open() }
                    .open()
            },
            "What is this place?", {
                plr.newDialogue()
                    .player("What is this place?")
                    .npc(494, "This is a branch of the Bank of Runescape. We have", "branches in many towns.")
                    .options(
                        "And what do you do?", {
                            plr.newDialogue()
                                .player(Expression.SKEPTICAL, "And what do you do?")
                                .npc(
                                    494,
                                    "We look after your items and money for you.",
                                    "Leave your valuables with us if you want to keep them",
                                    "safe."
                                )
                                .open()
                        },
                        "Didn't you used to be called the Bank of Varrock?", {
                            plr.newDialogue()
                                .player(Expression.SKEPTICAL, "Didn't you used to be called the Bank of Varrock?")
                                .npc(
                                    494,
                                    "Yes we did, but people kept on coming into our",
                                    "branches outside of Varrock and telling us that our",
                                    "signs were wrong. They acted as if we didn't know",
                                    "what town we were in or something."
                                )
                                .open()
                        }
                    ).open()
            }
        ).open()
}