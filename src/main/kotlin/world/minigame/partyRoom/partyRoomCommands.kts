package world.minigame.partyRoom

import api.predef.*
import io.luna.game.model.mob.Player
import world.minigame.partyRoom.dropParty.DropPartyInterface
import world.minigame.partyRoom.dropParty.DropPartyOption

/**
 * Locks the lever.
 */
fun lockLeverCmd(plr: Player) {
    PartyRoom.locked = !PartyRoom.locked
    plr.newDialogue().empty("You have successfully ${if (PartyRoom.locked) "locked" else "unlocked"} the lever.").open()
}

/**
 * Locks the chest.
 */
fun lockChestCmd(plr: Player) {
    DropPartyOption.chest.locked = !DropPartyOption.chest.locked
    plr.newDialogue().empty("You have successfully ${if (PartyRoom.locked) "locked" else "unlocked"} the chest.")
}

// All-in-one admin command for handling basic party room functions.
cmd("party_room", RIGHTS_ADMIN) {
    plr.newDialogue().options(
            if (PartyRoom.locked) "Unlock lever" else "Lock lever", { lockLeverCmd(it) },
            if (DropPartyOption.chest.locked) "Lock chest" else "Unlock chest", { lockChestCmd(it) },
            "Pull lever", { PartyRoom.pullLever(it, null) },
            "Open chest", { plr.interfaces.open(DropPartyInterface()) }
    ).open()
}