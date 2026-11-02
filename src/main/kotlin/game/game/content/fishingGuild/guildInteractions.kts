package game.content.fishingGuild

import api.predef.*

object1(FishingGuild.DOOR) {
    FishingGuild.openDoor(plr, gameObject)
}

npc1(FishingGuild.MASTER_FISHER) {
    if (plr.fishing.level < FishingGuild.REQUIRED_LEVEL) {
        plr.newDialogue().npc(FishingGuild.MASTER_FISHER,
            "Hello, I'm afraid only the top fishers are allowed",
            "to use our premier fishing facilities.").open()
    } else {
        plr.newDialogue().npc(FishingGuild.MASTER_FISHER,
            "Hello, welcome to the Fishing Guild. Please feel",
            "free to make use of any of our facilities.").open()
    }
}
