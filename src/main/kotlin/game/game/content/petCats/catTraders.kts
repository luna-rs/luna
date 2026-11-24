package game.content.petCats

import api.predef.*
import game.content.petCats.PetCat.Stage
import game.content.petCats.PetCats.catRats
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

val GERTRUDE = 780
val CIVILIANS = listOf(785, 786, 787)
val COINS = 995
val KITTEN_PRICE = 100
val DEATH_RUNE = 560
val CAT_PRICE = 100
val CAT_TRAINING_MEDAL = 1575
val CAVE_WITCH_CAT = 1491

/**
 * Gertrude sells kittens to anyone without one, and gives a medal to an owner whose cat has caught 100 rats.
 */
fun talkToGertrude(plr: Player) {
    // TODO Gertrude's Cat: until the quest is complete, Gertrude asks for help finding Fluffs and sells no kittens.
    val hasMedal = plr.inventory.contains(CAT_TRAINING_MEDAL) || plr.bank.contains(CAT_TRAINING_MEDAL) ||
            plr.equipment.contains(CAT_TRAINING_MEDAL)
    if (plr.catRats >= PetCats.RATS_FOR_MEDAL && !hasMedal) {
        val dialogue = plr.newDialogue()
            .player(Expression.HAPPY, "Hello again Gertrude!")
            .npc(GERTRUDE, "Well, hello adventurer! How are you?")
            .player(Expression.HAPPY, "My cat has caught 100 rats!")
            .npc(GERTRUDE, "Well well! You are good with cats!")
        if (plr.inventory.isFull) {
            dialogue.npc(GERTRUDE, "Free up a little space so I can give you a present.").open()
            return
        }
        dialogue.npc(GERTRUDE, "Here, I have a little present for you...")
            .give(Item(CAT_TRAINING_MEDAL), "Gertrude gives you a small medal.", true)
            .player("Hey thanks Gertrude.")
            .open()
        return
    }
    if (PetCats.hasKittenOrCat(plr)) {
        plr.newDialogue()
            .player("Hello again Gertrude.")
            .npc(GERTRUDE, "Well hello adventurer, how are you?")
            .player("Pretty good, thanks. Yourself?")
            .npc(GERTRUDE, "Same old, running after Shilop most of the time.")
            .player("Nevermind, I'm sure he'll calm down with age.")
            .open()
        return
    }
    val dialogue = plr.newDialogue()
        .player(Expression.HAPPY, "Do you have any more kittens?")
        .npc(GERTRUDE, Expression.HAPPY, "Indeed I have.", "They are 100 coins each, do you want one?")
    if (!plr.inventory.contains(Item(COINS, KITTEN_PRICE))) {
        dialogue.player("Oops, looks like I'm a bit short.", "I'll have to come back later.").open()
        return
    }
    dialogue.options(
        "Yes please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please.")
                .npc(GERTRUDE, Expression.HAPPY, "Okay then, here you go.")
                .player(Expression.HAPPY, "Thanks.")
                .then { buyKitten(it) }
                .open()
        },
        "No thanks.", {
            it.newDialogue().player("No thanks, I've paid that boy enough already.").open()
        }).open()
}

/**
 * Takes Gertrude's price and hands over a kitten that starts following the buyer.
 */
fun buyKitten(plr: Player) {
    if (PetCats.hasKittenOrCat(plr) || !plr.inventory.remove(Item(COINS, KITTEN_PRICE))) {
        plr.overlays.closeWindows()
        return
    }
    PetCats.giveKitten(plr)
    plr.newDialogue().text("Gertrude gives you another kitten.").open()
}

/**
 * The civilians in West Ardougne are overrun with mice and buy grown cats for death runes.
 */
fun talkToCivilian(plr: Player, id: Int) {
    // TODO Biohazard: the civilians only buy cats once the quest is complete.
    val dialogue = when (id) {
        785 -> plr.newDialogue()
            .player(Expression.HAPPY, "Hello there.")
            .npc(id, "Oh hello, I'm sorry, I'm a bit worn out.")
            .player("Busy day?")
            .npc(id, "Oh, It's those mice! They're everywhere! What I really need is a cat. But they're hard to come " +
                    "by nowadays.")
        786 -> plr.newDialogue()
            .player(Expression.HAPPY, "Hi there.")
            .npc(id, Expression.HAPPY, "Good day to you traveller.")
            .player("What are you up to?")
            .npc(id, Expression.BORED, "Chasing mice as usual! It's all I seem to do nowadays.")
            .player("You must waste a lot of time?")
            .npc(id, "Yes, but what can you do? It's not like there's many cats around here!")
        else -> plr.newDialogue()
            .player(Expression.HAPPY, "Hello there.")
            .npc(id, "I'm a bit busy to talk right now, sorry.")
            .player("Why? What are you doing?")
            .npc(id, Expression.ANGRY, "Trying to kill these mice! What I really need is a cat!")
    }
    val hasCat = PetCat.ITEMS.any { plr.inventory.contains(it) && PetCat.forItem(it)!!.stage != Stage.KITTEN }
    when {
        hasCat -> dialogue.options(
            "I have a cat that I could sell.", { offerCat(it, id) },
            "Nope, they're not easy to get hold of.", {
                it.newDialogue().player("Nope, they're not easy to get hold of.").open()
            }).open()
        plr.inventory.contains(CAVE_WITCH_CAT) -> dialogue
            .player(Expression.HAPPY, "I have a cat...look!")
            .npc(id, Expression.QUIZZICAL, "Hmmm...doesn't look like it's seen daylight in years. That's not going to " +
                    "catch any mice!")
            .open()
        else -> dialogue.player("No, you're right, you don't see many around.").open()
    }
}

/**
 * The civilian's offer: 100 death runes for a grown cat, handed over by using it on them.
 */
fun offerCat(plr: Player, id: Int) {
    plr.newDialogue()
        .player(Expression.HAPPY, "I have a cat that I could sell.")
        .npc(id, Expression.HAPPY, "You don't say, is that it ?")
        .player(Expression.HAPPY, "Say hello to a real mouse killer!")
        .npc(id, Expression.HAPPY, "Hmmm, not bad, not bad at all. Looks like it's a lively one.")
        .player(Expression.QUIZZICAL, "Erm...kind of...")
        .npc(id, "I don't have much in the way of money. I do have these!")
        .give(Item(DEATH_RUNE, CAT_PRICE), "The peasant shows you a sack of Death Runes.", false)
        .npc(id, Expression.HAPPY, "The dwarves bring them from the mine for us. Tell you what, I'll give you 100 " +
                "Death Runes for the cat.")
        .options(
            "Nope, I'm not parting for that.", {
                it.newDialogue()
                    .player(Expression.ANGRY, "Nope, I'm not parting for that.")
                    .npc(id, Expression.ANGRY, "Well, I'm not giving you anymore!")
                    .open()
            },
            "Ok then, you've got a deal.", {
                it.newDialogue()
                    .player(Expression.HAPPY, "Ok then, you've got a deal.")
                    .npc(id, Expression.HAPPY, "Great! Hand over the cat and I'll give you the runes.")
                    .open()
            })
        .open()
}

/**
 * Hands the cat at inventory [index] to a civilian for 100 death runes. Kittens are too small to want.
 */
fun sellCat(plr: Player, id: Int, index: Int) {
    val item = plr.inventory[index] ?: return
    val cat = PetCat.forItem(item.id)
    if (cat == null || cat.stage == Stage.KITTEN) {
        plr.sendMessage("Nothing interesting happens.")
        return
    }
    plr.inventory[index] = null
    plr.inventory.add(Item(DEATH_RUNE, CAT_PRICE))
    plr.newDialogue()
        .give(Item(DEATH_RUNE, CAT_PRICE), "You hand over the cat.\\nYou are given 100 Death Runes.", false)
        .npc(id, Expression.HAPPY, "Great, thanks for that!")
        .player(Expression.HAPPY, "That's ok, take care.")
        .open()
}

npc1(GERTRUDE) { talkToGertrude(plr) }

for (civilian in CIVILIANS) {
    npc1(civilian) { talkToCivilian(plr, civilian) }
    for (item in PetCat.ITEMS) {
        useItem(item).onNpc(civilian) { sellCat(plr, civilian, usedItemIndex) }
    }
}
