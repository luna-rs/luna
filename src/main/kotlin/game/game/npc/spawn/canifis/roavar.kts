package game.npc.spawn.canifis

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Roavar, the innkeeper of The Hair Of The Dog tavern.
 */
val ROAVAR = 1042

/**
 * A pint of Moonlight mead.
 */
val MOONLIGHT_MEAD = 2955

/**
 * What a pint of Moonlight mead costs.
 */
val MEAD_PRICE = 5

npc1(ROAVAR) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Hello there!")
        .npc(ROAVAR, Expression.HAPPY,
             "Greetings traveller. Welcome to 'The Hair Of The Dog'", "Tavern. What can I do you for?")
        .options("Can I buy a beer?", { askForBeer(it) },
                 "Can I hear some gossip?", { gossip(it) },
                 "Can I hear a story?", { story(it) },
                 "Nothing thanks.", {
                     it.newDialogue()
                         .player(Expression.QUIZZICAL, "Nothing thanks.")
                         .npc(ROAVAR, Expression.CONFUSED,
                              "...I don't know why you talked to me if you don't want", "anything then...")
                         .open()
                 })
        .open()
}

npc2(ROAVAR) {
    if (!Bartender.canPay(plr, MEAD_PRICE)) {
        plr.sendMessage("You do not have enough gold to buy a beer.")
    } else {
        pour(plr)
    }
}

/**
 * Roavar offers [plr] a pint of Moonlight mead.
 */
fun askForBeer(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Can I buy a beer?")
        .npc(ROAVAR, Expression.HAPPY,
             "Well that's my speciality! The local brew's named",
             "'Moonlight Mead' and will set you back 5 gold.", "Whaddya say? Fancy a pint?")
        .options("Yes please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Yes please.")
                .then { plr ->
                    if (Bartender.canPay(plr, MEAD_PRICE)) pour(plr) else noCredit(plr)
                }
                .open()
        }, "Actually, no thanks.", {
            it.newDialogue()
                .player("Actually, no thanks.")
                .npc(ROAVAR, "Eh, suit yourself. You're missing out on a genuine", "taste experience.")
                .open()
        })
        .open()
}

/**
 * Roavar sells [plr] a pint of Moonlight mead.
 */
fun pour(plr: Player) {
    if (Bartender.sell(plr, MOONLIGHT_MEAD, MEAD_PRICE)) {
        plr.newDialogue().npc(ROAVAR, "Here ya go pal. Enjoy!").open()
    }
}

/**
 * Roavar points [plr] at the sign behind the bar.
 */
fun noCredit(plr: Player) {
    plr.newDialogue()
        .player(Expression.SAD, "I don't have the money on me right now though... can", "I start a tab?")
        .npc(ROAVAR, Expression.ANGRY, "You see that sign behind me there?")
        .player(Expression.CONFUSED, "The one that says; 'Please Do Not Ask For Credit As",
                "Being Attacked By A Large Angry Werewolf Inn", "Keeper Often Offends'?")
        .npc(ROAVAR, Expression.ANGRY, "Bingo.")
        .open()
}

/**
 * Roavar offers [plr] the village gossip.
 */
fun gossip(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Can I hear some gossip?")
        .npc(ROAVAR, Expression.QUIZZICAL, "Well, I dunno... The village is kind of on the fringe out",
             "here, I dunno how up to date the stuff I hear about", "is...")
        .options("Tell me about this village.", {
            it.newDialogue()
                .player("Tell me about this village.")
                .npc(ROAVAR, Expression.CONFUSED, "You want to know about Canifis? I dunno why, not a",
                     "lot happens here. We're just your typical everyday", "down at earth werewolf folk, after all...")
                .player(Expression.QUIZZICAL, "So... everyone here is a werewolf?")
                .npc(ROAVAR, Expression.HAPPY, "Yep. We are as Zamorak made us!")
                .player(Expression.QUIZZICAL, "You mentioned Zamorak...")
                .npc(ROAVAR, "Yeah, he's great isn't he? Every year we hold a big",
                     "festival to give thanks to Zamorak for keeping us well", "fed and happy here.")
                .npc(ROAVAR, "I hear over in the West they worship Saradomin, but I",
                     "dunno why! What's he ever done to help out us", "werewolves, huh?")
                .npc(ROAVAR, "I hear he has all these crazy followers who say things",
                     "like we shouldn't kill people and eat them! What's up", "with that?")
                .player(Expression.QUIZZICAL, "...So when is this festival?")
                .npc(ROAVAR, Expression.SAD, "Aaaah, not for a good few months yet. Come and ask",
                     "me again nearer to the time, I'll keep some extra meat",
                     "and mead back from the rest for ya pal.")
                .open()
        }, "Tell me about the land of Morytania.", {
            // TODO Vampire Slayer: once the player has killed Count Draynor, Roavar says instead "He hates humans!
            //  Apparently long ago his brother Draynor was trapped in Misthalin and lost much of his powers, and was
            //  recently killed by some human!"
            it.newDialogue()
                .player("Tell me about the land of Morytania.")
                .npc(ROAVAR, "Well, I don't know what to tell you really... This village",
                     "is called Canifis and lies on the border between",
                     "Morytania and Misthalin... so we're kind of on the front",
                     "line if those Saradominists to the west ever decide to")
                .npc(ROAVAR, "attack us... South East of here is the castle of Lord", "Drakan, our master.")
                .player(Expression.QUIZZICAL, "Lord Drakan? Who is that?")
                .npc(ROAVAR, "Ahhh... you must be new to these parts if you haven't",
                     "heard of Lord Drakan! He's the lord of this land, and", "we all pledge allegiance to him.")
                .npc(ROAVAR, "In return for our allegiance, and the tithe of course, he",
                     "keeps our land safe from any invaders and the", "Saradominists who want to kill us all.")
                .player(Expression.QUIZZICAL, "Tithe? What do you mean?")
                .npc(ROAVAR, "Ah, well, in return for his protection, we have to give",
                     "Lord Drakan a share of blood every week. If we don't",
                     "have any to spare from our hunts, then we need to",
                     "pick a member of the village to give their life in return")
                .npc(ROAVAR, "for the blood so that the tithe is fulfilled.")
                .player(Expression.SHOCKED, "You mean you kill someone you know in order to meet", "the tithe???")
                .npc(ROAVAR, "That's right, but only if we haven't managed to get",
                     "enough spare blood from our hunts. It's kind of severe",
                     "if you look at it that way, but frankly, I think the price",
                     "is fair in return for his protection and his tolerance of")
                .npc(ROAVAR, "our village. He could probably kill us all if he wanted us",
                     "gone, so keeping on his good side is worth the sacrifice", "to us. Lucky we're not human really!")
                .player(Expression.CONFUSED, "Why's that?")
                .npc(ROAVAR, "He hates humans! Apparently his brother Draynor is",
                     "trapped in Misthalin, and adventurous little human", "wannabe-heroes keep trying to kill him!")
                .npc(ROAVAR, "Man, I'd hate to be a human around here, that's for",
                     "sure. Drakan would really enjoy hunting them down", "and killing them!")
                .player(Expression.SHOCKED, "Uh... thanks for the info...")
                .open()
        }, "Tell me about the shopkeepers here.", {
            it.newDialogue()
                .player("Tell me about the shopkeepers here.")
                .npc(ROAVAR, Expression.QUIZZICAL, "Hmmm? Why, who did you want to hear the gossip", "about?")
                .then { plr -> shopkeepers(plr) }
                .open()
        }, "Tell me about the temple to the west.", {
            it.newDialogue()
                .player("Tell me about the temple to the west.")
                .npc(ROAVAR, Expression.ANGRY, "Well, I'm not old enough to remember the full story",
                     "behind it, but it was a terrible day for our kingdom", "when it was built there.")
                .npc(ROAVAR, Expression.ANGRY, "Apparently Morytania had a once strong kingdom, with",
                     "lands spreading far further west than they do today,",
                     "and south into the desert, until the day when a sneak", "attack by the hated human forces")
                .npc(ROAVAR, Expression.ANGRY, "Saradomin burned our villages and slaughtered our", "peoples in mass.")
                .npc(ROAVAR, Expression.ANGRY, "They then cursed the river, so that it would burn our",
                     "kind should we touch it! Can you imagine??? To make", "an entire river poisonous to us???")
                .npc(ROAVAR, Expression.ANGRY, "Luckily they seem to have calmed down somewhat",
                     "recently, and tend to stay on their side of the river...",
                     "but the wrong they have done my people will never be",
                     "forgotten, and will never be forgiven. What is worse is")
                .npc(ROAVAR, Expression.ANGRY, "that they then had the nerve to build that gigantic",
                     "statue on the river mouth to mock the slaughter of our",
                     "people and the poisoning of our river! I can understand", "Lord Drakan's hatred for them")
                .npc(ROAVAR, Expression.ANGRY, "it to his extent.")
                .player(Expression.CONFUSED, "So you hate humans too?")
                .npc(ROAVAR, "Absolutely! If I ever met one I would gobble him up",
                     "where he stands! And then chew the bones for dessert!")
                .player(Expression.SHOCKED, "...Um, okay, thanks, bye.")
                .open()
        })
        .open()
}

/**
 * Roavar gossips with [plr] about one of the village's shopkeepers.
 */
fun shopkeepers(plr: Player) {
    plr.newDialogue()
        .options("Sbott the Tanner.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Tell me what you know about Sbott the Tanner.")
                .npc(ROAVAR, "Hey, I won't hear a word said bad about that guy! He's",
                     "an honest and hard-working wolf if I ever met one! He",
                     "charges a lot for his job, but he's one of the best",
                     "tanners I've ever seen - and I'm over four hundred")
                .npc(ROAVAR, "years old! You need stuff tanning, I recommend him!")
                .open()
        }, "Rufus the food seller.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Know anything interesting about Rufus the food seller?")
                .npc(ROAVAR, "Ah yeah... good old Rufus... He's kind of getting on in",
                     "years and doesn't like to come out of his wolf form so",
                     "often, but let me tell you this: that guy is a hunter",
                     "through and through. You seen how much food he")
                .npc(ROAVAR, "catches a day? That takes real dedication! Some of the",
                     "young pups think just because a wolf has a bit of grey",
                     "in his fur then he's past it, but I've seen him put those",
                     "pups to shame in a hunt! He's a real inspiration to us")
                .npc(ROAVAR, "all!")
                .open()
        }, "Barker the clothes seller.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Got anything to share about Barker the clothes seller?")
                .npc(ROAVAR, "Eh, I don't like the guy much, but you can't knock the",
                     "quality of his stock. They're some fine quality threads", "you can get yourself there.")
                .player(Expression.QUIZZICAL, "What's wrong with him?")
                .npc(ROAVAR, "Eh... I can't really tell you to be honest. Something",
                     "about the guy just gets my hackles up everytime he", "opens his yap, know what I mean?")
                .player(Expression.CONFUSED, "No, not really.")
                .npc(ROAVAR, "Lucky for you. I can't really explain it, I just don't like",
                     "the guy. Does great clothes though, you got to give him", "that.")
                .open()
        }, "Fidelio the general store owner.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "I bet you have some juicy gossip about Fidelio the",
                        "general store owner.")
                .npc(ROAVAR, "That nut job? Oh sure, we all know about him. He was",
                     "a real firebrand daredevil when he was younger, always",
                     "taking risks to make himself a quick buck... he got",
                     "himself caught up in the smuggling trade, sneaking over")
                .npc(ROAVAR, "the border into Misthalin and pretending to be a human",
                     "so he could buy exotic items to sell.")
                .player(Expression.QUIZZICAL, "Exotic items? From Misthalin? Like what?")
                .npc(ROAVAR, "Oh man, you name it! Flour... buckets... tinderboxes...",
                     "chisels... he could get the lot! He's been doing it so long", "now though, his nerves are shot.")
                .npc(ROAVAR, "Nowadays he's a disgrace, he can barely string a",
                     "sentence together and jumps at his own shadow. Makes", "you feel ashamed to be a werewolf.")
                .open()
        })
        .open()
}

/**
 * Roavar tells [plr] the story his mother told him.
 */
fun story(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Can I hear a story?")
        .npc(ROAVAR, "A story??? Heh, well the only one I can think of right",
             "now is one my dear old mammy told me as a pup...")
        .npc(ROAVAR, "Now how did it go... Ah yes!")
        .npc(ROAVAR, "Once upon a time a brave young wolf was walking",
             "through a forest, when he came upon a human dressed",
             "all in red. 'Aha!' he thought to himself, 'Here's a nice",
             "easy meal!' But the human talked to him, and as he was")
        .npc(ROAVAR, "always taught to be a polite wolf, he spoke back to it.")
        .npc(ROAVAR, "Well, this cunning human told him that there was a",
             "better meal that would not run away in a house in the",
             "woods. As the brave young wolf could see that this",
             "human was not fully grown, he figured maybe he'd")
        .npc(ROAVAR, "better get a better meal at this house, so he ran as fast",
             "as his paws could carry him to the house the human",
             "told him about... inside he found an old human lying in",
             "bed, and although the meat was a little tough, as the")
        .npc(ROAVAR, "human was older than he thought, he had a good meal",
             "and decided to sleep it off in the house for awhile. He",
             "had not been asleep long though, when he was woken by",
             "a knocking at the door. The human in red had followed")
        .npc(ROAVAR, "him to the house! Suspecting a human trap, the brave",
             "young wolf put on the old humans clothes and jumped",
             "into the bed, so that he could pretend to be human and",
             "escape from this terrible trap! Well now, this human")
        .npc(ROAVAR, "dressed in red came into the house, and pretended to",
             "believe the brave wolf was really a human, and began to",
             "talk to him. But then the human started asking strange",
             "questions of the wolf, because the human knew that it")
        .npc(ROAVAR, "was not a human at all! The brave young wolf decided",
             "to try and escape, for it was only a young human, and",
             "not very strong, so the brave young wolf said he would",
             "eat the human if they did not let him escape! As he said")
        .npc(ROAVAR, "this however, the human in red shouted out at him:",
             "'Aha! You are a wolf!' and as the human shouted this",
             "another bigger human ran into the room from outside!",
             "This bigger human was much stronger and was")
        .npc(ROAVAR, "carrying an axe, and the poor young wolf died in this",
             "terrible trap made for him by the humans.")
        .npc(ROAVAR, Expression.QUIZZICAL, "And do you know what the moral of this story is?")
        .player(Expression.CONFUSED, "Um... no, not really.")
        .npc(ROAVAR, "It's 'Never trust humans' of course! My dear old",
             "mammy told me that story when I was a pup, and I'm",
             "still alive and well to tell it to you today!")
        .npc(ROAVAR, Expression.HAPPY, "Pretty good story huh?")
        .player(Expression.CONFUSED, "Um... yeah. It was great. Really.")
        .open()
}
