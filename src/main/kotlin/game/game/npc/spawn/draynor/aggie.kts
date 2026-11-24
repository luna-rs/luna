package game.npc.spawn.draynor

import api.predef.*
import game.player.Sound
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression

/**
 * Aggie the witch, in her house in Draynor Village.
 */
val AGGIE = 922

/**
 * The coins Aggie charges and fines.
 */
val COINS = 995

/**
 * What Aggie charges for each dye.
 */
val DYE_PRICE = 5

/**
 * The pot of flour Aggie takes when a player who insults her has too few coins.
 */
val POT_OF_FLOUR = 1933

/**
 * The stack of coins shown when Aggie fines a player.
 */
val COIN_STACK = 1002

/**
 * Aggie's hand wave when she takes a fine.
 */
val WAVE = Animation(881)

/**
 * A dye Aggie makes.
 *
 * @property colour The dye's colour.
 * @property dye The dye's item id.
 * @property ingredient The item she makes it from.
 * @property amount How many of [ingredient] she needs.
 * @property ingredientName What [ingredient] is called in her messages.
 * @property recipe What Aggie says the dye needs.
 */
enum class AggieDye(val colour: String, val dye: Int, val ingredient: Int, val amount: Int, val ingredientName: String,
                    vararg val recipe: String) {
    RED("red", 1763, 1951, 3, "berries", "3 lots of redberries and 5 coins to you."),
    YELLOW("yellow", 1765, 1957, 2, "onions",
           "Yellow is a strange colour to get, comes from onion",
           "skins. I need 2 onions and 5 coins to make yellow dye."),
    BLUE("blue", 1767, 1793, 2, "woad leaves", "2 woad leaves and 5 coins to you.")
}

npc1(AGGIE) {
    // TODO Prince Ali Rescue: while the player is helping Prince Ali (after speaking to Osman, before he's saved),
    //  "Could you think of a way to make skin paste?" comes first. Until the quest exists nobody is at that stage.
    val aggie = targetNpc
    plr.newDialogue()
        .npc(AGGIE, Expression.HAPPY, "What can I help you with?")
        .options("What could you make for me?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "What could you make for me?")
                .npc(AGGIE, "I mostly just make what I find pretty. I sometimes",
                     "make dye for the women's clothes, brighten the place up.",
                     "I can make red, yellow and blue dyes. Would you like", "some?")
                .then { plr -> dyes(plr) }
                .open()
        }, "Cool, do you turn people into frogs?", {
            it.newDialogue()
                .player(Expression.HAPPY, "Cool, do you turn people into frogs?")
                .npc(AGGIE, "Oh, not for years, but if you meet a talking chicken,",
                     "you have probably met the professor in the manor north",
                     "of here. A few years ago it was flying fish. That", "machine is a menace.")
                .open()
        }, "You mad old witch, you can't help me.", {
            it.newDialogue()
                .player(Expression.ANGRY, "You mad old witch, you can't help me.")
                .then { plr -> fine(plr, aggie) }
                .open()
        }, "Can you make dyes for me please?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can you make dyes for me please?")
                .npc(AGGIE, Expression.QUIZZICAL, "What sort of dye would you like? Red, yellow or blue?")
                .then { plr -> dyes(plr) }
                .open()
        })
        .open()
}

for (dye in AggieDye.values()) {
    useItem(dye.ingredient).onNpc(AGGIE) { make(plr, dye) }
}

/**
 * Aggie asks [plr] which dye they'd like.
 */
fun dyes(plr: Player) {
    plr.newDialogue()
        .options("What do you need to make red dye?", { recipe(it, AggieDye.RED) },
                 "What do you need to make yellow dye?", { recipe(it, AggieDye.YELLOW) },
                 "What do you need to make blue dye?", { recipe(it, AggieDye.BLUE) },
                 "No thanks, I am happy the colour I am.", {
                     it.newDialogue()
                         .player("No thanks, I am happy the colour I am.")
                         .npc(AGGIE, "You are easily pleased with yourself then.", "When you need dyes, come to me.")
                         .open()
                 })
        .open()
}

/**
 * Aggie tells [plr] what [dye] needs, and offers to make it.
 */
fun recipe(plr: Player, dye: AggieDye) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "What do you need to make ${dye.colour} dye?")
        .npc(AGGIE, *dye.recipe)
        .options("Okay, make me some ${dye.colour} dye please.", { make(it, dye) },
                 "I don't think I have all the ingredients yet.", {
                     it.newDialogue()
                         .player(Expression.CONFUSED, "I don't think I have all the ingredients yet.")
                         .npc(AGGIE, "You know what you need to get now, come back when",
                              "you have them. Goodbye for now.")
                         .open()
                 },
                 "I can do without dye at that price.", {
                     it.newDialogue()
                         .player(Expression.ANGRY, "I can do without dye at that price.")
                         .npc(AGGIE, "That's your choice, but I would think you have killed",
                              "for less. I can see it in your eyes.")
                         .open()
                 })
        .open()
}

/**
 * Aggie makes [plr] one [dye], if they brought the ingredients and her fee.
 */
fun make(plr: Player, dye: AggieDye) {
    when {
        plr.inventory.computeAmountForId(dye.ingredient) < dye.amount ->
            plr.newDialogue()
                .text("You don't have enough ${dye.ingredientName} to make the ${dye.colour} dye!")
                .open()
        plr.inventory.computeAmountForId(COINS) < DYE_PRICE ->
            plr.newDialogue().text("You don't have enough coins to pay for the dye!").open()
        else -> plr.newDialogue()
            .player("Okay, make me some ${dye.colour} dye please.")
            .then {
                if (it.inventory.computeAmountForId(dye.ingredient) >= dye.amount &&
                    it.inventory.computeAmountForId(COINS) >= DYE_PRICE) {
                    it.inventory.remove(Item(COINS, DYE_PRICE))
                    it.inventory.remove(Item(dye.ingredient, dye.amount))
                    it.inventory.add(Item(dye.dye))
                    it.newDialogue()
                        .give(Item(dye.dye), "You hand the ${dye.ingredientName} and payment to Aggie.\\n" +
                                             "Aggie takes a ${dye.colour} bottle from nowhere\\nand hands it to you.",
                              false)
                        .open()
                }
            }
            .open()
    }
}

/**
 * Aggie fines [plr] for calling her names.
 */
fun fine(plr: Player, aggie: Npc) {
    val coins = plr.inventory.computeAmountForId(COINS)
    val fine = when {
        coins >= 101 -> 20
        coins >= 21 -> 5
        else -> 0
    }
    when {
        fine > 0 -> {
            plr.inventory.remove(Item(COINS, fine))
            takeFine(plr, aggie)
            plr.newDialogue()
                .npc(AGGIE, Expression.ANGRY, "Oh, you like to call a witch names do you?")
                .give(Item(COIN_STACK), "Aggie waves her hands about, and you seem\\nto be $fine coins poorer.", false)
                .npc(AGGIE, "That's a fine for insulting a witch. You should learn", "some respect.")
                .open()
        }
        plr.inventory.contains(POT_OF_FLOUR) -> {
            plr.inventory.remove(Item(POT_OF_FLOUR))
            takeFine(plr, aggie)
            plr.newDialogue()
                .npc(AGGIE, Expression.ANGRY, "Oh, you like to call a witch names do you?")
                .give(Item(POT_OF_FLOUR), "Aggie waves her hands near you, and you seem\\nto have lost some flour.",
                      false)
                .npc(AGGIE, "Thank you for your kind present of flour.", "I am sure you never meant to insult me.")
                .open()
        }
        else -> plr.newDialogue()
            .npc(AGGIE, Expression.ANGRY, "You should be careful about insulting a witch. You",
                 "never know what shape you could wake up in.")
            .open()
    }
}

/**
 * Aggie waves her hands at [plr] as she takes her fine.
 */
fun takeFine(plr: Player, aggie: Npc) {
    aggie.animation(WAVE)
    plr.playSound(Sound.PICK)
}
