package world.player.item.banking.regularBank

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.*

on(ServerLaunchEvent::class) {
    // Load all banking npcs and make them initialize dialogue
    Banking.loadBankingNpcs()
    for (id in Banking.bankingNpcs) {
        npc1(id, {bankerDialogue(plr, id)})
    }

    // Load all banking objects, make them open the bank.
    Banking.loadBankingObjects()
    for (id in Banking.bankingObjects) {
        object1(id) {
            val npc: Npc? = plr.localNpcs.firstOrNull { npc ->
                npc.position.isWithinDistance(gameObject.position, 1)
                && Banking.bankingNpcs.contains(npc.id)
            }

            if (npc != null) {
                bankerDialogue(plr, npc.id)
            } else {
                bankerDialogue(plr, Banking.DEFAULT_BANKER) // use default banker when no npc nearby
            }
        }
        if (objectDef(id).actions.contains("Use-quickly")) {
            object2(id) { plr.bank.open() }
        }
    }
}

fun bankerDialogue(plr: Player, bankerId: Int) {
    plr.newDialogue()
        .npc(bankerId, "Good day, how may I help you?")
        .options("I'd like to access my bank account, please.", {
                plr.newDialogue()
                    .player("I'd like to access my bank account, please.")
                    .then({ plr.bank.open() })
                    .open()
            },
            "What is this place?", {
                plr.newDialogue()
                    .player("What is this place?")
                    .npc(bankerId, "This is a branch of the bank of Runescape. We have", "branches in many towns.")
                    .options("And what do you do?", {
                        plr.newDialogue()
                            .player("And what do you do?")
                            .npc(bankerId, "We will look after your items and money for you.", "Leave your valuables with us if you want to keep them", "safe.")
                            .open()
                    }, "Didn't you used to be called the bank of Varrock?", {
                        plr.newDialogue()
                            .player("Didn't you used to be called the bank of Varrock?")
                            .npc(bankerId, "Yes we did, but people kept on coming into our", "branches outside of Varrock and telling us that our", "signs were wrong. They acted as if we didn't know", "what town we were in or something.")
                            .open()
                    })
                    .open()
            })
        .open();
}