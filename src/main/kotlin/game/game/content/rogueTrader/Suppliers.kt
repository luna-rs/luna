package game.content.rogueTrader

import api.predef.*
import api.predef.ext.*
import game.content.rogueTrader.RogueTrader.AUBURY
import game.content.rogueTrader.RogueTrader.BLACKJACK_SELLER_SHOWN
import game.content.rogueTrader.RogueTrader.Blackjacks
import game.content.rogueTrader.RogueTrader.Clothes
import game.content.rogueTrader.RogueTrader.Runes
import game.content.rogueTrader.RogueTrader.SIAMUN
import game.content.rogueTrader.RogueTrader.STREET_URCHIN
import game.content.rogueTrader.RogueTrader.Urchin
import game.content.rogueTrader.RogueTrader.blackjackNews
import game.content.rogueTrader.RogueTrader.blackjackStock
import game.content.rogueTrader.RogueTrader.clothesNews
import game.content.rogueTrader.RogueTrader.clothesStock
import game.content.rogueTrader.RogueTrader.metBlackjackSeller
import game.content.rogueTrader.RogueTrader.rogueTraderStarted
import game.content.rogueTrader.RogueTrader.runeDeal
import game.content.rogueTrader.RogueTrader.urchinStage
import game.skill.Skills
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * The three suppliers Ali Morrisane wants: Aubury's runes, the Blackjack seller's blackjacks and Siamun's clothes.
 *
 * @author TheLining
 */
object Suppliers {

    /**
     * Aubury's line for a player who has to tell him about Ali, if any.
     */
    const val PROPOSAL = "Actually, I'm here with a business proposal from Ali Morrisane."

    /**
     * Whether Aubury can be asked about Ali's rune stall.
     */
    fun canPropose(plr: Player) =
        plr.rogueTraderStarted && plr.runeDeal == Runes.NONE && RogueTrader.canAskAubury(plr)

    /**
     * Whether Aubury has agreed but Ali hasn't heard yet, which Aubury mentions first.
     */
    fun auburyWaiting(plr: Player) = plr.runeDeal == Runes.AGREED

    /**
     * Aubury reminds [plr] to tell Ali, before his usual greeting.
     */
    fun auburyReminder(builder: DialogueQueueBuilder, plr: Player): DialogueQueueBuilder =
        builder.npc(AUBURY, "Oh, sorry ${plr.username}, I didn't recognise you for a second,", "you're back again so soon. Have you spoken to Ali",
                    "Morrisane yet about the runes?")
            .player("Umm... not yet, I'll try to get around to doing that", "soon.")
            .npc(AUBURY, "Great! Now, do want to buy some runes?")

    /**
     * Ali's proposal to Aubury.
     */
    fun propose(plr: Player) {
        plr.newDialogue()
            .player(PROPOSAL)
            .npc(AUBURY, "Ali who?")
            .player("Ali Morrisane, a salesman of great repute based in Al", "Kharid.")
            .npc(AUBURY, "What is this proposal then?")
            .player("He wants to set up a rune-selling franchise in the", "markets of Al Kharid and he wants you to supply him",
                    "with the stock.")
            .npc(AUBURY, "Why doesn't he get his own runes and sell them?")
            .player("His talents don't lie in that area. What he's good at is", "selling things, and lots of them.")
            .npc(AUBURY, "Hmm, interesting... So let's get this straight, he wants", "me to supply him with runes, which he will sell, and",
                 "send me the profits?")
            .player("Nearly. He will charge a small commission on the runes.")
            .npc(AUBURY, "Do you think that it will work? Will people actually buy", "these runes off him?")
            .player("I wouldn't be surprised. Supply and demand are his", "rune essence and ore.")
            .npc(AUBURY, "Now this is very important, is he trustworthy?")
            .player("Yes, I believe so. Trust is a key part of commerce and", "something, which I doubt he would neglect.")
            .npc(AUBURY, "Fair enough, I'll trust your judgement.")
            .player("So you'll supply him with runes then?")
            .npc(AUBURY, "Well if he's half as good a salesman as you suggest I", "should have no problem in funding my research and",
                 "maybe even redecorating this place.")
            .then { it.runeDeal = Runes.AGREED }
            .open()
    }

    private val DYES = listOf(1763, 1765, 1767, 1769, 1771, 1773)

    /**
     * Cowhide, wool, grey wolf fur and snakeskin: three of one kind make a set of pelts.
     */
    private val PELTS = listOf(1739, 1737, 958, 6289)

    /**
     * Talk-to Siamun.
     */
    fun siamun(plr: Player) {
        if (!plr.rogueTraderStarted || !RogueTrader.canAskSiamun(plr)) {
            plr.newDialogue()
                .player("Hello.")
                .npc(SIAMUN, "Do you need something?")
                .player("No.")
                .npc(SIAMUN, "Well I have lots to do, so good day.")
                .open()
            return
        }
        when {
            plr.clothesNews -> plr.newDialogue()
                .npc(SIAMUN, "Hello adventurer. Have you checked with Ali M whether", "my garments are selling or not?")
                .player("Not yet, I have a few things to do first.")
                .npc(SIAMUN, "Well I would be very much obliged it you could have a", "talk with him sooner rather than later, I'm not all",
                     "that sure of the trustworthiness of those carpet men.")
                .player("Don't worry, I'll get to it.")
                .open()
            plr.clothesStock == Clothes.NONE -> plr.newDialogue()
                .player("Hello, Siamun?")
                .npc(SIAMUN, "What?")
                .player("So you are Siamun then?")
                .npc(SIAMUN, "Yes now what do you want?")
                .player("Wow, are you this friendly to everyone?")
                .npc(SIAMUN, "Just you. Now what do you want?")
                .player("A mutual friend of ours, Ali Morrisane, wants you to", "supply his stall in Al Kharid with your garments.")
                .npc(SIAMUN, "Now why would I do that?")
                .player("Well for one, you'd probably sell more clothes and once", "the people of Al Kharid see your amazing works of",
                        "tailoring, I'm sure they'd spread the word of your clothes", "far and wide.")
                .npc(SIAMUN, "Interesting, but I would need more materials to", "accomplish what you ask of me.")
                .player("Well than what do you need?")
                .npc(SIAMUN, "Either get me 3 pots of dye, or 3 animal pelts of the", "same variety and whatever you do, don't bring me",
                     "banknotes!")
                .options("What do you mean by animal pelt?", {
                    it.newDialogue()
                        .player("What do you mean by animal pelt?")
                        .npc(SIAMUN, "Just get me 3 cowhides, 3 wolf or snake skins or the", "shearings of 3 sheep. Simpler still get me 3 pots of",
                             "dye and, remember, I won't accept banknotes.")
                        .then { p -> materials(p, dyes = true, pelts = true) }.open()
                }, "Ok, I'll get you the materials.", {
                    materials(it, dyes = true, pelts = true)
                }).open()
            else -> {
                val wantsDye = plr.clothesStock == Clothes.DESERT
                val builder = plr.newDialogue()
                    .player("Hello Siamun.")
                    .npc(SIAMUN, "Hello adventurer. How are sales going?")
                    .player("Not too bad, Ali was well impressed by the reaction and", "demand they had initially but he's a little concerned",
                            "about how the sales have dropped off of recent.")
                    .npc(SIAMUN, "Hmmm... Maybe I could produce a different range of", "products.")
                if (wantsDye) {
                    builder.npc(SIAMUN, "I'll tell you what, get me 3 pots of dye and I'll change", "the type of garments I'm sending to Ali and, remember,",
                                "I won't accept banknotes.")
                        .then { materials(it, dyes = true, pelts = false) }.open()
                } else {
                    builder.npc(SIAMUN, "I tell you what, get me 3 animal pelts of the same", "variety and I'll change the shipments around and,",
                                "remember, I won't accept banknotes.")
                        .options("What do you mean by animal pelt?", {
                            it.newDialogue()
                                .player("What do you mean by animal pelt?")
                                .npc(SIAMUN, "Just get me 3 cowhides, 3 wolf or snake skins or the", "shearings of 3 sheep and, remember, I won't accept",
                                     "banknotes.")
                                .then { p -> materials(p, dyes = false, pelts = true) }.open()
                        }, "Ok, I'll get you the materials.", {
                            materials(it, dyes = false, pelts = true)
                        }).open()
                }
            }
        }
    }

    /**
     * Siamun asks whether [plr] has the materials on them, and takes them if so. Dye makes Menaphite clothes; pelts
     * make desert clothes.
     */
    private fun materials(plr: Player, dyes: Boolean, pelts: Boolean) {
        val builder = plr.newDialogue()
            .player("Ok, I'll get you the materials.")
            .npc(SIAMUN, "Unless you've got the materials on you?")
        val dyeCount = DYES.sumOf { plr.inventory.computeAmountForId(it) }
        val pelt = PELTS.firstOrNull { plr.inventory.computeAmountForId(it) >= 3 }
        when {
            dyes && dyeCount >= 3 -> builder.player("Yup, I've got what you want with me.").then {
                var left = 3
                for (id in DYES) {
                    val take = minOf(left, it.inventory.computeAmountForId(id))
                    if (take > 0) {
                        it.inventory.remove(Item(id, take))
                        left -= take
                    }
                }
                supplied(it, Clothes.MENAPHITE, "the dye")
            }
            pelts && pelt != null -> builder.player("Yup, I've got what you want with me.").then {
                it.inventory.remove(Item(pelt, 3))
                supplied(it, Clothes.DESERT, "the animal pelts")
            }
            else -> builder.player("Ummm.. No.")
                .npc(SIAMUN, "Keh! Come back when you have what I need.")
        }
        builder.open()
    }

    private fun supplied(plr: Player, clothes: Int, given: String) {
        plr.clothesStock = clothes
        plr.clothesNews = true
        plr.newDialogue()
            .text("You give the merchant $given.")
            .npc(SIAMUN, "Thank you adventurer. I will get my goods up to that", "merchant thief - Ali Morrisane as quickly as possible.")
            .player("Think nothing of it. I'm looking forward to seeing what", "you've produced for the Al Kharid market.")
            .open()
    }

    /**
     * Talk-to the Blackjack seller.
     */
    fun blackjackSeller(plr: Player, seller: Npc) {
        val builder = plr.newDialogue().npc(BLACKJACK_SELLER_SHOWN, "Hello. Could I interest you in a Blackjack?")
        if (!plr.rogueTraderStarted) {
            builder.open()
            return
        }
        when {
            plr.urchinStage == Urchin.PAID -> builder
                .player("Hi, I'm back.")
                .npc(BLACKJACK_SELLER_SHOWN, "What is it now?")
                .then { robbery(it, seller) }
                .open()
            plr.urchinStage == Urchin.ROBBED -> builder
                .npc(BLACKJACK_SELLER_SHOWN, "That pesky little urchin has done it again! I can't believe", "it he's managed to rob me again.")
                .player("Well maybe you should modify the blackjack to have", "better defensive qualities.")
                .npc(BLACKJACK_SELLER_SHOWN, "I might as well. Next time I'll get him.")
                .player("So I take it you will be supplying Ali Morrisane with", "the new variety of blackjack then?")
                .npc(BLACKJACK_SELLER_SHOWN, "Yes, yes. I'll start shipping it by carpet as soon as the", "first batch is ready.")
                .then {
                    it.urchinStage = Urchin.NONE
                    it.blackjackStock = Blackjacks.DEFENSIVE
                    it.blackjackNews = true
                }
                .open()
            plr.blackjackNews -> builder
                .npc(BLACKJACK_SELLER_SHOWN, "Oh sorry ${plr.username}. I mistook you for another customer.")
                .player("No problem.")
                .npc(BLACKJACK_SELLER_SHOWN, "Have you checked with Ali Morrisane whether my", "blackjacks are selling or not?")
                .player("Not yet, I have a few thing to do first.")
                .npc(BLACKJACK_SELLER_SHOWN, "Well I would be very grateful if you could have a talk", "with him sooner rather than later, I am still a little",
                     "nervous about the new variation of my blackjack.")
                .player("Don't worry, I'll get to it.")
                .open()
            else -> {
                if (!plr.metBlackjackSeller) {
                    plr.metBlackjackSeller = true
                    proposal(builder)
                }
                builder.player("Look will you create a larger variety of blackjacks or", "not?")
                    .npc(BLACKJACK_SELLER_SHOWN, "I don't know.")
                    .player("What do I have to do to convince you that you should", "make a larger variety?")
                    .npc(BLACKJACK_SELLER_SHOWN, "Well I suppose if you could demonstrate to me that they", "weren't up to the job.")
                    .options("Hmm... I'll have to think of a cunning plan.", {
                        it.newDialogue()
                            .player("Hmm... I'll have to think of a cunning plan.")
                            .npc(BLACKJACK_SELLER_SHOWN, "What was that?")
                            .player("What was what? Oh I must have been thinking out loud", "again, sorry.")
                            .then { p -> urchinCalls(p, seller) }.open()
                    }, "I bet you couldn't knock me out with your blackjack.", {
                        challenge(it, seller)
                    }).open()
            }
        }
    }

    private fun proposal(builder: DialogueQueueBuilder) {
        builder.player("Can I interest you in a proposal?")
            .npc(BLACKJACK_SELLER_SHOWN, "What are we talking about here? Nothing dodgy I hope. I", "don't like anything dodgy, I had enough of that sort of",
                 "thing when the bandits and those rogue Menaphites were", "running things here, so I packed up my things and left.")
            .player("No no nothing of the sort, a friend of mine Ali", "Morrisane, have you ever heard of him?")
            .npc(BLACKJACK_SELLER_SHOWN, "Have I what? He's my idol, he's the greatest salesman in", "all of Gielinor. Word has it that he even sold...")
            .player("...yeah yeah, sand to the Bedabin. Hmpf!! I have heard", "that story more times that I care for.. Anyway he has a",
                    "proposal for you...")
            .npc(BLACKJACK_SELLER_SHOWN, "I'm all ears. Does he want to take me on as an", "apprentice?")
            .player("No no nothing of the sort.")
            .npc(BLACKJACK_SELLER_SHOWN, "Oh...?")
            .player("He sees quite a bit of potential in your product.")
            .npc(BLACKJACK_SELLER_SHOWN, "The urchins of the town do too, they're always trying to", "steal my products.")
            .player("Yes, but Ali says if he were to stock your products he", "would need a larger variation of product.")
            .npc(BLACKJACK_SELLER_SHOWN, "I don't know. I'm quite happy with the product that I'm", "making at the moment.")
            .player("Hmm.. Ali did voice some concerns about you. He said", "you were an over cautious individual and ...")
            .npc(BLACKJACK_SELLER_SHOWN, "He did?")
    }

    /**
     * The street urchin nearest [near], if any.
     */
    private fun urchinNear(near: Npc): Npc? =
        world.npcs.filter { it.id == STREET_URCHIN && it.state == EntityState.ACTIVE && it.position.isWithinDistance(near.position, 15) }
            .minByOrNull { it.position.computeLongestDistance(near.position) }

    private fun urchinCalls(plr: Player, seller: Npc) {
        plr.urchinStage = Urchin.CALLED
        val urchin = urchinNear(seller)
        urchin?.interact(plr)
        urchin?.speak("Psst... ${plr.username} Come here.")
        plr.newDialogue().npc(STREET_URCHIN, "Psst... ${plr.username} Come here.").open()
    }

    /**
     * Talk-to the street urchin, who only has something to say while the blackjack seller's robbery is on.
     */
    fun urchin(plr: Player, urchin: Npc) {
        when (plr.urchinStage) {
            Urchin.CALLED -> urchinOffer(plr, urchin)
            Urchin.PAID -> plr.newDialogue()
                .npc(STREET_URCHIN, "Now walk up to the merchant, engage him in conversation", "and leave the rest up to me.")
                .open()
            Urchin.ROBBED -> plr.newDialogue()
                .npc(STREET_URCHIN, "Hah! Did you see the face on the blackjack seller! Ha", "boyo! I had to move quick to get out of the way of his",
                     "blackjack.")
                .player("You did well. I thought he hit you when swung his", "blackjack at you.")
                .npc(STREET_URCHIN, "Ya he nearly did. Boyo, does my head still hurt.")
                .player("You mean he hit you?")
                .npc(STREET_URCHIN, "Barely, the trick is in moving with the blow.")
                .player("I might bear that in mind for next time.")
                .open()
        }
    }

    private fun urchinOffer(plr: Player, urchin: Npc) {
        val seller = world.npcs.firstOrNull {
            it.id == RogueTrader.BLACKJACK_SELLER && it.position.isWithinDistance(urchin.position, 10)
        }
        val builder = plr.newDialogue()
        if (seller != null) {
            builder.npc(STREET_URCHIN, "Word has it that you're trying to get the blackjack man", "to change his kit.")
                .player("How did you know that?")
        } else {
            builder.npc(STREET_URCHIN, "Hey there I heard that you were trying to get the", "blackjack man to change his gear.")
                .player("You did?")
        }
        builder.npc(STREET_URCHIN, "Do you forget who you're talking to? I'm the fastest and", "sneakiest urchin in this town.")
            .player("Ok, so what's it to you then?")
            .npc(STREET_URCHIN, "Well it suits my purpose nearly as much as yours if the", "blackjack seller produces a wider variety of wares.")
            .player("How so?")
            .npc(STREET_URCHIN, "Well chances are, he'll produce some even higher quality", "items for me to steal")
            .player("Aha so if you can rob the blackjack seller and get away", "with it, it should prove to him that his blackjack isn't up",
                    "to the job?")
            .npc(STREET_URCHIN, "There's hope for you yet.")
            .npc(STREET_URCHIN, "Seeing as we've worked successfully together in the past", "I will only charge you 100 gold.")
        if (plr.inventory.computeAmountForId(RogueTrader.COINS) < RogueTrader.URCHIN_FEE) {
            builder.player("I don't have that much money on me.")
                .npc(STREET_URCHIN, "Pity, maybe we can do it again sometime.")
                .open()
            return
        }
        builder.options("Woah! That's far too much.", {
            it.newDialogue().player("Woah! That's far too much.").open()
        }, "Fair enough, here you go.", {
            it.newDialogue().player("Fair enough, here you go.").then { p ->
                if (p.inventory.remove(Item(RogueTrader.COINS, RogueTrader.URCHIN_FEE))) {
                    p.urchinStage = Urchin.PAID
                    p.newDialogue().npc(STREET_URCHIN, "Now walk up to the merchant, engage him in conversation", "and leave the rest up to me.")
                        .open()
                }
            }.open()
        }).open()
    }

    /**
     * The urchin snatches a blackjack and the seller swings at him.
     */
    private fun robbery(plr: Player, seller: Npc) {
        val urchin = urchinNear(seller)
        urchin?.interact(seller)
        urchin?.speak("Yoink!")
        world.scheduleOnce(2) {
            seller.speak("Hey come back you thief!")
            if (urchin != null) {
                seller.interact(urchin)
            }
            seller.animation(Animation(seller.combat.getAttackAnimation(CombatDamageType.MELEE)))
            plr.urchinStage = Urchin.ROBBED
            plr.newDialogue()
                .text("The blackjack seller takes a swing at the urchin, but the", "blow only glances off the urchin's head.")
                .open()
        }
    }

    /**
     * The bet: two blows from the seller's blackjack, each a Defence roll to stay standing.
     */
    private fun challenge(plr: Player, seller: Npc) {
        plr.newDialogue()
            .player("I bet you couldn't knock me out with your blackjack.")
            .npc(BLACKJACK_SELLER_SHOWN, "I reckon I could.")
            .player("I'll tell you what, if you can't knock me out with one", "blow, you should make better blackjacks.")
            .npc(BLACKJACK_SELLER_SHOWN, "Nah, I don't like the sounds of that. It leaves a lot up", "to chance.")
            .player("Ok, ok, how about two blows to the head?")
            .npc(BLACKJACK_SELLER_SHOWN, "Are you sure? I could possibly kill you?")
            .options("Umm... on second thoughts I might give it a skip.", { chickenOut(it) },
                     "I'm sure. No pain no gain. Grrr!", {
                it.newDialogue()
                    .player("I'm sure. No pain no gain. Grrr!")
                    .npc(BLACKJACK_SELLER_SHOWN, "You adventurers are crazy.")
                    .then { p -> blow(p, seller, first = true) }.open()
            }).open()
    }

    private fun chickenOut(plr: Player) {
        plr.newDialogue()
            .player("Umm... on second thoughts I might give it a skip. I just", "had my hair done, I wouldn't want to mess it up.")
            .npc(BLACKJACK_SELLER_SHOWN, "Phew! Sighs with relief. At last the voice of reason", "speaks.")
            .npc(BLACKJACK_SELLER_SHOWN, "Now if you don't mind I'll be getting back to work.", "Thank you.")
            .open()
    }

    private val STILL_STANDING = listOf(
        arrayOf("Ow! Ha! You didn't knock me down."),
        arrayOf("Pah, you didn't even draw blood."),
        arrayOf("Yeeeoouch that hurt! I'm still standing though. You", "can't knock me down.")
    )

    /**
     * One blow from the seller. Failing the roll knocks the player down for 3 damage, and the bet is off.
     */
    private fun blow(plr: Player, seller: Npc, first: Boolean) {
        seller.interact(plr)
        seller.animation(Animation(seller.combat.getAttackAnimation(CombatDamageType.MELEE)))
        world.scheduleOnce(1) {
            if (plr.state == EntityState.INACTIVE) {
                return@scheduleOnce
            }
            if (!Skills.success(100, 255, plr.defence.level)) {
                plr.damage(3)
                plr.sendMessage("The blackjack knocks you to the ground.")
                return@scheduleOnce
            }
            val standing = plr.newDialogue().player(*STILL_STANDING.random())
            if (first) {
                standing.options("Go again.", {
                    it.newDialogue()
                        .npc(BLACKJACK_SELLER_SHOWN, "You really are truly mad.")
                        .player("Are you going to hit me again? I really want to get this", "over with.")
                        .then { p -> blow(p, seller, first = false) }.open()
                }, "Chicken out.", { chickenOut(it) }).open()
            } else {
                standing.npc(BLACKJACK_SELLER_SHOWN, "Either your head is thicker than it looks or else I need", "to rework my blackjack.")
                    .player("Umm... definitely the blackjack. You just about fight off", "another bout of dizziness and nausea.")
                    .npc(BLACKJACK_SELLER_SHOWN, "I suppose I'll just have to modify the blackjack so that it", "has a better attack.")
                    .player("Great, so I take it you will be supplying Ali Morrisane", "with the new variety of blackjack then?")
                    .npc(BLACKJACK_SELLER_SHOWN, "Yes, yes. I'll start shipping it by carpet as soon as the", "first batch is ready.")
                    .then {
                        it.blackjackStock = Blackjacks.OFFENSIVE
                        it.blackjackNews = true
                    }
                    .open()
            }
        }
    }
}
