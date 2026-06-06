package game.npc.spawn.varrock

import api.attr.Attr
import api.bot.zone.SubZone
import api.predef.*
import api.shop.dsl.ShopHandler
import game.skill.magic.teleOther.NpcTeleOtherAction
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * The [Npc] id for Aubury.
 */
val AUBURY_ID = 553

/**
 * The name of the shop.
 */
val SHOP_NAME = "Aubury's Rune Shop."

/**
 * The overworld position players return to after leaving the Rune Essence mine through the mine portal.
 */
val EXIT_POSITION = Position(3253, 3400)

/**
 * Stores Aubury's currently active tele-other action.
 *
 * The action is stored directly on the npc so multiple players requesting transport to the Rune Essence mine can be
 * queued into the same running action instead of starting overlapping teleport sequences on the same Aubury instance.
 */
private var Npc.teleOtherAction by Attr.nullableObj(NpcTeleOtherAction::class)

/**
 * Requests that [aubury] teleport [plr] to the Rune Essence mine.
 *
 * If Aubury is not currently running a tele-other action, this starts a new [NpcTeleOtherAction]. If an action is
 * already active, the player is added to that action's request queue so Aubury can process the teleport naturally.
 *
 * @param aubury The Aubury npc performing the teleport.
 * @param plr The player requesting transport to the Rune Essence mine.
 */
fun teleport(aubury: Npc, plr: Player) {
    val action = aubury.teleOtherAction
    if (action == null || action.isFinished) {
        val destination = SubZone.ESSENCE_MINE.inside
        val newAction = NpcTeleOtherAction(aubury, plr, destination)
        aubury.teleOtherAction = newAction
        aubury.submitAction(newAction)
    } else {
        action.addRequest(plr)
    }
}

/*
 * Registers Aubury's rune shop.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Air rune" x 10_000
        "Fire rune" x 10_000
        "Water rune" x 10_000
        "Earth rune" x 10_000
        "Mind rune" x 10_000
        "Body rune" x 10_000
        "Chaos rune" x 500
        "Death rune" x 500
    }

    open {
        npc2 += AUBURY_ID
    }
}

/*
 * Handles Aubury's third npc option.
 *
 * This option is used as a direct Rune Essence mine teleport shortcut, bypassing the dialogue path and immediately
 * submitting or queueing the player into Aubury's tele-other action.
 */
npc3(id = AUBURY_ID, interaction = { _, _ -> InteractionPolicy(InteractionType.SIZE, Position.VIEWING_DISTANCE / 2) }) {
    teleport(targetNpc, plr)
}

/*
 * Handles Aubury's first npc option dialogue.
 *
 * The player can either open the rune shop, decline the shop, or ask Aubury to teleport them to the Rune Essence mine.
 */
npc1(AUBURY_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, "Do you want to buy some runes?")
        .options(
            "Yes please!",
            {
                plr.overlays.open(ShopInterface(world, SHOP_NAME))
            },
            "Oh, it's a rune shop. No thank you, then.",
            {
                plr.newDialogue()
                    .player("Oh, it's a rune shop. No thank you, then.")
                    .npc(
                        targetNpc.id,
                        "Well, if you find someone who does want",
                        "runes, please send them my way."
                    )
                    .open()
            },
            "Can you teleport me to the Rune Essence?",
            {
                plr.newDialogue()
                    .player("Can you teleport me to the Rune Essence?")
                    .npc(
                        targetNpc.id,
                        "Of course. By the way, if you end up making",
                        "any runes from the essence you mine, I'll",
                        "happily buy them from you."
                    ).then {
                        teleport(targetNpc, plr)
                    }
                    .open()
            }
        ).open()
}

/*
 * Handles the Rune Essence mine exit portal.
 *
 * Returns the player from the mine interior back outside Aubury's rune shop in Varrock.
 */
object1(2492) {
    plr.move(EXIT_POSITION)
}