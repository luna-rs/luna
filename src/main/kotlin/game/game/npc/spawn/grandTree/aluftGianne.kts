package game.npc.spawn.grandTree

import api.predef.*
import game.npc.spawn.grandTree.GnomeRestaurant.ALUFT
import game.npc.spawn.grandTree.GnomeRestaurant.HIRED
import game.npc.spawn.grandTree.GnomeRestaurant.Order
import game.npc.spawn.grandTree.GnomeRestaurant.TRIAL_START
import game.npc.spawn.grandTree.GnomeRestaurant.TrialDish
import game.npc.spawn.grandTree.GnomeRestaurant.gnomeRestaurantProgress
import game.skill.cooking.gnomeCooking.GnomeCooking
import game.skill.cooking.gnomeCooking.GnomeDish
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression

/**
 * Aluft Gianne, who runs the restaurant in the Grand Tree.
 */
npc1(ALUFT) { talk(plr) }

for (dish in GnomeRestaurant.DISHES) {
    useItem(dish).onNpc(ALUFT) { talk(plr) }
}

/**
 * Aluft's dialogue for where [plr] is in the trial or their orders.
 */
fun talk(plr: Player) {
    val trialDish = GnomeRestaurant.trialDish(plr)
    val order = GnomeRestaurant.order(plr)
    when {
        plr.gnomeRestaurantProgress == 0 -> introduce(plr)
        plr.gnomeRestaurantProgress == TRIAL_START -> startTrial(plr)
        trialDish != null -> trialStep(plr, trialDish)
        order != null -> checkOrder(plr, order)
        else -> plr.newDialogue()
            .player("Hello again Aluft.")
            .npc(ALUFT, Expression.HAPPY, "Well hello there ${plr.username}.")
            .npc(ALUFT, "Have you come to help me out?")
            .then { offerOrder(it) }
            .open()
    }
}

/**
 * Aluft offers [plr] a cook's job and, if they take it, his cook book.
 */
fun introduce(plr: Player) {
    plr.newDialogue()
        .player("Hello.")
        .npc(ALUFT, Expression.HAPPY, "Well hello there. Are you hungry? If so you've come to",
             "the right place. Eat green, eat gnome cuisine. My", "waiter will be glad to take your order.")
        .player("Thanks.")
        .npc(ALUFT, Expression.QUIZZICAL,
             "On the other hand if you looking for some work, I have a cook's position available.")
        .options("No thanks, I'm no cook.", {
            it.newDialogue()
                .player("No thanks, I'm no cook.")
                .npc(ALUFT, Expression.QUIZZICAL, "In that case, please eat and enjoy.")
                .open()
        }, "Ok, I'll give it a go.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Ok, I'll give it a go.")
                .npc(ALUFT, Expression.HAPPY, "Well that's great. I'm Aluft Gianne, owner of this",
                     "restaurant, and you are...?")
                .player("Most people call me ${it.username}.")
                .npc(ALUFT, Expression.HAPPY, "Really? Sorry, how rude of me. It just takes a little",
                     "while to get used to the funny names you humans", "have.")
                .npc(ALUFT, Expression.HAPPY, "Of course if you're going to work for me I'll have to",
                     "see what you're like first. Here, take a look at our", "menu.")
                .then { plr ->
                    if (GnomeRestaurant.exchange(plr, emptyList(), listOf(Item(GnomeRestaurant.COOK_BOOK)))) {
                        plr.gnomeRestaurantProgress = TRIAL_START
                        plr.newDialogue()
                            .text("Aluft hands you a cook book.")
                            .npc(ALUFT, "When you've had a look at the book, come back here",
                                 "and I'll let you prepare a few dishes.")
                            .player(Expression.HAPPY, "Ok then.")
                            .open()
                    }
                }
                .open()
        })
        .open()
}

/**
 * Aluft gives [plr] what they need for the first trial dish, a cheese and tomato batta.
 */
fun startTrial(plr: Player) {
    plr.newDialogue()
        .player("Hi Mr Gianne.")
        .npc(ALUFT, Expression.HAPPY, "Hello ${plr.username}. So what did you think of the book?")
        .player(Expression.CONFUSED, "I'm not too sure about toad's legs.")
        .npc(ALUFT, Expression.HAPPY, "They're a gnome delicacy, you'll love them. But we'll start with something " +
            "simple, can you make me a cheese and tomato gnome batta? Here's what you need.")
        .then {
            val ingredients = listOf(Item(GnomeCooking.TOMATO), Item(GnomeCooking.CHEESE),
                Item(GnomeCooking.EQUA_LEAVES), Item(GnomeCooking.GIANNE_DOUGH), Item(GnomeDish.BATTA.tin))
            if (GnomeRestaurant.exchange(it, emptyList(), ingredients)) {
                it.gnomeRestaurantProgress = TRIAL_START + 1
                it.newDialogue()
                    .text("Aluft gives you one tomato, some cheese, some equa leaves and some dough")
                    .player("Thanks!")
                    .npc(ALUFT, Expression.HAPPY, "Let me know how you get on.")
                    .open()
            }
        }
        .open()
}

/**
 * Aluft asks [plr] for the trial dish [dish], and takes it if they cooked one.
 */
fun trialStep(plr: Player, dish: TrialDish) {
    plr.newDialogue()
        .apply { trialGreeting(plr, dish) }
        .then {
            when {
                it.inventory.contains(dish.dish) -> handInTrialDish(it, dish)
                GnomeRestaurant.lost(it, dish.utensil) -> {
                    val (option, spoken) = GnomeRestaurant.utensilNames(listOf(dish.utensil))
                    it.newDialogue()
                        .options("I'm afraid I've lost the $option you gave to me.", { plr ->
                            plr.newDialogue()
                                .player(Expression.SAD, "I'm afraid I've lost the $spoken you gave to me.")
                                .then { replaceUtensils(it, listOf(dish.utensil), "another one") }
                                .open()
                        }, notDoneOption(dish), { plr -> notDone(plr, dish) })
                        .open()
                }
                it.inventory.contains(dish.premade) -> it.newDialogue()
                    .player("I think I've got one.")
                    .npc(ALUFT, "That looks identical to the ${dish.noun} I cooked not five",
                         "minutes ago. You'll have to prove to me that you can", "make one yourself.")
                    .open()
                else -> notDone(it, dish)
            }
        }
        .open()
}

/**
 * How [plr] and Aluft greet each other while [dish] is owed.
 */
fun DialogueQueueBuilder.trialGreeting(plr: Player, dish: TrialDish) {
    when (dish) {
        TrialDish.CHEESE_TOM_BATTA -> player("Hi Mr Gianne.")
            .npc(ALUFT, Expression.HAPPY, "Call me Aluft. So how did you get on?")
        TrialDish.CHOCOLATE_BOMB -> player("Hello Aluft.")
            .npc(ALUFT, Expression.QUIZZICAL, "Hello there, how did you get on?")
        TrialDish.TOAD_BATTA -> player("Hi Mr Gianne.")
            .npc(ALUFT, "Aluft.")
            .player("Sorry, Aluft.")
            .npc(ALUFT, Expression.QUIZZICAL, "So where's my toad batta?")
        TrialDish.WORM_HOLE -> player("Hello again Aluft.")
            .npc(ALUFT, Expression.QUIZZICAL, "Hello ${plr.username}, how did you do?")
        TrialDish.TOAD_CRUNCHIES -> player("Hi Aluft.")
            .npc(ALUFT, Expression.QUIZZICAL, "Hello ${plr.username}, how are you getting on?")
    }
}

/**
 * The option [plr] picks to say [dish] isn't ready, when they could also ask for a new utensil.
 */
fun notDoneOption(dish: TrialDish) = when (dish) {
    TrialDish.CHEESE_TOM_BATTA -> "Erm, I've not quite finished it yet."
    TrialDish.CHOCOLATE_BOMB -> "I haven't made it yet."
    TrialDish.TOAD_BATTA, TrialDish.WORM_HOLE -> "I'm not done yet."
    TrialDish.TOAD_CRUNCHIES -> "No luck so far."
}

/**
 * [plr] tells Aluft [dish] isn't ready yet.
 */
fun notDone(plr: Player, dish: TrialDish) {
    val dialogue = plr.newDialogue()
    when (dish) {
        TrialDish.CHEESE_TOM_BATTA -> dialogue
            .player(Expression.CONFUSED, "Erm, I've not quite finished it yet.")
            .npc(ALUFT, "Ok, let me know when you have. I just need one cheese and tomato batta.")
        TrialDish.CHOCOLATE_BOMB -> dialogue
            .player("I haven't made it yet.")
            .npc(ALUFT, "Just follow the instructions carefully. I need one chocolate bomb.")
        TrialDish.TOAD_BATTA -> dialogue
            .player("I'm not done yet.")
            .npc(ALUFT, "Ok, quick as you can though.")
            .player(Expression.HAPPY, "No problem.")
        TrialDish.WORM_HOLE -> dialogue
            .player("I'm not done yet.")
            .npc(ALUFT, "Ok, quick as you can though. I need one worm hole.")
            .player(Expression.HAPPY, "No problem.")
        TrialDish.TOAD_CRUNCHIES -> dialogue
            .player("No luck so far.")
            .npc(ALUFT, "Ok then but don't take too long. I need some toad crunchies.")
    }
    dialogue.open()
}

/**
 * [plr] hands Aluft the trial dish [dish], and he gives them what they need for the next one or hires them.
 */
fun handInTrialDish(plr: Player, dish: TrialDish) {
    val next = when (dish) {
        TrialDish.CHEESE_TOM_BATTA -> listOf(Item(GnomeCooking.CHOCOLATE_BAR, 4), Item(GnomeCooking.EQUA_LEAVES),
            Item(GnomeCooking.CHOCOLATE_DUST), Item(GnomeCooking.GIANNE_DOUGH), Item(GnomeCooking.POT_OF_CREAM, 2),
            Item(GnomeDish.BOWL.tin))
        TrialDish.CHOCOLATE_BOMB -> listOf(Item(GnomeCooking.CHEESE), Item(GnomeCooking.GIANNE_DOUGH),
            Item(GnomeCooking.EQUA_LEAVES), Item(GnomeCooking.GNOME_SPICE))
        TrialDish.TOAD_BATTA -> listOf(Item(GnomeCooking.GIANNE_DOUGH), Item(GnomeCooking.ONION, 2),
            Item(GnomeCooking.EQUA_LEAVES), Item(GnomeCooking.GNOME_SPICE))
        TrialDish.WORM_HOLE -> listOf(Item(GnomeCooking.GIANNE_DOUGH), Item(GnomeCooking.EQUA_LEAVES),
            Item(GnomeCooking.GNOME_SPICE), Item(GnomeDish.CRUNCHIES.tin))
        TrialDish.TOAD_CRUNCHIES -> emptyList()
    }
    if (!GnomeRestaurant.exchange(plr, listOf(Item(dish.dish)), next)) {
        return
    }
    plr.gnomeRestaurantProgress++
    val dialogue = plr.newDialogue()
    when (dish) {
        TrialDish.CHEESE_TOM_BATTA -> dialogue
            .player("No problem, it was easy.")
            .text("You give Aluft the batta. He sniffs it then takes a bite...")
            .npc(ALUFT, Expression.HAPPY, "Not bad, not bad at all.")
            .npc(ALUFT, "Ok now for something a little harder. Try and make me a chocolate bomb. They're my " +
                "favourite. Here's everything you need.")
            .text("Aluft gives you four bars of chocolate, some equa leaves, some chocolate dust, some dough, and " +
                "some cream.")
            .player(Expression.HAPPY, "Ok Aluft, I'll be back soon.")
        TrialDish.CHOCOLATE_BOMB -> dialogue
            .player(Expression.HAPPY, "Here you go.")
            .text("You give Aluft the chocolate bomb. He takes a bite...")
            .npc(ALUFT, Expression.HAPPY, "Yes, yes, yes, that's superb. I'm really impressed.")
            .npc(ALUFT, "Ok then, can you make me a toad batta? Here's what", "you need.")
            .text("Aluft gives you some cheese, some dough, some equa leaves, and some gnome spice.")
            .npc(ALUFT, Expression.HAPPY, "I'm afraid all our toad's legs are served fresh.")
            .player(Expression.BORED, "Nice.")
            .npc(ALUFT, Expression.HAPPY, "So you'll need to go to the swamp on ground level and catch a toad. " +
                "Let me know when the batta's ready.")
        TrialDish.TOAD_BATTA -> dialogue
            .player(Expression.HAPPY, "Here you go, that was no problem.")
            .text("You give Aluft the toad batta. He takes a bite...")
            .npc(ALUFT, Expression.HAPPY, "Ooh, that's some good toad. Very nice.")
            .npc(ALUFT, "Let's see if you can make a worm hole.")
            .player(Expression.CONFUSED, "A worm hole?")
            .npc(ALUFT, "Yes, it's in the cook book I gave you. You'll have to get the worms from the swamp. But " +
                "here's everything else you'll need. Let me know when you're done.")
        TrialDish.WORM_HOLE -> dialogue
            .player(Expression.HAPPY, "Here you go.")
            .text("You give Aluft the worm hole. He takes a bite...")
            .npc(ALUFT, Expression.HAPPY, "Hmm, that's actually really good.")
            .npc(ALUFT, "How about you make me some toad crunchies for desert? Then I'll decide whether I can take " +
                "you on.")
            .player(Expression.CONFUSED, "Toad crunchies?")
            .npc(ALUFT, Expression.HAPPY, "Thats right, here's all you need except the toad's legs.")
            .text("Aluft gives you some dough, some equa leaves, and some gnome spice.")
            .npc(ALUFT, "Let me know when you're done.")
        TrialDish.TOAD_CRUNCHIES -> dialogue
            .player(Expression.HAPPY, "Here, see what you think.")
            .text("You give Aluft the toad crunchies. He takes a bite...")
            .npc(ALUFT, Expression.QUIZZICAL, "Well for a human you certainly can cook. I'd love to have you on " +
                "the team.")
            .npc(ALUFT, Expression.QUIZZICAL, "If you ever want to make some money or want to improve your " +
                "cooking skills, just come and see me. I'll tell you what meals I need, and if you can, you make " +
                "them.")
            .player(Expression.CONFUSED, "What about ingredients?")
            .npc(ALUFT, Expression.HAPPY, "Well you know where to find toads and worms. You can buy the rest " +
                "from Hudo Glenfad the grocer.")
            .npc(ALUFT, Expression.HAPPY, "I'll always pay you much more for the meal than you paid for the " +
                "ingredients, and it's a great way to improve your cooking skills.")
    }
    dialogue.open()
}

/**
 * Aluft asks [plr], who has finished the trial, to cook him an order.
 */
fun offerOrder(plr: Player) {
    val lost = GnomeRestaurant.lostUtensils(plr)
    val options = mutableListOf(
        DialogueOption("Sorry Aluft, I'm too busy.") {
            it.newDialogue()
                .player("Sorry Aluft, I'm too busy.")
                .npc(ALUFT, Expression.HAPPY, "No worries, let me know when you're free.")
                .open()
        },
        DialogueOption("I would be glad to help.") {
            val order = GnomeRestaurant.assignOrder(it)
            it.newDialogue()
                .player(Expression.QUIZZICAL, "I would be glad to help.")
                .npc(ALUFT, order.request)
                .player(order.reply)
                .open()
        })
    if (lost.isNotEmpty()) {
        options += lostUtensilsOption(lost)
    }
    plr.newDialogue().options(options).open()
}

/**
 * The option for [plr] to ask Aluft to replace the [lost] utensils.
 */
fun lostUtensilsOption(lost: List<GnomeDish>): DialogueOption {
    val (option, spoken) = GnomeRestaurant.utensilNames(lost)
    return DialogueOption("I'm afraid I've lost the $option you gave me.") {
        it.newDialogue()
            .player(Expression.SAD, "I'm afraid I've lost the $spoken you gave me.")
            .then { plr -> replaceUtensils(plr, lost, "some more") }
            .open()
    }
}

/**
 * Aluft gives [plr] new [utensils], saying he gives them [amount].
 */
fun replaceUtensils(plr: Player, utensils: List<GnomeDish>, amount: String) {
    if (GnomeRestaurant.exchange(plr, emptyList(), utensils.map { Item(it.tin) })) {
        plr.newDialogue()
            .npc(ALUFT, "Ok, I'll give you $amount. Obviously I can't expect",
                 "you to cook without the proper utensils.")
            .open()
    }
}

/**
 * Aluft asks [plr] for [order], and pays for it if it's ready.
 */
fun checkOrder(plr: Player, order: Order) {
    plr.newDialogue()
        .player("Hi Aluft.")
        .npc(ALUFT, order.greeting.text(plr))
        .then {
            when {
                order.isReady(it) -> handInOrder(it, order)
                GnomeRestaurant.lostUtensils(it).isNotEmpty() -> it.newDialogue()
                    .options(listOf(DialogueOption("I'm not done yet.") { plr -> orderNotDone(plr, order) },
                                    lostUtensilsOption(GnomeRestaurant.lostUtensils(it))))
                    .open()
                else -> orderNotDone(it, order)
            }
        }
        .open()
}

/**
 * [plr] tells Aluft [order] isn't ready, and he repeats it.
 */
fun orderNotDone(plr: Player, order: Order) {
    plr.newDialogue()
        .player(Expression.SAD, "I'm not done yet.")
        .npc(ALUFT, "${order.reminder.first}${order.description}${order.reminder.second}")
        .open()
}

/**
 * [plr] hands Aluft [order], and he pays them and offers another.
 */
fun handInOrder(plr: Player, order: Order) {
    val dishes = order.dishes.map { (id, amount) -> Item(id, amount) }
    if (!GnomeRestaurant.exchange(plr, dishes, listOf(Item(GnomeRestaurant.COINS, order.coins)))) {
        return
    }
    plr.cooking.addExperience(GnomeRestaurant.EXP_PER_DISH * order.size)
    plr.gnomeRestaurantProgress = HIRED
    plr.newDialogue()
        .player(order.handIn)
        .text("You give Aluft ${order.description}.")
        .npc(ALUFT, "They look great, well done. Here's your share of the profit.")
        .text("Aluft gives you ${order.coins} gold coins.")
        .npc(ALUFT, "Can you stay and make another dish?")
        .then { offerOrder(it) }
        .open()
}
