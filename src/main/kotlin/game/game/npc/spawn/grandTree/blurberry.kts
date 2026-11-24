package game.npc.spawn.grandTree

import api.predef.*
import game.npc.spawn.grandTree.GnomeBar.BLURBERRY
import game.npc.spawn.grandTree.GnomeBar.HIRED
import game.npc.spawn.grandTree.GnomeBar.Order
import game.npc.spawn.grandTree.GnomeBar.TRIAL_START
import game.npc.spawn.grandTree.GnomeBar.TrialCocktail
import game.npc.spawn.grandTree.GnomeBar.gnomeBarProgress
import game.skill.cooking.gnomeCooking.GnomeCooking
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Blurberry, who runs the bar in the Grand Tree.
 */
// TODO Alfred Grimhand's Barcrawl: a player with a barcrawl card who hasn't had Blurberry's drink yet is served
//  it first.
npc1(BLURBERRY) { talk(plr) }

for (cocktail in GnomeBar.COCKTAILS) {
    useItem(cocktail).onNpc(BLURBERRY) {
        if (plr.gnomeBarProgress == 0 || plr.gnomeBarProgress == HIRED) {
            plr.newDialogue().npc(BLURBERRY, "Nice cocktail, but I don't need any right now...").open()
        } else {
            talk(plr)
        }
    }
}

/**
 * Blurberry's dialogue for where [plr] is in the trial or their orders.
 */
fun talk(plr: Player) {
    val trialCocktail = GnomeBar.trialCocktail(plr)
    val order = GnomeBar.order(plr)
    when {
        plr.gnomeBarProgress == 0 -> introduce(plr)
        plr.gnomeBarProgress == TRIAL_START -> startTrial(plr)
        trialCocktail != null -> trialStep(plr, trialCocktail)
        order != null -> checkOrder(plr, order)
        else -> plr.newDialogue()
            .player("Hello again Blurberry!")
            .npc(BLURBERRY, "Well hello traveller.")
            .npc(BLURBERRY, "I'm quite busy as usual, any chance you could help?")
            .then { offerOrder(it) }
            .open()
    }
}

/**
 * Blurberry offers [plr] bar work and, if they take it, his cocktail guide.
 */
fun introduce(plr: Player) {
    plr.newDialogue()
        .player("Hello.")
        .npc(BLURBERRY, Expression.HAPPY, "Well hello there ${plr.username}.", "If you're looking for a cocktail",
             "the barman will happily make you one.")
        .player("He looks pretty busy.")
        .npc(BLURBERRY, Expression.HAPPY, "I know, I just can't find any skilled staff.",
             "I don't suppose you're looking for some part time work?",
             "The pay isn't great but it's a good way to meet people.")
        .options("No thanks, I prefer to stay this side of the bar.", {
            it.newDialogue().player("No thanks, I prefer to stay this side of the bar.").open()
        }, "Ok then I'll give it a go.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Ok then I'll give it a go.")
                .npc(BLURBERRY, Expression.HAPPY, "Excellent. It's not an easy job, I'll have to try you out " +
                    "first. I'm sure you'll be great though.")
                .then { plr ->
                    if (GnomeRestaurant.exchange(plr, emptyList(), listOf(Item(GnomeBar.COCKTAIL_GUIDE)))) {
                        plr.gnomeBarProgress = TRIAL_START
                        plr.newDialogue()
                            .npc(BLURBERRY, Expression.HAPPY,
                                 "Here, take this cocktail guide. The book tells you how to",
                                 "make all the cocktails we serve. I'll tell you what I need", "and you can make them.")
                            .player(Expression.HAPPY, "Sounds easy enough.")
                            .npc(BLURBERRY, "Take a look at the book then come and talk to me.")
                            .open()
                    }
                }
                .open()
        })
        .open()
}

/**
 * Blurberry gives [plr] what they need for the first trial cocktail, a Fruit Blast.
 */
fun startTrial(plr: Player) {
    plr.newDialogue()
        .player("Hello Blurberry.")
        .npc(BLURBERRY, Expression.HAPPY, "Hi, are you ready to make your first cocktail?")
        .player(Expression.HAPPY, "Absolutely.")
        .npc(BLURBERRY, Expression.HAPPY, "Ok then, to start with make a Fruit Blast. Here, you'll",
             "need these ingredients, but I'm afraid I can't give you", "any more if you mess up.")
        .then {
            val ingredients = listOf(Item(GnomeCooking.LEMON, 2), Item(GnomeCooking.ORANGE),
                Item(GnomeCooking.PINEAPPLE), Item(GnomeCooking.COCKTAIL_GLASS), Item(GnomeBar.KNIFE),
                Item(GnomeCooking.COCKTAIL_SHAKER))
            if (GnomeRestaurant.exchange(it, emptyList(), ingredients)) {
                it.gnomeBarProgress = TRIAL_START + 1
                it.newDialogue()
                    .text("Blurberry gives you two lemons, one orange, one pineapple, a cocktail shaker, a glass " +
                        "and a knife.")
                    .npc(BLURBERRY, "Let me know when you're done.")
                    .open()
            }
        }
        .open()
}

/**
 * Blurberry asks [plr] for the trial cocktail [cocktail], and takes it if they mixed one.
 */
fun trialStep(plr: Player, cocktail: TrialCocktail) {
    val dialogue = plr.newDialogue()
    when (cocktail) {
        TrialCocktail.FRUIT_BLAST -> dialogue
            .npc(BLURBERRY, "So where's my Fruit Blast?")
        TrialCocktail.DRUNK_DRAGON -> dialogue
            .player("Hello Blurberry.")
            .npc(BLURBERRY, Expression.QUIZZICAL, "Hello again ${plr.username}, how did you do?")
        TrialCocktail.SGG -> dialogue
            .player("Hi Blurberry.")
            .npc(BLURBERRY, Expression.QUIZZICAL, "So, have you got my SGG?")
        TrialCocktail.CHOC_SATURDAY -> dialogue
            .player("Hello Blurberry.")
            .npc(BLURBERRY, "Hello, how did it go with the Choc Saturday?")
        TrialCocktail.BLURBERRY_SPECIAL -> dialogue
            .player("Hi again.")
            .npc(BLURBERRY, "So how did you do?")
    }
    dialogue.then {
        when {
            it.inventory.contains(cocktail.cocktail) -> handInTrialCocktail(it, cocktail)
            it.inventory.contains(cocktail.premade) -> it.newDialogue()
                .player(Expression.HAPPY, "I think I've got one.")
                .npc(BLURBERRY, Expression.HAPPY, "No, I need you to make the ${cocktail.spoken} yourself. I'm",
                     "afraid it doesn't really work if you try to sell the bar its", "own drinks.")
                .open()
            else -> notDone(it, cocktail)
        }
    }.open()
}

/**
 * [plr] hasn't brought Blurberry the trial cocktail [cocktail] yet.
 */
fun notDone(plr: Player, cocktail: TrialCocktail) {
    val dialogue = plr.newDialogue()
    when (cocktail) {
        TrialCocktail.FRUIT_BLAST, TrialCocktail.DRUNK_DRAGON, TrialCocktail.SGG -> dialogue
            .npc(BLURBERRY, Expression.ANGRY, "I don't know what you have there but it's no ${cocktail.spoken}!")
        TrialCocktail.CHOC_SATURDAY -> dialogue
            .player(Expression.SAD, "I haven't managed to make it yet.")
            .npc(BLURBERRY, "Ok, it's one Choc Saturday I need.  Let me know when", "it's done.")
        TrialCocktail.BLURBERRY_SPECIAL -> dialogue
            .player(Expression.SAD, "I haven't managed to make it yet.")
            .npc(BLURBERRY, "I need one Blurberry Special.  Let me know when", "it's done.")
    }
    dialogue.open()
}

/**
 * [plr] hands Blurberry the trial cocktail [cocktail], and he gives them what they need for the next one or hires
 * them.
 */
fun handInTrialCocktail(plr: Player, cocktail: TrialCocktail) {
    val glass = Item(GnomeCooking.COCKTAIL_GLASS)
    val coin = Item(GnomeRestaurant.COINS)
    val next = when (cocktail) {
        TrialCocktail.FRUIT_BLAST -> listOf(glass, Item(GnomeCooking.VODKA), Item(GnomeCooking.GIN),
            Item(GnomeCooking.DWELLBERRIES), Item(GnomeCooking.PINEAPPLE), Item(GnomeCooking.POT_OF_CREAM))
        TrialCocktail.DRUNK_DRAGON -> listOf(coin, glass, Item(GnomeCooking.VODKA), Item(GnomeCooking.LIME, 4),
            Item(GnomeCooking.EQUA_LEAVES))
        TrialCocktail.SGG -> listOf(coin, glass, Item(GnomeCooking.WHISKY), Item(GnomeCooking.BUCKET_OF_MILK),
            Item(GnomeCooking.EQUA_LEAVES), Item(GnomeCooking.CHOCOLATE_BAR), Item(GnomeCooking.POT_OF_CREAM),
            Item(GnomeCooking.CHOCOLATE_DUST))
        TrialCocktail.CHOC_SATURDAY -> listOf(glass, Item(GnomeCooking.VODKA), Item(GnomeCooking.GIN),
            Item(GnomeCooking.BRANDY), Item(GnomeCooking.LEMON, 3), Item(GnomeCooking.ORANGE, 2),
            Item(GnomeCooking.LIME), Item(GnomeCooking.EQUA_LEAVES))
        TrialCocktail.BLURBERRY_SPECIAL -> listOf(glass)
    }
    if (!GnomeRestaurant.exchange(plr, listOf(Item(cocktail.cocktail)), next)) {
        return
    }
    plr.gnomeBarProgress++
    val dialogue = plr.newDialogue()
    when (cocktail) {
        TrialCocktail.FRUIT_BLAST -> dialogue
            .player(Expression.HAPPY, "Here you go.")
            .text("You give Blurberry the Fruit Blast. He takes a sip...")
            .npc(BLURBERRY, Expression.HAPPY, "Hmmm... not bad, not bad at all.")
            .npc(BLURBERRY, Expression.QUIZZICAL, "Now can you make me a Drunk Dragon?")
            .text("Blurberry gives you some vodka, some gin, some dwellberries, some pineapple, and some cream.")
            .npc(BLURBERRY, "I'm afraid I won't be able to give you anymore if you make a mistake though.")
            .npc(BLURBERRY, "Let me know when it's done.")
        TrialCocktail.DRUNK_DRAGON -> dialogue
            .player(Expression.HAPPY, "Here you go.")
            .text("You give Blurberry the Drunk Dragon. He takes a sip...")
            .npc(BLURBERRY, Expression.HAPPY, "Woooo, that's some good stuff. I can sell that.",
                 "There you go, your share of the profit.")
            .text("Blurberry gives you 1 gold coin.")
            .player(Expression.HAPPY, "Thanks.")
            .npc(BLURBERRY, "Okay then now I need an SGG.")
            .player(Expression.CONFUSED, "A what?")
            .npc(BLURBERRY, "A Short Green Guy, and don't bring me a gnome.", "Here's all you need.")
            .text("Blurberry gives you four limes, some vodka and some equa leaves.")
        TrialCocktail.SGG -> dialogue
            .player(Expression.HAPPY, "Here you go.")
            .text("You give Blurberry the SGG. He takes a sip...")
            .npc(BLURBERRY, Expression.HAPPY, "Hmmm, not bad, not bad at all. I can sell that.",
                 "There you go, that's your share.")
            .text("Blurberry gives you 1 gold coin.")
            .npc(BLURBERRY, "You're doing quite well, I'm impressed.")
            .npc(BLURBERRY, "Ok let's try a Choc Saturday, I love them.")
            .text("Blurberry gives you some wisky, some milk, some equa leaves, a chocolate bar, some cream, and " +
                "some chocolate dust.")
        TrialCocktail.CHOC_SATURDAY -> dialogue
            .player(Expression.QUIZZICAL, "Here, try some.")
            .text("You give Blurberry the Choc Saturday. He takes a sip...")
            .npc(BLURBERRY, Expression.HAPPY, "That's blurberry-tastic! You're quite a bartender.")
            .npc(BLURBERRY, Expression.HAPPY, "Okay, let's test you once more. Make me a",
                 "Blurberry Special then we'll see if you have what it takes.", "Here's your ingredients.")
            .player("Ok, I'll try my best.")
            .npc(BLURBERRY, "I'm sure you'll make a great bartender.")
        TrialCocktail.BLURBERRY_SPECIAL -> dialogue
            .player(Expression.QUIZZICAL, "I think I've made it right.")
            .text("You give Blurberry the Blurberry Special. He takes a sip...")
            .npc(BLURBERRY, Expression.SHOCKED, "Well I never... incredible. Not many manage to get that",
                 "right, but this is perfect!")
            .npc(BLURBERRY, Expression.QUIZZICAL, "It would be an honour to have you on the team.")
            .player(Expression.QUIZZICAL, "Thanks!")
            .npc(BLURBERRY, Expression.QUIZZICAL, "Now if you ever want to make some money or want",
                 "to improve your cooking skills, just come and see me.",
                 "I'll tell you what drinks we need, and if you can, you make them.")
            .player(Expression.CONFUSED, "What about ingredients?")
            .npc(BLURBERRY, Expression.HAPPY, "I'm afraid I can't give you anymore for free, but you can buy them " +
                "from Heckel Funch the grocer.")
            .npc(BLURBERRY, Expression.HAPPY, "I'll always pay you more for the cocktail than you paid for the " +
                "ingredients and it's a great way to learn how to prepare food and drinks.")
    }
    dialogue.open()
}

/**
 * Blurberry asks [plr], who has finished the trial, to mix him an order.
 */
fun offerOrder(plr: Player) {
    plr.newDialogue()
        .options("I'm quite busy myself, sorry.", {
            it.newDialogue()
                .player("I'm quite busy myself, sorry.")
                .npc(BLURBERRY, "That's ok, come back when you're free.")
                .open()
        }, "Ok then, what do you need?", {
            val order = GnomeBar.assignOrder(it)
            it.newDialogue()
                .player(Expression.HAPPY, "Ok then, what do you need?")
                .npc(BLURBERRY, order.request)
                .player(order.reply)
                .open()
        })
        .open()
}

/**
 * Blurberry asks [plr] for [order], and pays for it if it's ready.
 */
fun checkOrder(plr: Player, order: Order) {
    plr.newDialogue()
        .player("Hi.")
        .npc(BLURBERRY, "Have you made the order?")
        .then {
            if (order.isReady(it)) {
                handInOrder(it, order)
            } else {
                it.newDialogue()
                    .player(Expression.SAD, "Not yet.")
                    .npc(BLURBERRY, "Ok, I need ${order.description}.")
                    .npc(BLURBERRY, "Let me know when you're done.")
                    .open()
            }
        }
        .open()
}

/**
 * [plr] hands Blurberry [order], and he pays them and offers another.
 */
fun handInOrder(plr: Player, order: Order) {
    val cocktails = order.cocktails.map { (id, amount) -> Item(id, amount) }
    if (!GnomeRestaurant.exchange(plr, cocktails, listOf(Item(GnomeRestaurant.COINS, order.coins)))) {
        return
    }
    plr.gnomeBarProgress = HIRED
    plr.newDialogue()
        .player("Here you go, ${order.description}.")
        .text("You give blurberry ${order.description}.")
        .npc(BLURBERRY, order.praise)
        .text("Blurberry gives you ${order.coins} gold coins.")
        .npc(BLURBERRY, "Could you make me another order?")
        .then { offerOrder(it) }
        .open()
}
