package game.content.dwarfMulticannon

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.dwarfMulticannon.DwarfMulticannon.AMMO_MOULD
import game.content.dwarfMulticannon.DwarfMulticannon.INSTRUCTION_MANUAL
import game.content.dwarfMulticannon.DwarfMulticannon.NULODION
import game.content.dwarfMulticannon.DwarfMulticannon.cannonPosition
import game.content.dwarfMulticannon.DwarfMulticannon.cannonStage
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The shop's name, shown as its title.
 */
val SHOP_NAME = "Multi cannon parts for sale:"

/**
 * The price of a full cannon.
 */
val CANNON_PRICE = 750_000

/**
 * The coins item ID.
 */
val COINS = 995

ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.DEFAULT
    currency = Currency.COINS

    sell {
        "Cannon base" x 5
        "Cannon stand" x 5
        "Cannon barrels" x 5
        "Cannon furnace" x 5
        "Instruction manual" x 5
        "Ammo mould" x 5
    }

    open {
        npc2 += NULODION
    }
}

/**
 * Opens Nulodion's shop.
 */
fun openShop(plr: Player) {
    plr.overlays.open(ShopInterface(world, SHOP_NAME))
}

/**
 * Sells [plr] a full cannon, if they can afford it.
 */
fun buyCannon(plr: Player) {
    plr.newDialogue()
        .player("Ok, I'll take a cannon please.")
        // The ANGRY animations are a shifty look.
        .npc(NULODION, Expression.ANGRY,"Ok then, but keep it quiet...", "This thing is top secret!")
        .then {
            if (plr.inventory.computeAmountForId(COINS) < CANNON_PRICE) {
                plr.newDialogue()
                    .player(Expression.SAD, "Oops, I don't have enough money.")
                    .npc(NULODION, "Sorry, I can't go any lower than that.")
                    .open()
            } else if (plr.inventory.remove(Item(COINS, CANNON_PRICE))) {
                CannonStage.entries.forEach { plr.giveItem(Item(it.partId)) }
                plr.giveItem(Item(AMMO_MOULD))
                plr.giveItem(Item(INSTRUCTION_MANUAL))
                plr.newDialogue()
                    .text("You give the Cannon Engineer 750,000 coins...")
                    .text("He gives you the four parts that make the cannon, plus",
                          "an ammo mould and an instruction manual.")
                    .npc(NULODION, "There you go, you be careful with that thing.")
                    .player("Will do, take care mate.")
                    .npc(NULODION, "Take care adventurer.")
                    .open()
            }
        }.open()
}

/**
 * Asks Nulodion about buying a cannon.
 */
fun askToBuy(plr: Player) {
    plr.newDialogue()
        .player("I was hoping you might sell me a cannon.")
        .npc(NULODION, "Hmmmmmm...")
        .npc(NULODION, "I shouldn't really, but as you helped us so much,",
             "well, I could sort something out.",
             "I'll warn you though, they don't come cheap.")
        .player("How much?")
        .npc(NULODION, "For the full set up... 750,000 coins.",
             "Or I can sell you the separate parts for 200,000 each.")
        .player("That's not cheap!")
        .options("Ok, I'll take a cannon please.", { buyCannon(it) },
                 "Can I look at the separate parts please?", {
                     plr.newDialogue()
                         .player("Can I look at the separate parts please?")
                         .npc(NULODION, "Of course!")
                         .then { openShop(it) }
                         .open()
                 },
                 "Sorry, that's too much for me.", {
                     plr.newDialogue()
                         .player(Expression.SAD, "Sorry, that's too much for me.")
                         .npc(NULODION, "Fair enough, it's too much for most of us.")
                         .open()
                 },
                 "Have you any ammo or instructions to sell?", {
                     plr.newDialogue()
                         .player("Have you any ammo or instructions to sell?")
                         .npc(NULODION, "Yes, of course.")
                         .then { openShop(it) }
                         .open()
                 })
        .open()
}

/**
 * Asks Nulodion about the cannon.
 */
fun askAboutCannon(plr: Player) {
    plr.newDialogue()
        .player("I want to know more about the cannon.")
        .npc(NULODION, "There's only so much I can tell you adventurer.",
             "We've been working on this little beauty for some time now.")
        .player("Is it effective?")
        .npc(NULODION, "In short bursts it's very effective, the most",
             "destructive weapon to date. The cannon automatically",
             "targets monsters close by. You just have to make the",
             "ammo and let it rip.")
        .open()
}

/**
 * Replaces [plr]'s cannon if it was lost while set up.
 */
fun replaceCannon(plr: Player) {
    plr.newDialogue()
        .player(Expression.SAD, "I've lost my cannon...")
        .npc(NULODION, "That's unfortunate... but don't worry, I can sort you out.")
        .then {
            val stage = plr.cannonStage
            val cannon = Cannon.ALL[plr.username]
            when {
                plr.cannonPosition == null || stage == null || cannon?.stage == CannonStage.FURNACE ->
                    plr.newDialogue()
                        .npc(NULODION, Expression.SAD, "Oh dear, I'm only allowed to replace cannons",
                             "that were stolen in action.",
                             "I'm sorry but you'll have to buy a new set.")
                        .open()

                cannon != null -> {
                    val parked = when (cannon.stage) {
                        CannonStage.BASE -> arrayOf("Hmmm. I think you'll find the base still happily",
                                                    "parked on the spot where you put it.")
                        CannonStage.STAND -> arrayOf("Hmmm. I think you'll find the base and stand still",
                                                     "happily parked on the spot where you put them.")
                        else -> arrayOf("Hmmm. I think you'll find most of the cannon still",
                                        "happily parked on the spot where you put it.")
                    }
                    plr.newDialogue()
                        .npc(NULODION, *parked)
                        .player("Oh, is it still there? I thought I'd lost it.")
                        .npc(NULODION, Expression.LAUGHING, "Ha ha ha, what a muddle-headed numpty you are!")
                        .player("...")
                        .open()
                }

                else -> plr.newDialogue()
                    .npc(NULODION, "Keep that quiet or I'll be in real trouble!")
                    .player("Of course.")
                    .then {
                        plr.sendMessage(when (stage) {
                                            CannonStage.BASE -> "The dwarf gives you a new cannon part."
                                            CannonStage.FURNACE -> "The dwarf gives you a new cannon."
                                            else -> "The dwarf gives you new cannon parts."
                                        })
                        stage.parts.forEach { plr.giveItem(Item(it)) }
                        plr.cannonPosition = null
                        plr.cannonStage = null
                    }.open()
            }
        }.open()
}

npc1(NULODION) {
    plr.newDialogue()
        .player("Hello.")
        .npc(NULODION, "Hello traveller, how's things?")
        .player("Not bad thanks, yourself?")
        .npc(NULODION, "I'm good, just working hard as usual...")
        .options("I was hoping you might sell me a cannon?", { askToBuy(it) },
                 "Well, take care of yourself then.", {
                     plr.newDialogue().player("Well, take care of yourself then.").open()
                 },
                 "I want to know more about the cannon.", { askAboutCannon(it) },
                 "I've lost my cannon.", { replaceCannon(it) })
        .open()
}
