package game.npc.spawn.shantayPass

import api.predef.*
import io.luna.game.model.mob.Npc

/**
 * The [Npc] id for the Shantay Guards that patrol the pass.
 */
val PATROL_GUARD_ID = 837

/**
 * The [Npc] id for the Shantay Guard that stands at the gate.
 */
val GATE_GUARD_ID = 838

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
            "I'd like to go into the desert please.", { ShantayPass.enterDesert(it, npc) },
            "Nothing thanks.",
            {
                plr.newDialogue()
                    .player("Nothing thanks.")
                    .open()
            }
        ).open()
}

object1(ShantayPass.DOORWAY) { ShantayPass.use(plr, gameObject) }
