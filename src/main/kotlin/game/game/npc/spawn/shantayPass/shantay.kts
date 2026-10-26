package game.npc.spawn.shantayPass

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.obj.doors.Doors
import game.player.Sound
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import kotlin.random.Random

/**
 * The [Npc] id for Shantay.
 */
val SHANTAY_ID = 836

/**
 * The name of Shantay's shop.
 */
val SHOP_NAME = "Shantay Pass Shop"

/**
 * The coins item ID.
 */
val COINS = 995

/**
 * The Shantay pass item ID.
 */
val SHANTAY_PASS = 1854

/**
 * The price of a Shantay pass, which is also the jail fine.
 */
val PASS_PRICE = 5

/**
 * The scrumpled paper item ID, which Shantay sometimes drops when spoken to.
 */
val SCRUMPLED_PAPER = 1847

/**
 * The jail progress value for a player who has never spoken to Shantay.
 */
val NOT_TALKED = 0

/**
 * The jail progress value for a player who has spoken to Shantay, or paid their fine.
 */
val TALKED = 1

/**
 * The jail progress value for a player who has been locked in Shantay's jail.
 */
val JAILED = 2

/**
 * The position inside Shantay's jail cell.
 */
val JAIL_CELL = Position(3296, 3124)

/**
 * The position inside the Port Sarim prison.
 */
val PORT_SARIM_PRISON = Position(3017, 3182)

/**
 * The prison door of Shantay's jail. It is owned by this script, so it only opens once the fine is paid.
 */
val JAIL_DOOR = DoorType(2692, 1541, null, Sound.GRATE_OPEN, Sound.GRATE_CLOSE, 3)

/**
 * The player's progress through Shantay's jail.
 */
var Player.shantayJailProgress by Attr.int().persist("shantay_jail_progress")

/**
 * Returns whether Shantay is close enough to [plr] to keep them in his jail.
 */
fun shantayNearby(plr: Player) =
    world.npcs.any { it.id == SHANTAY_ID && it.position.isWithinDistance(plr.position, 12) }

/**
 * Opens Shantay's shop.
 */
fun openShop(plr: Player) {
    plr.newDialogue()
        .player("Can I see what you have to sell please?")
        .npc(SHANTAY_ID, "Absolutely Effendi!")
        .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
        .open()
}

/**
 * Ends the conversation with Shantay.
 */
fun mustBeGoing(plr: Player) {
    plr.newDialogue()
        .player("I must be going.")
        .npc(SHANTAY_ID, "So long...")
        .open()
}

/**
 * Sells [plr] a Shantay pass, if they can afford it.
 */
fun buyPass(plr: Player) {
    plr.newDialogue()
        .player("I want to buy a shantay pass for 5 gold coins.")
        .then {
            if (it.inventory.computeAmountForId(COINS) < PASS_PRICE) {
                it.newDialogue()
                    .npc(
                        SHANTAY_ID,
                        "Sorry friend, the Shantay Pass is 5 gold coins. You",
                        "don't seem to have enough money!"
                    )
                    .open()
            } else if (it.inventory.remove(Item(COINS, PASS_PRICE))) {
                it.giveItem(Item(SHANTAY_PASS))
                it.newDialogue()
                    .give(Item(SHANTAY_PASS), "You purchase a Shantay Pass.", false)
                    .open()
            }
        }
        .open()
}

/**
 * Opens the dialogue where [plr] asks why they have to pay to go into the desert.
 */
fun payForDesert(plr: Player) {
    plr.newDialogue()
        .player("Why do I have to pay to go into the desert?")
        .then {
            it.overlays.closeWindows()
            it.sendMessage("Shantay opens his arms wide as if to embrace you.")
            world.scheduleOnce(3) { _ ->
                it.newDialogue()
                    .npc(
                        SHANTAY_ID,
                        "Effendi, you insult me! I am not interested in making",
                        "a profit from you! I merely seek to cover my expenses",
                        "in keeping this pass open."
                    )
                    .npc(
                        SHANTAY_ID,
                        "There is repair work to carry out and also the men's",
                        "wages to consider. For the paltry sum of 5 Gold pieces,",
                        "I think we offer a great service."
                    )
                    .options(
                        "Can I see what you have to sell please?", { p -> openShop(p) },
                        "I must be going.", { p -> mustBeGoing(p) }
                    )
                    .open()
            }
        }
        .open()
}

/**
 * Sends [plr] from Shantay's jail to the Port Sarim prison.
 */
fun sendToPortSarim(plr: Player) {
    plr.overlays.closeWindows()
    plr.sendMessage("You find yourself in a prison.")
    plr.move(PORT_SARIM_PRISON)
    plr.face(Direction.WEST)
}

/**
 * Opens the bank so a jailed [plr] can fetch the coins for their fine.
 */
fun payFineFromBank(plr: Player) {
    plr.newDialogue()
        .player("I'll pay the fine.")
        .npc(SHANTAY_ID, "Okay then..., you'll need access to your bank.")
        .then { it.bank.open() }
        .open()
}

/**
 * Opens the dialogue where a jailed [plr] refuses to pay their fine.
 */
fun refuseFine(plr: Player) {
    plr.newDialogue()
        .player("No thanks, you're not having my money.")
        .npc(
            SHANTAY_ID,
            "You have a choice.",
            "You can either pay five gold pieces or...",
            "You can be transported to a maximum security prison",
            "in Port Sarim."
        )
        .npc(SHANTAY_ID, "Will you pay the five gold pieces?")
        .options(
            "Yes, okay.", { payFineFromBank(it) },
            "No, do your worst!",
            {
                it.newDialogue()
                    .npc(
                        SHANTAY_ID,
                        "You are to be transported to a maximum security",
                        "prison in Port Sarim. I hope you've learnt an important",
                        "lesson from this."
                    )
                    .then { p -> sendToPortSarim(p) }
                    .open()
            }
        )
        .open()
}

/**
 * Opens the dialogue where a jailed [plr] agrees to pay their fine. Players without the coins are offered their bank,
 * or sent to the Port Sarim prison if their bank cannot cover it either.
 */
fun payFine(plr: Player) {
    plr.newDialogue()
        .player("Yes, okay.")
        .npc(SHANTAY_ID, "Good, I see that you have come to your senses.")
        .then {
            if (it.inventory.computeAmountForId(COINS) >= PASS_PRICE && it.inventory.remove(Item(COINS, PASS_PRICE))) {
                it.shantayJailProgress = TALKED
                it.sendMessage("You hand over five gold pieces to Shantay.")
                it.newDialogue()
                    .npc(SHANTAY_ID, "Great Effendi, now please try to keep the peace.")
                    .then { p -> p.sendMessage("Shantay unlocks the door to the cell.") }
                    .open()
            } else if (it.bank.computeAmountForId(COINS) >= PASS_PRICE) {
                it.newDialogue()
                    .npc(SHANTAY_ID, "You don't have that kind of cash on you I see.")
                    .npc(
                        SHANTAY_ID,
                        "But perhaps you have some in your bank? Or would",
                        "you prefer the maximum security prison in Port Sarim."
                    )
                    .npc(SHANTAY_ID, "Which is it going to be?")
                    .options(
                        "I'll pay the fine.", { p -> payFineFromBank(p) },
                        "No thanks, you're not having my money.", { p -> refuseFine(p) }
                    )
                    .open()
            } else {
                it.newDialogue()
                    .npc(SHANTAY_ID, "You don't have that kind of cash on you I see.")
                    .then { p -> sendToPortSarim(p) }
                    .open()
            }
        }
        .open()
}

/**
 * Asks a jailed [plr] whether they will pay their fine.
 */
fun askForFine(plr: Player, vararg text: String) {
    plr.newDialogue()
        .npc(SHANTAY_ID, *text)
        .options(
            "Yes, okay.", { payFine(it) },
            "No thanks, you're not having my money.", { refuseFine(it) }
        )
        .open()
}

/**
 * Locks [plr] in Shantay's jail for claiming to be an outlaw.
 */
fun arrest(plr: Player) {
    plr.newDialogue()
        .player("I am definitely an outlaw, prepare to die!")
        .npc(SHANTAY_ID, "Ha, very funny.....")
        .npc(SHANTAY_ID, "Guards arrest ${if (plr.appearance.isFemale) "her" else "him"}!")
        .then {
            it.overlays.closeWindows()
            it.sendMessage("The guards arrest you and place you in the jail.")
            world.scheduleOnce(2) { _ ->
                it.shantayJailProgress = JAILED
                it.move(JAIL_CELL)
                askForFine(
                    it,
                    "You'll have to stay in there until you pay the fine of",
                    "five gold pieces. Do you want to pay now?"
                )
            }
        }
        .open()
}

/**
 * Opens the options that follow Shantay's explanation of the pass.
 */
fun afterExplanation(plr: Player) {
    plr.newDialogue()
        .options(
            "Can I see what you have to sell please?", { openShop(it) },
            "I must be going.", { mustBeGoing(it) },
            "Why do I have to pay to go into the desert?", { payForDesert(it) }
        )
        .open()
}

/**
 * Opens the dialogue where [plr] asks Shantay what the pass is.
 */
fun whatIsThisPlace(plr: Player) {
    if (plr.shantayJailProgress == JAILED) {
        plr.shantayJailProgress = TALKED
        plr.newDialogue()
            .npc(
                SHANTAY_ID,
                "You should be in jail! Well, no doubt the authorities in",
                "Port Sarim know what they're doing. But if you get",
                "into any more trouble, you'll be stuck back in jail."
            )
            .open()
        return
    }
    plr.newDialogue()
        .player("What is this place?")
        .npc(
            SHANTAY_ID,
            "This is the pass of Shantay. I guard this area with my",
            "men. I am responsible for keeping this pass open and",
            "repaired."
        )
        .npc(
            SHANTAY_ID,
            "My men and I prevent outlaws from getting out of the",
            "desert. And we stop the inexperienced from a dry death",
            "in the sands. Which would you say you were?"
        )
        .options(
            "I am definitely an outlaw, prepare to die!", { arrest(it) },
            "I am a little inexperienced.",
            {
                it.newDialogue()
                    .player("I am a little inexperienced.")
                    .npc(
                        SHANTAY_ID,
                        "Can I recommend that you purchase a full waterskin",
                        "and a knife! These items will no doubt save your life. A",
                        "waterskin will keep water from evaporating in the desert."
                    )
                    .npc(
                        SHANTAY_ID,
                        "And a keen woodsman with a knife can extract the juice",
                        "from a cactus. Before you go into the desert, it's",
                        "advisable to wear desert clothes. It's very hot in the",
                        "desert and you'll surely cook if you wear armour."
                    )
                    .npc(
                        SHANTAY_ID,
                        "To keep the pass bandit free, we charge a small toll of",
                        "five gold pieces. You can buy a desert pass from me,",
                        "just ask me to open the shop. You can also use our",
                        "free banking services by clicking on the chest."
                    )
                    .then { p -> afterExplanation(p) }
                    .open()
            },
            "Er, neither, I'm an adventurer.",
            {
                it.newDialogue()
                    .player("Er, neither, I'm an adventurer.")
                    .npc(
                        SHANTAY_ID,
                        "Great, I have just the thing for the desert adventurer.",
                        "I sell desert clothes which will keep you cool in the heat",
                        "of the desert. I also sell waterskins so that you won't",
                        "die in the desert."
                    )
                    .npc(
                        SHANTAY_ID,
                        "A waterskin and a knife help you survive from the juice",
                        "of a cactus. Use the chest to store your items, we'll take",
                        "them to the bank. It's hot in the desert, you'll bake in",
                        "all that armour."
                    )
                    .npc(
                        SHANTAY_ID,
                        "To keep the pass open we ask for 5 gold pieces. And",
                        "we give you a Shantay Pass, just ask to see what I sell",
                        "to buy one."
                    )
                    .then { p -> afterExplanation(p) }
                    .open()
            }
        )
        .open()
}

/**
 * Handles a jailed player using the prison door. Returns whether the player may pass through it.
 *
 * Shantay offers jailed players inside the cell the chance to pay their fine. A jailed player found outside the cell
 * is let off, as they have already been through the Port Sarim prison.
 */
fun checkJailDoor(plr: Player, inside: Boolean): Boolean {
    if (plr.shantayJailProgress != JAILED || !shantayNearby(plr)) {
        return true
    }
    if (!inside) {
        plr.shantayJailProgress = TALKED
        plr.newDialogue()
            .npc(
                SHANTAY_ID,
                "You should be in jail! Well, no doubt the authorities in",
                "Port Sarim know what they're doing. But if you get",
                "into any more trouble, you'll be stuck back in jail."
            )
            .open()
    } else {
        askForFine(
            plr,
            "If you want to be let out, you have to pay a fine of",
            "five gold. Do you want to pay now?"
        )
    }
    return false
}

/**
 * Walks to the prison door and steps through it, once the player is free to leave.
 */
class JailCrossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, JAIL_DOOR) {
    override fun checkAccess(entering: Boolean): Boolean = checkJailDoor(mob, entering)
}

/*
 * Handles Shantay's first npc option dialogue.
 *
 * Shantay greets new players differently, and occasionally drops a scrumpled paper. The player can ask about the pass,
 * open the shop, leave, or buy a Shantay pass.
 */
npc1(SHANTAY_ID) {
    if (Random.nextInt(100) == 0) {
        plr.sendMessage("Something drops out of Shantay's pocket onto the floor.")
        plr.sendMessage("It looks like a piece of paper.")
        world.addItem(GroundItem(ctx, SCRUMPLED_PAPER, 1, targetNpc.position, ChunkUpdatableView.localView(plr)))
    }
    val greeting = plr.newDialogue()
    if (plr.shantayJailProgress == NOT_TALKED) {
        plr.shantayJailProgress = TALKED
        greeting.npc(SHANTAY_ID, "Hello effendi, I am Shantay.")
            .npc(
                SHANTAY_ID,
                "I see you're new. Please read the billboard poster",
                "before going into the desert. It'll give yer details on the",
                "dangers you can face."
            )
    } else {
        greeting.npc(
            SHANTAY_ID,
            "Hello again friend. Please read the billboard poster",
            "before going into the desert. It'll give yer details on the",
            "dangers you can face."
        )
    }
    greeting.options(
        "What is this place?", { whatIsThisPlace(it) },
        "Can I see what you have to sell please?", { openShop(it) },
        "I must be going.", { mustBeGoing(it) },
        "I want to buy a shantay pass for 5 gold coins.", { buyPass(it) }
    ).open()
}

/*
 * Handles Shantay's "Buy-pass" option.
 */
npc3(SHANTAY_ID) {
    buyPass(plr)
}

/*
 * Handles the prison door of Shantay's jail.
 */
object1(JAIL_DOOR.closed) {
    val inside = Doors.isEntering(gameObject.position, gameObject.direction, plr.position)
    if (checkJailDoor(plr, inside) && plr.actions.first(JailCrossing::class.java) == null) {
        plr.submitAction(JailCrossing(plr, gameObject))
    }
}
