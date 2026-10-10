package game.content.sailing

import api.predef.*
import game.content.sailing.Sailing.payFare
import game.content.sailing.Sailing.sail
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Captain Tobias, Seaman Lorris and Seaman Thresnor, who sail from Port Sarim to Karamja.
 */
val PORT_SARIM_SAILORS = listOf(376, 377, 378)

/**
 * Captain Barnaby, who sails from Ardougne to Brimhaven.
 */
val CAPTAIN_BARNABY = 381

/**
 * The customs officers at Musa Point and Brimhaven.
 */
val CUSTOMS_OFFICER = 380

/**
 * Karamjan rum, which customs officers confiscate.
 */
val KARAMJAN_RUM = 431

/**
 * Offers [plr] a trip along [route] for 30 coins.
 */
fun offerTrip(plr: Player, npc: Int, route: ShipRoute) {
    // TODO During Dragon Slayer, after Oziach, add "I'd rather go to Crandor Isle." first. The sailor answers "No I
    //  need to stay alive, I have a wife and family to support."
    plr.newDialogue()
        .npc(npc, "Do you want to go on a trip to ${route.place}?")
        .npc(npc, "The trip will cost you 30 coins.")
        .options("Yes please.", {
            it.newDialogue().player("Yes please.").then {
                if (payFare(it)) {
                    it.sendMessage("You pay the 30 coins...")
                    sail(it, route, "and board the ship.")
                }
            }.open()
        }, "No, thank you.", {
            it.newDialogue().player("No, thank you.").open()
        }).open()
}

for (id in PORT_SARIM_SAILORS) {
    npc1(id) { offerTrip(plr, targetNpc.id, ShipRoute.PORT_SARIM_TO_KARAMJA) }
}

npc1(CAPTAIN_BARNABY) { offerTrip(plr, targetNpc.id, ShipRoute.ARDOUGNE_TO_BRIMHAVEN) }

/**
 * Takes [plr]'s rum if they're carrying any. Returns `true` if they were.
 */
fun confiscateRum(plr: Player): Boolean {
    val rum = plr.inventory.computeAmountForId(KARAMJAN_RUM)
    if (rum == 0) {
        return false
    }
    plr.inventory.remove(Item(KARAMJAN_RUM, rum))
    plr.sendMessage("The customs officer confiscates your rum.")
    return true
}

/**
 * Asks [plr] for the 30 coin boarding charge, then sails them along [route].
 */
fun boardingCharge(plr: Player, route: ShipRoute) {
    plr.newDialogue().options("Ok.", {
        it.newDialogue().player("Ok.").then {
            if (payFare(it)) {
                sail(it, route, "You pay 30 coins and board the ship.")
            }
        }.open()
    }, "Oh, I'll not bother then.", {
        it.newDialogue().player("Oh, I'll not bother then.").open()
    }).open()
}

/**
 * Searches [plr] for rum before they can board.
 */
fun search(plr: Player, npc: Int, route: ShipRoute) {
    plr.newDialogue().player("Search away, I have nothing to hide.").then {
        if (it.inventory.computeAmountForId(KARAMJAN_RUM) > 0) {
            it.newDialogue()
                .npc(npc, "Aha, trying to smuggle rum are we?")
                .player("Umm... it's for personal use?")
                .then {
                    if (confiscateRum(it)) {
                        it.sendMessage("You will need to find some way to smuggle it off the island...")
                    }
                }.open()
        } else {
            // Said even when the player's inventory is empty.
            it.newDialogue()
                .npc(npc, "Well you've got some odd stuff, but it's all legal. Now",
                     "you need to pay a boarding charge of 30 coins.")
                .then { boardingCharge(it, route) }
                .open()
        }
    }.open()
}

/**
 * Refuses to be searched, so [plr] can't board.
 */
fun refuseSearch(plr: Player, npc: Int) {
    plr.newDialogue()
        .player("You're not putting your hands on my things!")
        .npc(npc, "You're not getting on this ship then.")
        .open()
}

/**
 * Asks to board the customs officer's ship, which sails along [route].
 */
fun askToBoard(plr: Player, npc: Int, route: ShipRoute) {
    // TODO After Pirate's Treasure starts the plantation job: if the player has rum, the officer says "Spot
    //  inspection. You don't mind do you?" and "Aha, trying to smuggle rum eh?", the player says "Umm... it's for
    //  personal use?", and the rum is confiscated. Otherwise the officer says "Hey, I know you, you work at the
    //  plantation." and "I don't think you'll try smuggling anything, you just need to pay a boarding charge of 30
    //  coins.", then asks for the boarding charge without a search.
    plr.newDialogue()
        .player("Can I journey on this ship?")
        .npc(npc, "You need to be searched before you can board.")
        .options("Why?", {
            it.newDialogue()
                .player("Why?")
                .npc(npc, "Because Asgarnia has banned the import of intoxicating", "spirits.")
                .options("Search away, I have nothing to hide.", { search(it, npc, route) },
                         "You're not putting your hands on my things!", { refuseSearch(it, npc) })
                .open()
        }, "Search away, I have nothing to hide.", {
            search(it, npc, route)
        }, "You're not putting your hands on my things!", {
            refuseSearch(it, npc)
        }).open()
}

npc1(CUSTOMS_OFFICER) {
    val npc = targetNpc.id
    // One officer stands at Musa Point, the rest at Brimhaven.
    val route = if (targetNpc.position.x < 2815) ShipRoute.BRIMHAVEN_TO_ARDOUGNE else ShipRoute.KARAMJA_TO_PORT_SARIM
    plr.newDialogue()
        .npc(npc, "Can I help you?")
        .options("Can I journey on this ship?", {
            askToBoard(it, npc, route)
        }, "Does Karamja have unusual customs then?", {
            it.newDialogue()
                .player("Does Karamja have any unusual customs then?")
                .npc(npc, "I'm not that sort of customs officer.")
                .open()
        }).open()
}
