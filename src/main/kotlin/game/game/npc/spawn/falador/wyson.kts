package game.npc.spawn.falador

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.util.RandomUtils
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Wyson the gardener.
 */
val WYSON_ID = 36

/**
 * The coins item ID.
 */
val COINS = 995

/**
 * The woad leaf item ID.
 */
val WOAD_LEAF = 1793

/**
 * The Giant Mole's parts, which Wyson trades for bird nests.
 */
val MOLE_PARTS = setOf(7416, 7418)

/**
 * The nest Wyson gives for each mole part, by weight: a seed nest, a ring nest or an empty nest.
 */
val MOLE_PART_NESTS = mapOf(7413 to 72.0, 5074 to 20.0, 5075 to 8.0)

/**
 * Opens the dialogue where Wyson turns down an offer that is too low.
 */
fun lowOffer(plr: Player, npc: Npc, offer: String) {
    plr.newDialogue()
        .player(offer)
        .npc(
            npc.id,
            Expression.ANGRY,
            "No no, that's far too little.",
            "Woad leaves are hard to get.",
            "I used to have plenty but someone",
            "kept stealing them off me!"
        )
        .open()
}

/**
 * Sells [plr] woad leaves for [price] coins, if they can afford it.
 */
fun buyLeaves(plr: Player, npc: Npc, price: Int, leaves: Int) {
    if (plr.inventory.computeAmountForId(COINS) < price) {
        plr.newDialogue()
            .player(Expression.SAD, "I don't have enough coins to buy the leaves.", "I'll come back later.")
            .open()
    } else if (plr.inventory.remove(Item(COINS, price))) {
        plr.giveItem(Item(WOAD_LEAF, leaves))
        plr.sendMessage("You give Wyson $price coins.")
        plr.sendMessage(
            if (leaves == 1) "Wyson the gardener gives you a woad leaf."
            else "Wyson the gardener gives you some woad leaves."
        )
    }
}

/**
 * Opens the dialogue where the player haggles with Wyson over the price of woad leaves.
 */
fun woadDialogue(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("Yes please, I need woad leaves.")
        .npc(npc.id, Expression.SHIFTY, "How much are you willing to pay?")
        .options(
            "How about 5 coins?", { lowOffer(it, npc, "How about 5 coins?") },
            "How about 10 coins?", { lowOffer(it, npc, "How about 10 coins?") },
            "How about 15 coins?",
            {
                plr.newDialogue()
                    .player("How about 15 coins?")
                    .npc(npc.id, "Mmmm... okay, sounds fair.")
                    .then { buyLeaves(it, npc, 15, 1) }
                    .open()
            },
            "How about 20 coins?",
            {
                plr.newDialogue()
                    .player("How about 20 coins?")
                    .npc(npc.id, Expression.HAPPY, "Okay, that's more than fair.")
                    .then {
                        if (it.inventory.computeAmountForId(COINS) < 20) {
                            buyLeaves(it, npc, 20, 2)
                        } else {
                            it.newDialogue()
                                .npc(npc.id, Expression.HAPPY, "Here, have two, you're a generous person.")
                                .then { buyLeaves(it, npc, 20, 2) }
                                .open()
                        }
                    }
                    .open()
            }
        ).open()
}

/**
 * Opens the dialogue where Wyson tells the player about the Giant Mole.
 */
fun moleDialogue(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("How about ME helping YOU instead?")
        .npc(npc.id, "That's a nice thing to say. I do need a hand, now you",
             "mention it. You see, there's some stupid mole digging",
             "up my lovely garden.")
        .player("A mole? Surely you've dealt with moles in the past?")
        .npc(npc.id, "Ah, well this is no ordinary mole! He's a big'un for",
             "sure. Ya see... I'm always relied upon to make the most",
             "of this 'ere garden - the faster and bigger I can grow",
             "plants the better!")
        .npc(npc.id, "In my quest for perfection I looked into",
             "'Malignius-Mortifer's-Super-Ultra-Flora-Growth-Potion'.",
             "It worked well on my plants, no doubt about it! But it",
             "had the same effect on a nearby mole. Ya can imagine")
        .npc(npc.id, "the havoc he causes to my patches of sunflowers! Why,",
             "if any of the other gardeners knew about this mole, I'd",
             "be looking for a new job in no time!")
        .player("I see. What do you need me to do?")
        .npc(npc.id, "If ya are willing maybe yer wouldn't mind killing it for",
             "me? Take a spade and use it to shake up them mole",
             "hills. Be careful though, he really is big!")
        .player("Is there anything in this for me?")
        .npc(npc.id, "Well, if yer gets any mole skin or mole claws off 'un,",
             "I'd trade 'em for bird nests if ye brings 'em here to",
             "me.")
        .player("Right, I'll bear it in mind.")
        .open()
}

/**
 * Opens the dialogue where the player turns Wyson down.
 */
fun notInterested(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("Sorry, but I'm not interested.")
        .npc(npc.id, "Fair enough.")
        .open()
}

/**
 * Trades each mole claw and mole skin [plr] carries for a bird nest.
 */
fun tradeMoleParts(plr: Player) {
    plr.newDialogue()
        .player("Okay, I will trade the mole parts.")
        .then {
            for (index in 0 until it.inventory.capacity()) {
                if (it.inventory[index]?.id in MOLE_PARTS) {
                    it.inventory.set(index, Item(RandomUtils.weightedRoll(MOLE_PART_NESTS)))
                }
            }
        }
        .open()
}

/*
 * Handles Wyson the gardener's first npc option dialogue.
 *
 * The player can buy woad leaves, hear about the Giant Mole that's digging up his garden, or trade him the mole's
 * claws and skins for bird nests.
 */
npc1(WYSON_ID) {
    val npc = targetNpc
    if (plr.inventory.containsAnyIds(MOLE_PARTS)) {
        plr.newDialogue()
            .npc(npc.id, "If I'm not mistaken, you've got some body parts from a",
                 "big mole there! I'll trade it for bird nests if ye likes. Or",
                 "was ye wantin' some woad leaves instead?")
            .options(
                "Okay, I will trade the mole parts.", { tradeMoleParts(it) },
                "Yes please, I need woad leaves.", { woadDialogue(it, npc) },
                "Sorry, but I'm not interested.", { notInterested(it, npc) }
            ).open()
    } else {
        plr.newDialogue()
            .npc(npc.id, "I'm the head gardener around here. If you're looking",
                 "for woad leaves, or if you need help with owt, I'm yer",
                 "man.")
            .options(
                "Yes please, I need woad leaves.", { woadDialogue(it, npc) },
                "How about ME helping YOU instead?", { moleDialogue(it, npc) },
                "Sorry, but I'm not interested.", { notInterested(it, npc) }
            ).open()
    }
}
