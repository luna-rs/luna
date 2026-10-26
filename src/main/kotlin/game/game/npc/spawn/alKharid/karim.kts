package game.npc.spawn.alKharid

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Karim.
 */
val KARIM_ID = 543

/**
 * The coins item ID.
 */
val COINS = 995

/**
 * The kebab item ID.
 */
val KEBAB = 1971

/**
 * The price of a kebab.
 */
val KEBAB_PRICE = 1

/**
 * Sells [plr] a kebab, if they can afford it.
 */
fun buyKebab(plr: Player, npc: Npc) {
    if (plr.inventory.computeAmountForId(COINS) < KEBAB_PRICE) {
        plr.newDialogue()
            .player(Expression.SAD, "Oops, I forgot to bring any money with me.")
            .npc(npc.id, "Come back when you have some.")
            .open()
    } else if (plr.inventory.remove(Item(COINS, KEBAB_PRICE))) {
        plr.giveItem(Item(KEBAB))
        plr.sendMessage("You buy a kebab.")
    }
}

/*
 * Handles Karim's first npc option dialogue.
 *
 * The player can buy a kebab for one coin or decline. Medium clue scroll progress for Karim is handled separately by
 * the treasure trails plugin.
 */
npc1(KARIM_ID) {
    val npc = targetNpc
    plr.newDialogue()
        .npc(npc.id, Expression.QUIZZICAL, "Would you like to buy a nice kebab? Only one gold.")
        .options(
            "I think I'll give it a miss.",
            {
                plr.newDialogue()
                    .player("I think I'll give it a miss.")
                    .open()
            },
            "Yes please.",
            {
                plr.newDialogue()
                    .player("Yes please.")
                    .then { buyKebab(it, npc) }
                    .open()
            }
        ).open()
}
