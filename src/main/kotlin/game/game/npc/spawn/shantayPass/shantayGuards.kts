package game.npc.spawn.shantayPass

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * The [Npc] id for the Shantay Guards that patrol the pass.
 */
val PATROL_GUARD_ID = 837

/**
 * The [Npc] id for the Shantay Guard that stands at the gate.
 */
val GATE_GUARD_ID = 838

/**
 * The Shantay pass item ID.
 */
val SHANTAY_PASS = 1854

/**
 * The Shantay disclaimer item ID.
 */
val SHANTAY_DISCLAIMER = 1848

/**
 * The position on the desert side of the gate.
 */
val DESERT_SIDE = Position(3304, 3115)

/**
 * Takes [plr]'s Shantay pass and lets them through the gate into the desert. Players who have never been through
 * are also handed a disclaimer.
 */
fun passGate(plr: Player, npc: Npc) {
    plr.newDialogue()
        .npc(npc.id, "Can I see your Shantay Desert Pass please.")
        .give(Item(SHANTAY_PASS), "You hand over a Shantay Pass.", false)
        .player("Sure, here you go!")
        .then {
            if (it.inventory.remove(Item(SHANTAY_PASS))) {
                it.move(DESERT_SIDE)
                it.sendMessage("You go through the gate.")
                if (!it.inventory.contains(SHANTAY_DISCLAIMER)) {
                    it.giveItem(Item(SHANTAY_DISCLAIMER))
                    it.newDialogue()
                        .npc(
                            npc.id,
                            "Here, have a disclaimer... It means that Shantay isn't",
                            "responsible if you die in the desert."
                        )
                        .open()
                }
            }
        }
        .open()
}

/**
 * Opens the dialogue where [plr] asks the gate guard to let them into the desert.
 *
 * Players without a disclaimer are first shown the warning poster by the gate, and may change their mind.
 */
fun enterDesert(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("I'd like to go into the desert please.")
        .then {
            if (!it.inventory.contains(SHANTAY_PASS)) {
                it.newDialogue()
                    .npc(
                        npc.id,
                        "You need a Shantay pass to get through this gate. See",
                        "Shantay, he will sell you one for a very reasonable",
                        "price."
                    )
                    .open()
            } else if (it.inventory.contains(SHANTAY_DISCLAIMER)) {
                passGate(it, npc)
            } else {
                it.newDialogue()
                    .text("There is a large poster on the wall near the gateway.", "It reads:")
                    .text(
                        "The Desert is a VERY Dangerous place. Do not enter if",
                        "you are scared of dying. Beware of high temperatures,",
                        "sand storms, robbers, and slavers."
                    )
                    .text("That seems pretty scary!", "Are you sure you want to go through?")
                    .options(
                        "Yeah, that poster doesn't scare me!", { passGate(it, npc) },
                        "No, I'm having serious second thoughts now.", { it.overlays.closeWindows() }
                    )
                    .title("Go into Desert?")
                    .open()
            }
        }
        .open()
}

/*
 * Handles the patrolling Shantay Guards' first npc option dialogue.
 */
npc1(PATROL_GUARD_ID) {
    plr.newDialogue()
        .npc(
            targetNpc.id,
            "Go talk to Shantay. I'm on duty and I don't have time",
            "to talk to the likes of you!"
        )
        .text(
            "The guard seems quite bad tempered, probably from having",
            "to wear heavy armour in this intense heat."
        )
        .open()
}

/*
 * Handles the gate Shantay Guard's first npc option dialogue.
 *
 * The player can either ask to be let into the desert or end the conversation.
 */
npc1(GATE_GUARD_ID) {
    val npc = targetNpc
    plr.newDialogue()
        .npc(npc.id, "Hello there! What can I do for you?")
        .options(
            "I'd like to go into the desert please.", { enterDesert(it, npc) },
            "Nothing thanks.",
            {
                plr.newDialogue()
                    .player("Nothing thanks.")
                    .open()
            }
        ).open()
}
