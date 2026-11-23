package game.npc.spawn.varrock

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.dialogue.Expression

/**
 * Thessalia's shop, and her make-overs: a new top or new legwear for [MAKEOVER_FEE] coins each, picked from the
 * [TailorStyle] catalogues.
 *
 * @author hydrozoa
 */

val shopkeeperId = 548

/**
 * What one make-over costs.
 */
val MAKEOVER_FEE = 500

/**
 * How close Thessalia must be to speak to the player after a make-over.
 */
val TALK_DISTANCE = 4

/**
 * What Thessalia says after a make-over.
 */
val COMPLIMENTS = listOf("Totally cool! That outfit looks great!",
                         "Very stylish! It really suits you!",
                         "Woah! Fabulous! You look absolutely great!",
                         "Wow! Good choice! You're looking great!",
                         "WoooOOOooo! Hot stuff!",
                         "Fantastic! You should be a model!")

ShopHandler.create("Thessalia's Fine Clothes.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "White apron" x 30
        "Leather body" x 120
        "Leather gloves" x 100
        "Brown apron" x 10
        "Pink skirt" x 50
        "Black skirt" x 30
        "Blue skirt" x 20
        1007 x 40
        "Silk" x 50
        "Silk" x 50
        426 x 30
        428 x 30
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId) {
    plr.newDialogue()
        .npc(targetNpc.id, "Do you want to buy any fine clothes?")
        .options("What have you got?", {
            plr.newDialogue()
                .player("What have you got?")
                .npc(targetNpc.id, "Well, I have a number of fine pieces of clothing", "on sale or, if you prefer, I can offer you ", "an exclusive, total-clothing makeover?")
                .options("Tell me more about this makeover.", {
                    plr.newDialogue()
                        .npc(targetNpc.id, "Certainly!")
                        .npc(targetNpc.id, "Here at Thessalia's fine clothing boutique,", "we offer a unique service where we will totally", "revamp your outfit to your choosing, for... wait", "for it...")
                        .npc(targetNpc.id, "A fee of only 500 gold coins! Tired of always", "wearing the same old outfit, day in, day out?", "This is the service for you!")
                        .npc(targetNpc.id, "So what do you say? Interested? We can change", "either your top, or your legwear for only 500 gold", "an item!")
                        .options("I'd like to change my top please.", {
                            catalogue(plr, targetNpc, if (plr.appearance.isMale) TailorStyle.MAN_TOP else TailorStyle.WOMAN_TOP)
                        }, "I'd like to change my legwear please.", {
                            catalogue(plr, targetNpc, if (plr.appearance.isMale) TailorStyle.MAN_LEGS else TailorStyle.WOMAN_LEGS)
                        }, "I'd just like to buy some clothes.", {
                            plr.overlays.open(ShopInterface(world, "Thessalia's Fine Clothes."))
                        }, "No, thank you.", {
                            noThanksDialogue(plr, targetNpc)
                        })
                        .open()
                }, "I'd just like to buy some clothes.", {
                    plr.overlays.open(ShopInterface(world, "Thessalia's Fine Clothes."))
                }, "No, thank you.", {
                    noThanksDialogue(plr, targetNpc)
                })
                .open()
        }, "No, thank you.", {
            noThanksDialogue(plr, targetNpc)
        })
        .open()
}

fun noThanksDialogue(plr: Player, targetNpc: Npc) {
    plr.newDialogue()
        .player("No, thank you.")
        .npc(targetNpc.id, "Well, please return if ", "you change your mind.")
        .open()
}

/**
 * Thessalia hands [plr] the [style] catalogue.
 */
fun catalogue(plr: Player, thessalia: Npc, style: TailorStyle) {
    plr.newDialogue()
        .npc(thessalia.id, Expression.HAPPY,
             "Just select what style and colour you would like",
             "from this catalogue, and then give me the 500",
             "coins when you've picked.")
        .then { it.overlays.open(TailorInterface(style, thessalia)) }
        .open()
}

/**
 * Returns the [style] catalogue if [plr] has it open.
 */
fun catalogueOpen(plr: Player, style: TailorStyle): TailorInterface? {
    val window = plr.overlays.getOverlay(TailorInterface::class.java)
    return if (window?.style == style) window else null
}

/**
 * Gives [plr] the picks on the open [style] catalogue, for [MAKEOVER_FEE] coins.
 */
fun confirm(plr: Player, style: TailorStyle) {
    val window = catalogueOpen(plr, style) ?: return
    val kit = window.kit
    val arms = window.arms
    val colour = window.colour
    if (kit == null || colour == null || (style.top && arms == null)) {
        plr.sendMessage(if (style.top) "You must make a selection from all three categories." else
                            "You must make a selection from both categories.")
        return
    }
    plr.overlays.closeWindows()
    if (plr.appearance.get(PlayerAppearance.GENDER) != style.gender) {
        return
    }
    if (plr.inventory.computeAmountForId(995) < MAKEOVER_FEE || !plr.inventory.remove(Item(995, MAKEOVER_FEE))) {
        plr.sendMessage("You do not have enough money.")
        return
    }
    plr.appearance.set(style.part, kit)
    if (arms != null) {
        plr.appearance.set(PlayerAppearance.ARMS, arms)
    }
    plr.appearance.set(style.colourPart, colour)
    plr.flags.flag(UpdateFlag.APPEARANCE)
    val thessalia = window.thessalia
    if (thessalia.state == EntityState.ACTIVE && plr.position.isWithinDistance(thessalia.position, TALK_DISTANCE)) {
        plr.newDialogue()
            .npc(thessalia.id, Expression.HAPPY, COMPLIMENTS.random())
            .player(Expression.HAPPY, "Thanks!")
            .open()
    }
}

TailorStyle.values().forEach { style ->
    style.styles.forEach { (id, kit) -> button(id) { catalogueOpen(plr, style)?.kit = kit } }
    style.arms.forEach { (id, kit) -> button(id) { catalogueOpen(plr, style)?.arms = kit } }
    style.colours.forEachIndexed { colour, id -> button(id) { catalogueOpen(plr, style)?.colour = colour } }
    button(style.confirmButton) { confirm(plr, style) }
}