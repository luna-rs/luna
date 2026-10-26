package game.npc.spawn.falador

import api.predef.*

/**
 * The [Npc] ids for the gardeners working under Wyson in Falador park.
 */
val GARDENER_IDS = listOf(1217, 3234)

/*
 * Handles the Falador park gardeners' first npc option dialogue.
 */
for (id in GARDENER_IDS) {
    npc1(id) {
        plr.newDialogue()
            .player("Hello.")
            .npc(
                targetNpc.id,
                "Oi'm busy. If tha' wants owt, tha' can go find Wyson.",
                "He's ta boss 'round here. And,",
                "KEEP YE' TRAMPIN' FEET OFF MA'FLOWERS!"
            )
            .player("Right...")
            .open()
    }
}
