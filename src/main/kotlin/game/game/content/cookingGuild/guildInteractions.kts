package game.content.cookingGuild

import api.predef.*

object1(CookingGuild.DOOR) { CookingGuild.openDoor(plr, gameObject) }

npc1(CookingGuild.HEAD_CHEF) {
    plr.newDialogue().npc(CookingGuild.HEAD_CHEF, 588,
        "Hello, welcome to the Cooking Guild.",
        "Only accomplished chefs and cooks are allowed in here.",
        "Feel free to use any of our facilities.").open()
}
