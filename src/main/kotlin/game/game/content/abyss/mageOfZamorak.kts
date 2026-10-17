package game.content.abyss

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.varp.Varp

/**
 * The Mage of Zamorak in the Wilderness north of Edgeville, who teleports players into the Abyss.
 */
val WILDERNESS_MAGE = 2257

/**
 * The Mage of Zamorak the client shows in the Wilderness once Enter the Abyss is complete. Dialogue uses this id for
 * his name and face.
 */
val WILDERNESS_MAGE_SHOWN = 2259

/**
 * The Mage of Zamorak in Varrock's Chaos Temple.
 */
val VARROCK_MAGE = 2260

/**
 * The Mage of Zamorak the client shows in Varrock's Chaos Temple once Enter the Abyss is started. Dialogue uses this id
 * for his name and face.
 */
val VARROCK_MAGE_SHOWN = 2261

/**
 * The Wilderness mage's rune shop.
 */
val SHOP_NAME = "Battle Runes."

on(LoginEvent::class) {
    // TODO Enter the Abyss: send the player's miniquest stage instead. Until the miniquest exists every player has
    //  finished it, so the Wilderness mage offers Teleport and the Varrock mage shows himself.
    plr.sendVarp(Varp(Abyss.MINIQUEST_VARP, Abyss.MINIQUEST_COMPLETE))
}

ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    // TODO Enter the Abyss: before it's complete the mage stocks 100 of each elemental, mind and body rune and 50
    //  chaos and death runes, with no blood runes.
    sell {
        "Fire rune" x 1000
        "Water rune" x 1000
        "Air rune" x 1000
        "Earth rune" x 1000
        "Mind rune" x 1000
        "Chaos rune" x 500
        "Death rune" x 500
        "Blood rune" x 500
    }

    open {
        npc2 += WILDERNESS_MAGE
    }
}

npc1(WILDERNESS_MAGE) {
    // TODO Rune Mysteries and Enter the Abyss: before Rune Mysteries the mage turns the player away. Before Enter the
    //  Abyss he says "Meet me in Varrock's Chaos Temple. Here is not the place to talk.", then "I already told you!
    //  Meet me in the Varrock Chaos Temple!"
    val mage = targetNpc
    plr.newDialogue()
        .npc(WILDERNESS_MAGE_SHOWN, Expression.ANGRY, "This is no place to talk!",
             "If you need help getting out of my sight I can send", "you to the Abyss?")
        .options("Yes", { AbyssTeleport.cast(it, mage) },
                 "No", { it.overlays.closeWindows() })
        .title("Teleport to the Abyss?")
        .open()
}

npc3(WILDERNESS_MAGE) {
    AbyssTeleport.cast(plr, targetNpc)
}

/**
 * The player asks the Varrock mage what the Abyss is.
 */
fun askAboutAbyss(plr: Player) {
    plr.newDialogue()
        .player(Expression.CONFUSED, "Uh... I really don't see how this talk about an 'abyss'",
                "relates to Runecrafting in the slightest.")
        .npc(VARROCK_MAGE_SHOWN, "My primary research responsibility was not towards the",
             "manufacture of runes, this is true.")
        .npc(VARROCK_MAGE_SHOWN, "Rather, I was investigating an error that occurred to",
             "one of our initiates in a routine teleportation", "experiment.")
        .npc(VARROCK_MAGE_SHOWN, "My discovery was this 'abyssal' space, and as a side",
             "effect, as documented in the research notes I have",
             "given you, I discovered the existence of these 'temples'", "used to create runes.")
        .npc(VARROCK_MAGE_SHOWN, "Fortunate for both of us, I would say. As I say, read",
             "my research notes, it should all become clear.")
        .npc(VARROCK_MAGE_SHOWN, "My colleague inside the abyss may be of help to you as", "well.")
        .options("Is this abyss dangerous?", { askIfDangerous(it) },
                 "Can you teleport me there now?", { askForTeleport(it) })
        .open()
}

/**
 * The player asks the Varrock mage whether the Abyss is dangerous.
 */
fun askIfDangerous(plr: Player) {
    plr.newDialogue()
        .player(Expression.CONFUSED, "So... This 'abyss' place... Is it dangerous?")
        .npc(VARROCK_MAGE_SHOWN, "Well, the creatures there ARE particularly offensive...")
        .player(Expression.CONFUSED, "You mean they smell?")
        .npc(VARROCK_MAGE_SHOWN, "No, I mean they hunt and attack any visitors to their",
             "dimension on sight. This is not the danger however.")
        .player(Expression.CONFUSED, "It's not?")
        .npc(VARROCK_MAGE_SHOWN, "No. Unfortunately the magic we have had to use to",
             "retain a portal to the abyss open and effective is derived", "from Lord Zamorak himself.")
        .player(Expression.CONFUSED, "And that's a bad thing somehow...?")
        .npc(VARROCK_MAGE_SHOWN, "Well, he has his occasional quirks. In this case it means",
             "that when you enter this dimension you will be 'skulled'", "and your prayer will be drained.")
        .npc(VARROCK_MAGE_SHOWN, "This makes it somewhat more dangerous than other",
             "places you may be used to.")
        .player("I see...")
        .npc(VARROCK_MAGE_SHOWN, "Was there anything else you wanted?")
        .options("So what is this 'abyss' stuff?", { askAboutAbyss(it) },
                 "Can you teleport me there now?", { askForTeleport(it) })
        .open()
}

/**
 * The player asks the Varrock mage for a teleport, and is sent to the Wilderness instead.
 */
fun askForTeleport(plr: Player) {
    plr.newDialogue()
        .player(Expression.CONFUSED, "Well, I reckon I'm prepared to go there now. Beam me",
                "there, or whatever it is that you do!")
        .npc(VARROCK_MAGE_SHOWN, "No, not from here. The use of my Lord Zamoraks",
             "magic in this land will draw too much attention to", "myself.")
        .npc(VARROCK_MAGE_SHOWN, "Meet me in the wilderness where you spoke to me",
             "before, and right click to find the 'teleport' option.")
        .npc(VARROCK_MAGE_SHOWN, "I trust you do not wish to have too lengthy a",
             "conversation in such a dangerous place.")
        .player(Expression.WORRIED, "You're right, I don't!")
        .npc(VARROCK_MAGE_SHOWN, "I should be able to improve my stock of runes thanks to",
             "your assistance locating the essence site too, so feel free",
             "to stop by if you require any specific runes.")
        .player(Expression.HAPPY, "Okay, thanks!")
        .open()
}

npc1(VARROCK_MAGE) {
    // TODO Enter the Abyss: this mage runs the miniquest. Until it exists every player has finished it.
    plr.newDialogue()
        .options("So what is this 'abyss' stuff?", { askAboutAbyss(it) },
                 "Is this abyss dangerous?", { askIfDangerous(it) },
                 "Can you teleport me there now?", { askForTeleport(it) })
        .open()
}
