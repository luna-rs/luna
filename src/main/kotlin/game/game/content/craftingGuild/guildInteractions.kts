package game.content.craftingGuild

import api.predef.*
import io.luna.game.model.mob.dialogue.Expression

object1(CraftingGuild.DOOR) {
    CraftingGuild.openDoor(plr, gameObject)
}

npc1(CraftingGuild.MASTER_CRAFTER) {
    plr.newDialogue()
        .npc(CraftingGuild.MASTER_CRAFTER, 575, "Yeah?")
        .player(Expression.HAPPY, "Hello.")
        .npc(CraftingGuild.MASTER_CRAFTER, 575, "Whassup?")
        .player(Expression.QUIZZICAL, "So... are you here to give crafting tips?")
        .npc(CraftingGuild.MASTER_CRAFTER, 575, "Dude, do I look like I wanna talk to you?")
        .player(Expression.NEUTRAL, "I suppose not.")
        .npc(CraftingGuild.MASTER_CRAFTER, 567, "Right on!")
        .open()
}
