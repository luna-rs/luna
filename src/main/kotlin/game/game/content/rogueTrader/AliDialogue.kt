package game.content.rogueTrader

import game.content.rogueTrader.RogueTrader.ALI
import game.content.rogueTrader.RogueTrader.Blackjacks
import game.content.rogueTrader.RogueTrader.Clothes
import game.content.rogueTrader.RogueTrader.Runes
import game.content.rogueTrader.RogueTrader.blackjackNews
import game.content.rogueTrader.RogueTrader.blackjackStock
import game.content.rogueTrader.RogueTrader.carpetDiscount
import game.content.rogueTrader.RogueTrader.clothesNews
import game.content.rogueTrader.RogueTrader.clothesStock
import game.content.rogueTrader.RogueTrader.largeCasketOpened
import game.content.rogueTrader.RogueTrader.rogueTraderStarted
import game.content.rogueTrader.RogueTrader.rogueTraderThanked
import game.content.rogueTrader.RogueTrader.runeDeal
import game.content.rogueTrader.RogueTrader.smallCasketOpened
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * Ali Morrisane's side of Rogue Trader: asking for suppliers, hearing how they sell, and his stocks.
 *
 * @author TheLining
 */
object AliDialogue {

    /**
     * Talk-to Ali.
     */
    fun talk(plr: Player) {
        when {
            !RogueTrader.canStart(plr) -> wares(plr)
            !plr.rogueTraderStarted -> intro(plr)
            else -> plr.newDialogue()
                .npc(ALI, "Hello ${plr.username}. How's the adventuring going?")
                .player("Hi Ali. Not bad. How are you?")
                .npc(ALI, "Still selling.")
                .then { news(it) }
                .open()
        }
    }

    /**
     * What Ali says to anyone not helping him: a look at his everyday wares.
     */
    private fun wares(plr: Player) {
        plr.newDialogue()
            .npc(ALI, "Hello, my friend. Have a look at my wares.")
            .options("No, I'm really too busy.", {
                it.newDialogue().player("No, I'm really too busy.").open()
            }, "Okay.", {
                it.newDialogue().player("Okay.").then { p -> RogueTrader.openShop(p, RogueTrader.GENERAL_STOCK) }.open()
            }).open()
    }

    private fun intro(plr: Player) {
        plr.rogueTraderStarted = true
        val builder = plr.newDialogue()
            .npc(ALI, "Hello ${plr.username}. How's the adventuring going?")
            .player("Hi Ali. Not bad. How are you?")
            .npc(ALI, "Still selling.")
            .player("Anything interesting?")
            .npc(ALI, "For you, always. As a matter of fact I have just", "acquired a fleet of magic carpets, which operate",
                 "throughout the Kharidian desert.")
            .player("Wow, that sounds great, how about your stall?")
            .npc(ALI, "Trade is good, but I would like to expand and", "diversify, but it's proving a little more difficult than I",
                 "initially thought.")
            .player("How so?")
            .npc(ALI, "I think the other merchants in this town are", "conspiring against me.")
            .player("Oh come on! You're just being paranoid now.")
            .npc(ALI, "Well maybe I can't get any new suppliers then because", "I'm Pollnivnian.")
            .player("You're what?")
            .npc(ALI, "From Pollnivneach.")
            .player("Maybe I could help you then?")
            .npc(ALI, "Perhaps you could.")
        if (RogueTrader.canAskSiamun(plr)) {
            builder.npc(ALI, "I have an old friend - a cloth merchant Siamun, who", "lives in Sophanem - Do you know the place?")
                .player("Umm..... Images of cats and priests come to mind. Ah", "yes! There we go, it's the city of the dead South of",
                        "Pollnivneach isn't it?")
                .npc(ALI, "That's the one, well if you could convince him to supply", "me with some of his merchandise, I would be more than",
                     "willing to sell them to the masses ...... for a small", "percentage of course.")
        } else {
            builder.npc(ALI, "My friend in Sophanem... Ahh never mind him, maybe we", "might have a look at him another time.")
                .player("Is there anyone else you want me to talk to on your", "behalf?")
                .npc(ALI, "There's a blackjack seller in Pollnivneach, a cautious", "type of guy, not the usual entrepreneurial sort that I",
                     "would normally deal with, but...")
                .npc(ALI, "I think that he has a product with real potential...")
                .player("What? .... Blackjacks?")
                .npc(ALI, "Yes, but at the moment the product isn't polished", "enough, if you could convince him to create a larger",
                     "variety of weapon I would gladly stock his products.")
        }
        builder.options("So what's in this for me?", {
            whatsInIt(it) { p -> runeIdea(p) }
        }, "Ok I'll get on to it.", {
            it.newDialogue().player("Ok I'll get on to it.").then { p -> runeIdea(p) }.open()
        }).open()
    }

    /**
     * Ali asks for a rune supplier, if [plr] knows Aubury.
     */
    private fun runeIdea(plr: Player) {
        if (!RogueTrader.canAskAubury(plr)) {
            goodbye(plr)
            return
        }
        plr.newDialogue()
            .npc(ALI, "I think I need just one more product type, something", "magical ... now what could I stock that's magical, has",
                 "large demand and a pretty good profit margin...")
            .player("How about runes?")
            .npc(ALI, "Yes, that would be perfect, but who could I get that", "would supply? There really seems to be a gap in the",
                 "market in the desert but I don't know anyone in that", "line of business.")
            .player("Hang on, I know somebody called Aubury, decent fella. I", "helped him out a while back. Perhaps I could approach",
                    "him about the idea.")
            .npc(ALI, "Oh yes, the rune seller from Varrock. That would be", "perfect. He's far enough away that he wouldn't end up",
                 "competing with himself.")
            .player("What do you mean?")
            .npc(ALI, "Well the rune market is a limited one, if I were to sell", "his runes from a location nearby his shop, then I would",
                 "take part of his market share so he would in fact not", "benefit from me selling runes.")
            .player("Ah ha I think I know where this is going. So because", "you're located far enough away from him you will take up",
                    "someone else's share in the market and Aubury will", "make more money as a result.")
            .npc(ALI, "Very well anticipated.")
            .options("So what's in this for me?", {
                whatsInIt(it) { p -> goodbye(p) }
            }, "Ok I'll see what I can do.", {
                it.newDialogue().player("Ok I'll see what I can do.").then { p -> goodbye(p) }.open()
            }).open()
    }

    /**
     * "So what's in this for me?": the carpet discount, the first time.
     */
    private fun whatsInIt(plr: Player, next: (Player) -> Unit) {
        val builder = plr.newDialogue()
            .player("What's in this for me?")
            .npc(ALI, "I thought you wanted to help.")
            .player("Of course, but everyone seems to be benefiting here but", "me.")
            .npc(ALI, "What do you mean?")
            .player("Well the trader gets their product sold, you get your", "cut, but what do I get?")
            .npc(ALI, "You get access to more products. Is that not reward", "enough?")
            .npc(ALI, "Hmmm... Perhaps not.")
        if (plr.carpetDiscount) {
            builder.npc(ALI, "Didn't we also agree that you would get discounted", "travel on my magic carpet network?")
                .player("Oh ya, I forgot.")
                .npc(ALI, "So what's your problem then?")
                .player("I don't have one I guess.")
                .npc(ALI, "Good, that's what I like to hear. Now where were we?")
        } else {
            plr.carpetDiscount = true
            builder.npc(ALI, "I'll tell you what then, how about I let you have", "discounted flights on my magic carpet rides?")
                .player("Wow that would be great.")
                .npc(ALI, "Fair enough so, we have a win, win, win situation on", "our hands then.")
        }
        builder.then(next).open()
    }

    /**
     * Ali sends [plr] on their way, selling them some junk first.
     */
    private fun goodbye(plr: Player) {
        plr.newDialogue()
            .npc(ALI, "Well you had better get going, you've a fair bit of", "travelling ahead of you. Unless you want to make a small",
                 "purchase from me first?")
            .then {
                RogueTrader.sellJunk(it)
                it.newDialogue().options("No I'm really too busy.", { p ->
                    p.newDialogue().player("No, I'm really too busy.").open()
                }, "Ok", { p ->
                    RogueTrader.openShop(p, RogueTrader.GENERAL_STOCK)
                }).open()
            }.open()
    }

    /**
     * How Ali's suppliers are doing, then his stock.
     */
    private fun news(plr: Player) {
        when {
            plr.runeDeal == Runes.AGREED -> {
                plr.runeDeal = Runes.DELIVERED
                plr.newDialogue()
                    .npc(ALI, "Good job on getting the rune shipments sorted. I'm just", "receiving the first of them. Though there seems to be a",
                         "funny sort of locking mechanism on them, which I", "haven't completely figured out.")
                    .player("No problem. I might have a look at it later.")
                    .then { menu(it) }.open()
            }
            plr.blackjackNews -> {
                plr.blackjackNews = false
                plr.newDialogue()
                    .npc(ALI, "Fantastic job on the blackjacks. I started getting the first", "batches of them a short time ago, and they've being",
                         "flying off the shelves, although the demand seems to be", "dropping off a little.")
                    .player("Not too badly, I hope.")
                    .npc(ALI, "No, no, they're still selling fine, but if you could", "perhaps convince the blackjack man to send a different",
                         "variety the next time you see him, I would be more than", "happy.")
                    .player("I should like to have a look at them, at some stage.")
                    .then { thanks(it) }.open()
            }
            plr.clothesNews -> {
                plr.clothesNews = false
                val builder = plr.newDialogue()
                if (plr.clothesStock == Clothes.MENAPHITE) {
                    builder.npc(ALI, "Siamun's clothes seem to have really struck a chord with", "the locals in Al Kharid. Menaphite culture seems to be",
                                "taking off here, rumour has it that one of Prince Ali's", "servants bought him one of Siamun's robes.")
                } else {
                    builder.npc(ALI, "Siamun's clothes seem to have really struck a chord with", "the locals in Al Kharid.")
                }
                builder.player("Wow, that sounds positive.")
                    .npc(ALI, "Quite, if I don't say so myself. But in order to keep", "things fresh, the next time you're in Sophanem, could",
                         "you ask that cranky old so-and-so Siamun to produce a", "new line of clothing?")
                    .player("Sure, why not. The Sultan himself might even buy some", "of the new range.")
                    .npc(ALI, "That would be the day.")
                    .then { thanks(it) }.open()
            }
            RogueTrader.allSuppliers(plr) && !plr.rogueTraderThanked -> thanks(plr)
            else -> menu(plr)
        }
    }

    /**
     * Once all three suppliers are sending goods, Ali thanks [plr] with the carpet discount.
     */
    private fun thanks(plr: Player) {
        if (!RogueTrader.allSuppliers(plr) || plr.rogueTraderThanked) {
            return
        }
        plr.rogueTraderThanked = true
        plr.carpetDiscount = true
        plr.newDialogue()
            .npc(ALI, "${plr.username}, I would like to commend you on all the", "selfless help you have given me.")
            .player("It was nothing, really.")
            .npc(ALI, "I think you underestimate your efforts. Anyway, as a", "token of my gratitude I have told all my magic carpet",
                 "attendants to allow you to travel on the carpets at a", "discounted rate.")
            .player("Really, Ali, there is no need.")
            .npc(ALI, "I insist.")
            .then { menu(it) }.open()
    }

    /**
     * Ali's stock, and while suppliers are missing, who to see next.
     */
    private fun menu(plr: Player) {
        val options = ArrayList<DialogueOption>()
        if (plr.blackjackStock != Blackjacks.NONE) {
            options += DialogueOption("I would like to have a look at your selection of blackjacks.") {
                it.newDialogue().npc(ALI, "Hang on a second while I get my stock of blackjacks out.")
                    .then { p -> RogueTrader.openBlackjacks(p) }.open()
            }
        }
        if (plr.clothesStock != Clothes.NONE) {
            options += DialogueOption("I would like to have a look at your selection of clothes.") {
                it.newDialogue().npc(ALI, "Hang on a second while I get the garments.")
                    .then { p -> RogueTrader.openClothes(p) }.open()
            }
        }
        if (plr.runeDeal == Runes.DELIVERED) {
            options += DialogueOption("I would like to have a look at your selection of runes.") { runes(it) }
        }
        if (RogueTrader.allSuppliers(plr)) {
            options += DialogueOption("I would like to have a look at your general stock.") {
                RogueTrader.openShop(it, RogueTrader.GENERAL_STOCK)
            }
            plr.newDialogue().options(options).open()
            return
        }
        options += DialogueOption("Who did you want me to talk to again?") { reminders(it) }
        options += DialogueOption("Ya, I'll get to it soon.") {
            it.newDialogue().player("Ya, I'll get to it soon.").then { p -> goodbye(p) }.open()
        }
        plr.newDialogue()
            .npc(ALI, "Is there any chance of getting you to look up a merchant", "to supply me?")
            .options(options).open()
    }

    /**
     * Who Ali still wants [plr] to see.
     */
    private fun reminders(plr: Player) {
        val builder = plr.newDialogue().player("Who did you want me to talk to again?")
        if (plr.clothesStock == Clothes.NONE && RogueTrader.canAskSiamun(plr)) {
            builder.npc(ALI, "Go see Siamun in Sophanem about some clothes.")
                .player("Right, so I'll go see Siamun of Sophanem.")
                .npc(ALI, "Good, good.")
        }
        if (plr.runeDeal == Runes.NONE && RogueTrader.canAskAubury(plr)) {
            builder.npc(ALI, "Could you talk to your friend Aubury in Varrock about", "supplying me with runes?")
                .player("Hmm, that could work out quite well. Varrock is always", "quite busy, so I might try to get a couple of other",
                        "things done while I'm there.")
                .npc(ALI, "Don't tarry there too long, I'm quite anxious to get a", "rune store open before anyone else in this area.")
        }
        if (plr.blackjackStock == Blackjacks.NONE) {
            builder.npc(ALI, "In Pollnivneach go and talk to the blackjack seller.", "Convince him to supply me with his produce.")
                .player("Any ideas how I should go about that?")
                .npc(ALI, "You're a bright chap. I'm sure you'll work something", "out.")
                .player("I'll get to it right away.")
        }
        builder.open()
    }

    /**
     * Ali's runes: the caskets until each has been opened once.
     */
    private fun runes(plr: Player) {
        val builder = plr.newDialogue().npc(ALI, "Hang on a second.")
        if (!plr.smallCasketOpened && !plr.largeCasketOpened) {
            builder.npc(ALI, "Well at the moment I don't exactly have a selection of", "runes.")
                .player("You don't?")
                .npc(ALI, "Those boxes from your friend Aubury have a very odd", "locking mechanism.")
                .player("You have managed to get them off?")
                .npc(ALI, "Not exactly.")
                .player("What do you mean by 'not exactly'?")
                .npc(ALI, "What I mean by 'not exactly' is 'no, I haven't managed", "to get the lock off'.")
                .player("Oh.")
                .npc(ALI, "I was kind of hoping that you would have a look at", "them. There are two distinctive types of caskets one with",
                     "a simple locking mechanism and another, which", "ummm... looks a little tricky to say the least.")
        }
        builder.options("Try opening a small casket of runes", {
            casket(it, RuneCasket.SMALL, "I think I'll have a go at opening a small casket of runes.")
        }, "Try opening a large casket of runes", {
            casket(it, RuneCasket.LARGE, "I'm feeling lucky, let's have a look at opening one of those large caskets.")
        }, "On second thoughts I don't really need any runes.", {
            it.newDialogue().player("On second thoughts I don't really need any runes.").open()
        }).open()
    }

    /**
     * A casket of runes: once opened, its stock; otherwise the three ways in.
     */
    private fun casket(plr: Player, casket: RuneCasket, line: String) {
        if (casket.opened(plr)) {
            RogueTrader.openShop(plr, casket.shop)
            return
        }
        plr.newDialogue()
            .player(line)
            .options("Force box.", { casket.force(it) },
                     "Pick lock.", { casket.pick(it) },
                     "Examine lock.", { casket.examine(it) }).open()
    }
}
