package game.content.sailing

import api.predef.*
import game.content.sailing.Sailing.CAN_I_JOURNEY
import game.content.sailing.Sailing.CAPTAIN_BARNABY
import game.content.sailing.Sailing.CUSTOMS_OFFICER
import game.content.sailing.Sailing.KARAMJAN_RUM
import game.content.sailing.Sailing.OK
import game.content.sailing.Sailing.PORT_SARIM_SAILORS
import game.content.sailing.Sailing.SEARCH_AWAY
import game.content.sailing.Sailing.YES_PLEASE
import game.content.sailing.Sailing.payFare
import game.content.sailing.Sailing.sail
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Offers [plr] a trip along [route] for its fare.
 */
fun offerTrip(plr: Player, npc: Int, route: ShipRoute) {
    // TODO During Dragon Slayer, after Oziach, add "I'd rather go to Crandor Isle." first. The sailor answers "No I
    //  need to stay alive, I have a wife and family to support."
    plr.newDialogue()
        .npc(npc, "Do you want to go on a trip to ${route.place}?")
        .npc(npc, "The trip will cost you ${route.fare} coins.")
        .options(YES_PLEASE, {
            it.newDialogue().player(YES_PLEASE).then {
                if (payFare(it, route)) {
                    it.sendMessage("You pay the ${route.fare} coins...")
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
 * Asks [plr] for the boarding charge of [route], then sails them along it.
 */
fun boardingCharge(plr: Player, route: ShipRoute) {
    plr.newDialogue().options(OK, {
        it.newDialogue().player(OK).then {
            if (payFare(it, route)) {
                sail(it, route, "You pay ${route.fare} coins and board the ship.")
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
    plr.newDialogue().player(SEARCH_AWAY).then {
        if (route.search.turnsAway(it)) {
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
                     "you need to pay a boarding charge of ${route.fare} coins.")
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
        .player(CAN_I_JOURNEY)
        .npc(npc, "You need to be searched before you can board.")
        .options("Why?", {
            it.newDialogue()
                .player("Why?")
                .npc(npc, "Because Asgarnia has banned the import of intoxicating", "spirits.")
                .options(SEARCH_AWAY, { search(it, npc, route) },
                         "You're not putting your hands on my things!", { refuseSearch(it, npc) })
                .open()
        }, SEARCH_AWAY, {
            search(it, npc, route)
        }, "You're not putting your hands on my things!", {
            refuseSearch(it, npc)
        }).open()
}

npc1(CUSTOMS_OFFICER) {
    val npc = targetNpc.id
    // One officer stands at Musa Point, the rest at Brimhaven.
    val route = ShipRoute.departingNear(targetNpc) ?: return@npc1
    plr.newDialogue()
        .npc(npc, "Can I help you?")
        .options(CAN_I_JOURNEY, {
            askToBoard(it, npc, route)
        }, "Does Karamja have unusual customs then?", {
            it.newDialogue()
                .player("Does Karamja have any unusual customs then?")
                .npc(npc, "I'm not that sort of customs officer.")
                .open()
        }).open()
}
