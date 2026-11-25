package game.content.mageTrainingArena

import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.ENTRANCE_GUARDIAN
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.mtaStarted
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * What the guardians and the progress hat say.
 *
 * @author TheLining
 */
object ArenaDialogues {

    private const val GUARD = ENTRANCE_GUARDIAN

    /**
     * Talking to the Entrance Guardian.
     */
    fun entranceGuardian(plr: Player) {
        val dialogue = plr.newDialogue().player("Hi.").npc(GUARD, "Greetings. What wisdom do you seek?")
        if (plr.mtaStarted) {
            entranceOptions(dialogue)
        } else {
            dialogue.options("I'm new to this place. Where am I?", ::introduction, "None, I don't really care.", { p ->
                p.newDialogue().player("None, I don't really care.")
                    .npc(GUARD, "You won't gain with that attitude.").open()
            })
        }
        dialogue.open()
    }

    private fun entranceMenu(plr: Player) {
        entranceOptions(plr.newDialogue()).open()
    }

    private fun entranceOptions(dialogue: DialogueQueueBuilder): DialogueQueueBuilder =
        dialogue.options(
            "Can you tell me about this place again?", ::history,
            "Can you explain the different portals?", { it.newDialogue().player("Can you explain the different portals?").then(::portals).open() },
            "About the progress hat...", ::aboutHat,
            "Thanks, bye!", ::bye)

    private fun introduction(plr: Player) {
        plr.newDialogue()
            .player("I'm new to this place. Where am I?")
            .npc(GUARD, "Well young one, you have entered the Magic Training Arena. It was built at the start of the " +
                "Fifth Age, when runestones were first discovered. It was made because of the many pointless " +
                "accidents caused by inexperienced mages.")
            .player("Who created it?")
            .npc(GUARD, "Good question. It was originally made by the ancestors of the wizards in the Wizards Tower. " +
                "However, it was destroyed by melee and ranged warriors who took offence at the use of this new " +
                "'Magic Art'.")
            .npc(GUARD, "Recently, the current denizens of the Wizards Tower have resurrected the arena including " +
                "various Guardians you will see as you look around. We are here to help and to ensure things run " +
                "smoothly.")
            .player("Interesting. So what can I do here?")
            .npc(GUARD, "You may train up your skill in the magic arts by travelling through one of the portals at " +
                "the back of this entrance hall. By training up in one of these areas you will be awarded special " +
                "Pizazz Points unique to each room.")
            .npc(GUARD, "With these points you may claim a variety of items from my fellow guardian up the stairs.")
            .player("How do you record the points I have earned?")
            .npc(GUARD, "You really are full of questions! You will need a special Pizazz Progress Hat! I can give " +
                "you one if you so wish to train here.")
            .player("Yes please!")
            .then { p ->
                if (!p.inventory.hasSpaceFor(Item(MageTrainingArena.DULL_HAT))) {
                    p.newDialogue().npc(GUARD, "Ah, I'm afraid I can't give it to you as you don't have room to " +
                        "carry it!").open()
                    return@then
                }
                p.inventory.add(Item(MageTrainingArena.hatFor(p)))
                p.mtaStarted = true
                p.newDialogue()
                    .npc(GUARD, "Here you go. Talk to the hat to find out your current Pizazz Point totals.")
                    .player("Talk to it?")
                    .npc(GUARD, "Well of course, it's a magic Pizazz Progress Hat! Mind your manners though, hats " +
                        "have feelings too!")
                    .player("Er... if you insist.")
                    .npc(GUARD, "Oh, and a word of warning: should you decide to leave the rooms by any method " +
                        "other than the exit portals, you will be teleported to the entrance and have any items " +
                        "that you picked up in the room removed.")
                    .player("Can you explain the different portals?")
                    .then(::portals).open()
            }.open()
    }

    private fun history(plr: Player) {
        plr.newDialogue()
            .player("Can you tell me about this place again?")
            .npc(GUARD, "Very well. You have entered the magic training arena. It was built at the start of the " +
                "Fifth Age, when runestones were first discovered. It was built to put an end to many pointless " +
                "accidents involving inexperienced mages.")
            .player("Who created it?")
            .npc(GUARD, "Good question. It was originally made by the ancestors of the wizards in the Wizards Tower. " +
                "However, it was destroyed by melee and ranged warriors who took offence at the use of this new " +
                "'Magic Art'.")
            .npc(GUARD, "Recently, the current denizens of the Wizards Tower have resurrected the arena including " +
                "various Guardians you will see as you look around. We are here to help and to ensure things run " +
                "smoothly.")
            .player("Interesting. So what can I do here?")
            .npc(GUARD, "You may train up your skill in the magic arts by travelling through one of the portals at " +
                "the back of this building. By training up in one of these areas you will be awarded Pizazz Points " +
                "unique to each room.")
            .npc(GUARD, "With these points you may claim a variety of items from my fellow guardian up the stairs.")
            .player("How do you record the Pizazz Points I have earned?")
            .npc(GUARD, "With the Pizazz Progress Hat I gave you, of course.")
            .player("OK.")
            .npc(GUARD, "Oh, and a word of warning: should you decide to log out whilst in any of the rooms in the " +
                "arena, you will be teleported to the entrance and have any items that you picked up in the room " +
                "removed.")
            .then(::entranceMenu).open()
    }

    private fun portals(plr: Player) {
        plr.newDialogue()
            .npc(GUARD, "They lead to four areas to train your magic: The Telekinetic Theatre, The Alchemists' " +
                "Playground, The Enchanting Chamber, and The Creature Graveyard.")
            .then(::portalMenu).open()
    }

    private fun portalMenu(plr: Player) {
        plr.newDialogue().options(
            "What's the Telekinetic Theatre?", ::theatre,
            "What's the Alchemists' Playground?", ::playground,
            "What's the Enchanting Chamber?", ::chamber,
            "What's the Creature Graveyard?", ::graveyard,
            "Thanks, bye!", ::bye).open()
    }

    private fun theatre(plr: Player) {
        plr.newDialogue()
            .player("What's the Telekinetic Theatre?")
            .npc(GUARD, "In there you can earn Telekinetic Pizazz Points for trade upstairs.")
            .player("What will I be doing in there?")
            .npc(GUARD, "That depends on how much of a time-waster you are! You are required to use the Telekinetic " +
                "Grab spell in order to move a statue through a maze. Casting the spell will move the statue " +
                "towards you until it reaches a wall.")
            .npc(GUARD, "So by standing on the different sides of the maze you can move the statue North, East, " +
                "South or West. You will be rewarded Pizazz Points for each maze successfully solved.")
            .npc(GUARD, "It will be clearer when you arrive and there's a Guardian through the portal that will be " +
                "able to tell you more. There's a book you can buy from the Rewards Guardian upstairs that will " +
                "explain it all.")
            .then(::portalMenu).open()
    }

    private fun playground(plr: Player) {
        plr.newDialogue()
            .player("What's the Alchemists' Playground?")
            .npc(GUARD, "In the playground you can earn Alchemist Pizazz Points for trading with the rewards " +
                "guardian upstairs.")
            .player("What's in there?")
            .npc(GUARD, "You'll find eight cupboards containing items you can turn to gold using the low and high " +
                "alchemy spells. The money you earn will be taken from you upon leaving the playground to pay for " +
                "the upkeep of this training arena and to help fund magic shops around Gielinor.")
            .npc(GUARD, "You will be rewarded with 1 Pizazz Point for every 100 coins deposited and a percentage of " +
                "the money you create. Keep in mind that you will not be able to take more than 1000 coins back " +
                "out with you.")
            .player("Sounds simple.")
            .npc(GUARD, "I am afraid not, for the cost of the items varies, as does their locations in the " +
                "cupboards. You'll see a list of item costs that will help you decide what you should be converting " +
                "to gold.")
            .npc(GUARD, "Fear not, it will be clearer when you arrive and there's a Guardian through the portal that " +
                "will be able to tell you more. You can also buy a book from the Rewards Guardian upstairs that will " +
                "explain it all.")
            .then(::portalMenu).open()
    }

    private fun chamber(plr: Player) {
        plr.newDialogue()
            .player("What's the Enchanting Chamber?")
            .npc(GUARD, "In here you will be able to earn Enchantment Pizazz Points for trade upstairs.")
            .player("What will I have to do?")
            .npc(GUARD, "You will find yourself amongst various piles of shapes. You can pick up these shapes and use " +
                "one of your enchanting spells upon it to morph it into an orb. You'll also see a hole in the centre " +
                "of the room; put the orbs in here and for every 20 we will credit you with a gift.")
            .npc(GUARD, "You will get Pizazz Points for every 10 shapes that you convert and the amount of points " +
                "depends on the spell you use.")
            .player("What's the difference between the shapes?")
            .npc(GUARD, "Ah, you will be notified of a different shape that will give you a bonus Pizazz Point when " +
                "you convert that shape. You should also look out for dragon stones, as these will give you more " +
                "Pizazz Points when you convert them.")
            .npc(GUARD, "It will be clearer when you arrive and there's a Guardian through the portal that will be " +
                "able to tell you more. You can also buy a book from the Rewards Guardian upstairs that will explain " +
                "it all.")
            .then(::portalMenu).open()
    }

    private fun graveyard(plr: Player) {
        plr.newDialogue()
            .player("What's the Creature Graveyard?")
            .npc(GUARD, "In here you will be able to earn Graveyard Pizazz Points for trade upstairs.")
            .player("But what do I have to do in there?")
            .npc(GUARD, "Patience young one. A great many creatures die in this world and much of their remains can " +
                "cause clutter. We have taken it upon ourselves to teleport many bones from these creatures into " +
                "this graveyard for a use.")
            .npc(GUARD, "It's up to you to practice your bones to bananas spell in order to put these bones into " +
                "immediate use - nutritious food for monsters! Just convert the bones and put them in the holes in " +
                "the walls.")
            .npc(GUARD, "There is a drawback to this room however, the bones often fall on people so you may want to " +
                "eat some of those bananas to stay alive. When people die we confiscate Pizazz Points for the effort " +
                "of teleporting incompetent mages.")
            .player("What else do I get from this besides bruises?")
            .npc(GUARD, "As well as the Pizazz Points for giving us the bananas, you will be credited with " +
                "runestones for your efforts, and you may be able to save up for a new kind of spell to use in the " +
                "graveyard!")
            .npc(GUARD, "It will be clearer when you arrive and one of my fellow guardians through the portal will be " +
                "able to tell you more. You can also buy a book from the Rewards Guardian upstairs that will explain " +
                "it all.")
            .then(::portalMenu).open()
    }

    private fun aboutHat(plr: Player) {
        if (!MageTrainingArena.hasHat(plr) && !plr.bank.containsAny(*MageTrainingArena.HATS.toIntArray())) {
            plr.newDialogue()
                .player("About the progress hat...")
                .npc(GUARD, "You want another one don't you.")
                .player("Sorry, can I?")
                .then { p ->
                    if (!p.inventory.hasSpaceFor(Item(MageTrainingArena.DULL_HAT))) {
                        p.newDialogue().npc(GUARD, "Ah, I'm afraid I can't give it to you as you don't have room " +
                            "to carry it!").then(::entranceMenu).open()
                    } else {
                        p.inventory.add(Item(MageTrainingArena.hatFor(p)))
                        p.newDialogue().npc(GUARD, "Here you go. Talk to the hat to find out your current Pizazz " +
                            "Point totals.").then(::entranceMenu).open()
                    }
                }.open()
            return
        }
        plr.newDialogue()
            .player("About the progress hat...")
            .player("Yes. What's it for?")
            .npc(GUARD, "Collect Pizazz Points from the various areas and the hat will remember your totals. Talk to " +
                "the hat at any time to find out what points you have. Go upstairs and talk to the Rewards Guardian " +
                "to claim items for the points.")
            .then(::entranceMenu).open()
    }

    private fun bye(plr: Player) {
        plr.newDialogue().player("Thanks, bye!").npc(GUARD, "Use what you have learned.").open()
    }

    /**
     * Talking to the Rewards Guardian.
     */
    fun rewardsGuardian(plr: Player) {
        val npc = MageTrainingArena.REWARDS_GUARDIAN
        if (!plr.mtaStarted) {
            plr.newDialogue().player("Hi.")
                .npc(npc, "Greetings. Have you spoken to my fellow Guardian downstairs?")
                .player("Nope.")
                .npc(npc, "Well, you need to talk to him first.").open()
            return
        }
        plr.newDialogue().player("Hi.").npc(npc, "Greetings. What wisdom do you seek?").then(::rewardsMenu).open()
    }

    private fun rewardsMenu(plr: Player) {
        val npc = MageTrainingArena.REWARDS_GUARDIAN
        plr.newDialogue().options(
            "Who are you?", {
                it.newDialogue().player("Who are you?")
                    .npc(npc, "Me? I'm here to grant you rewards for any of the Pizazz Points you may have earned in " +
                        "this training arena. Like my fellow Guardians, I am part of the arena and live to ensure " +
                        "its safe running.")
                    .player("I see.").then(::rewardsMenu).open()
            },
            "Can I trade my Pizazz Points please?", {
                it.newDialogue().player("Can I trade my Pizazz Points please?").npc(npc, "Why of course.")
                    .then(RewardsShop::open).open()
            },
            "Got anything else I can buy?", ::arenaBookOffer,
            "Thanks, bye!", {
                it.newDialogue().player("Thanks, bye!").npc(npc, "Use what you've learned, young one.").open()
            }).open()
    }

    private fun arenaBookOffer(plr: Player) {
        val npc = MageTrainingArena.REWARDS_GUARDIAN
        val book = MageTrainingArena.ARENA_BOOK
        val dialogue = plr.newDialogue().player("Got anything else I can buy?")
            .npc(npc, "Well, we do stock a special book that you may be interested in, which provides a comprehensive " +
                "guide to this training arena. It costs 200 coins. Would you like one?")
        if (plr.inventory.contains(book) || plr.bank.contains(book)) {
            dialogue.player("No thanks, I already have one.").npc(npc, "Let me know if you lose it.").open()
            return
        }
        dialogue.options("That sounds useful. Yes please.", {
            it.newDialogue().player("That sounds useful. Yes please.")
                .npc(npc, "Very good, that'll be 200 coins please!").then { p ->
                    if (p.inventory.computeAmountForId(995) < 200) {
                        p.newDialogue().player("Sorry, I don't have enough money.").open()
                    } else if (!p.inventory.hasSpaceFor(Item(book)) && p.inventory.computeAmountForId(995) != 200) {
                        p.sendMessage("You don't have enough inventory space.")
                    } else {
                        p.inventory.remove(Item(995, 200))
                        p.inventory.add(Item(book))
                        p.newDialogue().npc(npc, "Thank you.").then(::rewardsMenu).open()
                    }
                }.open()
        }, "I don't want some dusty old book.", {
            it.newDialogue().player("I don't want some dusty old book.")
                .npc(npc, "It's not old and it certainly isn't dusty, but suit yourself.").open()
        }).open()
    }

    /**
     * Talking to the progress hat: one of four openings, one of four replies, then the totals.
     */
    fun progressHat(plr: Player) {
        val hat = MageTrainingArena.PIZAZZ_HAT
        val opener = listOf("Erm, excuse me Mr Progress Hat?", "Mr Progress Hat... sir?", "Mr Progress Hat? Hello?",
                            "Hello... Mr Progress Hat?").random()
        val dialogue = plr.newDialogue().player(opener)
        when (rand(3)) {
            0 -> dialogue.npc(hat, "Can't you see I'm busy?")
                .player("But you're just a hat? Can you tell me my Pizazz Point totals?")
            1 -> dialogue.npc(hat, "I suppose you want to know your Pizazz Points.").player("That would be nice, yes.")
            2 -> dialogue.npc(hat, "What do you want?").player("Do you think you could tell me my Pizazz Points?")
            else -> dialogue.npc(hat, "That's me, why are you bothering me human?")
                .player("Sorry, but do you think you could tell me my Pizazz Points?")
        }
        val points = Points.values().map { MageTrainingArena.points(plr, it) }
        dialogue.npc(hat, "Ok, I suppose it's my job. You have: ${points[0]} Telekinetic, ${points[1]} Alchemist, " +
            "${points[2]} Enchantment, and ${points[3]} Graveyard Pizazz Points.")
            .player("Thank you!").open()
    }

    /**
     * The four room guardians' talk, from the transcripts.
     */
    fun roomGuardian(plr: Player, npc: Int) {
        val (greeting, what, rewards, tips) = when (npc) {
            MageTrainingArena.TELEKINETIC_GUARDIAN -> listOf(
                "Greetings young one. What is it you wish to know?",
                "In this room you will see a maze within which one of my fellow Guardians has been turned to stone " +
                    "for the purposes of this exercise. You must move the statue using your telekinetic grab spell " +
                    "to the exit square at the edge of the maze to bring the Guardian back to life.|Simply stand on " +
                    "the side that you wish for the statue to travel towards and cast the spell on the statue. Once " +
                    "you have solved the maze, the statue will change back into the Guardian and he will award you " +
                    "with Telekinetic Pizazz Points and teleport you to the next maze.|You can switch to a better " +
                    "view of the maze by selecting the 'Observe' option on the statue and return your view to normal " +
                    "by selecting the same option again. There is also a 'Reset' option on the statue just in case " +
                    "things aren't going too well.",
                "As well as the experience in casting magic, you will get Telekinetic Pizazz Points for each maze " +
                    "successfully solved and bonus points for completing five mazes in a row without returning to " +
                    "the entrance.",
                "Have a good look at the maze before you try to solve it because this can save you time and runes " +
                    "required to navigate the maze. Although you will still be getting magic experience for moving " +
                    "the statue incorrectly, you won't be progressing towards collecting Telekinetic Pizazz Points.|" +
                    "Lastly, all the mazes can be solved in ten moves or less.")

            MageTrainingArena.ALCHEMY_GUARDIAN -> listOf(
                "Greetings young one. What wisdom do you seek?",
                "In this room you will see various cupboards. It is your task to search the cupboards to find items " +
                    "to turn into gold using your low or high alchemy spells. You can deposit the money in the " +
                    "receptacle at the end of the hall in order to receive your Alchemist Pizazz Points.|This money " +
                    "is used for the upkeep of the training arena as well as magic shops all around Gielinor. Keep an " +
                    "eye on the costs of each item as these will change from time-to-time, as will the location of " +
                    "the items.|Occasionally one of the items will be indicated as costing no runestones to convert " +
                    "to money.",
                "You will get magic experience from casting the alchemist spells, as well as 1 Alchemist Pizazz " +
                    "Point for every 100 coins you deposit, and 10% of the coins you deposit will be given to you as " +
                    "you leave. Keep in mind that you will not be able to take more than 1000 coins back out with you.",
                "You must remember to keep an eye on the various costs of the items. If you watch the movements of " +
                    "the other players, you might be able to guess which are the best places to visit. You will get " +
                    "1 Pizazz Point for every 100 coins, so if you have 190 coins, why not get an extra 10?|#I see.|" +
                    "Oh, and a word of warning: should you decide to leave this room by a method other than the exit " +
                    "portals, you will be teleported to the entrance and have any items that you picked up in the " +
                    "room removed, but the gold coins and Pizazz points you earned will be granted.")

            MageTrainingArena.ENCHANTMENT_GUARDIAN -> listOf(
                "Greetings young one. How can I enlighten you?",
                "In this chamber you will see various piles of shapes. You can pick up these shapes and enchant them " +
                    "using the enchant jewelry spells. By enchanting these shapes, you'll be converting them into " +
                    "orbs which you can be placed in the hole in the centre.|You will get Enchantment Pizazz Points " +
                    "for every ten shapes that you convert and the points you get depends on the level of " +
                    "enchantment spell you cast.|You will get a bonus Pizazz Point for enchanting a certain shape at " +
                    "a certain time, which I will periodically shout out. You will also be rewarded with an item for " +
                    "every so many orbs you deposit into the hole.",
                "As well as the magic experience from casting your enchantment spells, you will get Enchantment " +
                    "Pizazz Points for converting so many shapes, plus a bonus point for converting a shape of the " +
                    "correct type. You should also note that you will occasionally be rewarded with items when you " +
                    "put one of the enchanted orbs in the floor.",
                "Try to guess or keep track of the time between the changes of the best shape to enchant. This means " +
                    "you can be ready to run to a different shape when you know it is about to change. Look out for " +
                    "the dragon gems, as these will get you more Pizazz Points!|#I see.|Oh, and a word of warning: " +
                    "should you decide to leave this room by a method other than the exit portals, you will be " +
                    "teleported to the entrance and have any items that you picked up in the room removed.")

            else -> listOf(
                "Greetings young one. What knowledge do you seek?",
                "Have you noticed all the bones around the room? These are teleported here from all over Gielinor " +
                    "to help clean up the landscape of countless bones left behind from combat. What better use for " +
                    "these bones than to convert them to nutritious fruit to be eaten by you mortals?|You have to use " +
                    "your Bones to Bananas spell to convert the bones and then place them in the holes on the walls " +
                    "to earn Graveyard Pizazz Points.|Unluckily for you, your health will constantly decrease from " +
                    "getting hit by these dropping bones so you will probably want to eat some of the bananas " +
                    "yourself to increase your stay here.",
                "You will get experience from casting your bones to bananas spell and you will get Graveyard Pizazz " +
                    "Points when you put the bananas through the wall. Occasionally you will also be credited with a " +
                    "runestone to help you in your future spell casting.",
                "Different bones will provide you with different numbers of bananas, so try to find the best type. " +
                    "Collect enough points and you will be able to buy a 'Bones to Peaches' spell from my fellow " +
                    "guardian above the entrance hall.|This spell can be used here just like the bones to bananas " +
                    "spell, except this spell will give you even more experience and the peaches will restore more " +
                    "health!|#I see.|Oh, and a word of warning: should you decide to leave this room by a method " +
                    "other than the exit portals, you will be teleported to the entrance and have any items that you " +
                    "picked up in the room removed.")
        }
        val hello = if (npc == MageTrainingArena.TELEKINETIC_GUARDIAN || npc == MageTrainingArena.ALCHEMY_GUARDIAN)
            "Hi." else "Hello."
        plr.newDialogue().player(hello).npc(npc, greeting).then { roomMenu(it, npc, what, rewards, tips) }.open()
    }

    private fun roomMenu(plr: Player, npc: Int, what: String, rewards: String, tips: String) {
        fun answer(question: String, text: String): (Player) -> Unit = { p ->
            val dialogue = p.newDialogue().player(question)
            say(dialogue, npc, text)
            dialogue.then { roomMenu(it, npc, what, rewards, tips) }.open()
        }
        plr.newDialogue().options(
            "What do I have to do in this room?", answer("What do I have to do in this room?", what),
            "What are the rewards?", answer(if (npc == MageTrainingArena.ALCHEMY_GUARDIAN) "How will I be rewarded?"
                                            else "What are the rewards?", rewards),
            "Got any tips that may help me?", answer("Got any tips that may help me?", tips),
            "Thanks, bye!", { p ->
                p.newDialogue().player("Thanks, bye!").npc(npc, "Use what you've learned, young one.").open()
            }).open()
    }

    /**
     * Adds [text] to [dialogue], a box for each '|'-separated part; a part starting with '#' is the player's.
     */
    private fun say(dialogue: DialogueQueueBuilder, npc: Int, text: String) {
        for (part in text.split('|')) {
            if (part.startsWith("#")) dialogue.player(part.substring(1)) else dialogue.npc(npc, part)
        }
    }
}
