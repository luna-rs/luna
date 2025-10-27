package engine.bank

import api.predef.*
import engine.bank.Banking
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player

/**
 * Load all banking NPC dialogues and bank object actions.
 */
on(ServerLaunchEvent::class) {

    // Load all banking npcs and make them initialize dialogues.
    Banking.loadBankingNpcs()
    for (id in Banking.bankingNpcs) {
        npc1(id) { openDialogue(plr, id) }
    }

    // Load all banking objects, make them open the bank.
    Banking.loadBankingObjects()
    for (id in Banking.bankingObjects) {

        // Add listeners for "Use"
        object1(id) {
            val npc = plr.localNpcs.stream().filter { Banking.bankingNpcs.contains(it.id) }
                .filter { it.position.isWithinDistance(gameObject.position, 1) }.findFirst()
            if (npc.isPresent) {
                openDialogue(plr, npc.get().id)
            } else {
                // Use default banker if no NPC is nearby.
                openDialogue(plr, Banking.DEFAULT_BANKER)
            }
        }

        // Add listeners for "Use-quickly"
        if (objectDef(id).actions.contains("Use-quickly")) {
            object2(id) { plr.bank.open() }
        }
    }
}

/**
 * Opens the NPC main banker dialogue.
 */
fun openDialogue(plr: Player, id: Int) {
    plr.newDialogue()
        .npc(id, "Good day, how may I help you?")
        .options("I'd like to access my bank account, please.", { accessAccountDialogue(it) },
                 "What is this place?", { areaInfoDialogue(it, id) },
                 "Didn't you used to be called the bank of Varrock?", { oldNameInfoDialogue(it, id) }).open()
}

/**
 * Opens the sub-dialogue for accessing the bank.
 */
fun accessAccountDialogue(plr: Player) {
    plr.newDialogue()
        .player("I'd like to access my bank account, please.")
        .then { plr.bank.open() }
        .open()
}

/**
 * Opens the sub-dialogue for learning about the area.
 */
fun areaInfoDialogue(plr: Player, id: Int) {
    plr.newDialogue()
        .player("What is this place?")
        .npc(id, "This is a branch of the bank of Runescape. We have", "branches in many towns.")
        .options("And what do you do?", {
            plr.newDialogue()
                .player("And what do you do?")
                .npc(id, "We will look after your items and money for you.",
                     "Leave your valuables with us if you want to keep them",
                     "safe.").open()
        }, "Didn't you used to be called the bank of Varrock?", {
                     plr.newDialogue()
                         .player("Didn't you used to be called the bank of Varrock?")
                         .npc(id, "Yes we did, but people kept on coming into our",
                              "branches outside of Varrock and telling us that our",
                              "signs were wrong. They acted as if we didn't know",
                              "what town we were in or something.").open()
                 }).open()
}

/**
 * Opens the sub-dialogue for learning about the previous name.
 */
fun oldNameInfoDialogue(plr: Player, id: Int) {
    plr.newDialogue()
        .player("Didn't you used to be called the bank of Varrock?")
        .npc(id, "Yes we did, but people kept on coming into our",
             "branches outside of Varrock and telling us that our",
             "signs were wrong. They acted as if we didn't know",
             "what town we were in or something.")
        .open()
}