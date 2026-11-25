package game.content.tirannwn

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Islwyn, who sells new crystal bows and shields, and Ilfeen, who chants a crystal seed back into one for less each
 * time she does it for the same player.
 *
 * @author TheLining
 */
object CrystalElves {

    /**
     * Islwyn's NPC id.
     */
    const val ISLWYN = 1680

    /**
     * Ilfeen's NPC id.
     */
    const val ILFEEN = 1777

    /**
     * The seed a crystal bow or shield reverts to.
     */
    const val SEED = 4207

    /**
     * The coins item id.
     */
    private const val COINS = 995

    /**
     * How many of Ilfeen's chants it takes before her price stops falling.
     */
    private const val CHANTS_TO_LOWEST_PRICE = 4

    /**
     * The two crystal items the elves make.
     *
     * @param id The new item's id.
     * @param price Islwyn's price, and Ilfeen's for her first chant.
     * @param label The name used in dialogue.
     */
    enum class CrystalItem(val id: Int, val price: Int, val label: String) {
        BOW(4212, 900_000, "bow"),
        SHIELD(4224, 750_000, "shield");

        /**
         * The price, written as in dialogue.
         */
        val coins: String get() = numF(price)
    }

    /**
     * How many crystal seeds Ilfeen has chanted for this player.
     */
    var Player.crystalSeedChants by Attr.int().persist("crystal_seed_chants")

    /**
     * Ilfeen's price for chanting [item] for this player: a fifth less each time, down to a fifth of [CrystalItem.price].
     */
    fun Player.chantPrice(item: CrystalItem): Int =
        item.price / 5 * (5 - crystalSeedChants.coerceAtMost(CHANTS_TO_LOWEST_PRICE))

    /**
     * Talks to Islwyn.
     */
    fun talkToIslwyn(plr: Player) {
        if (!QuestGates.passes(plr, Quest.ROVING_ELVES)) {
            plr.newDialogue().player("Hello there.").npc(ISLWYN, "Leave me be, I have no time for humans.").open()
            return
        }
        plr.newDialogue()
            .player("Hello again.")
            .npc(ISLWYN, "Ahh... good to see you back. Are you after more crystal equipment?")
            .options("One of your bows, please.", { offer(it, CrystalItem.BOW, "One of your bows, please.") },
                     "I'd like to buy a shield.", { offer(it, CrystalItem.SHIELD, "I'd like to buy a shield.") },
                     "What should I do with crystal seeds?", {
                         it.newDialogue()
                             .player("What should I do with crystal seeds?")
                             .npc(ISLWYN, "Ilfeen can chant them into new items for you.")
                             .player("I'll keep an eye out for her, thanks.")
                             .open()
                     },
                     "No, I'm just passing by.", { it.newDialogue().player("No, I'm just passing by.").open() })
            .open()
    }

    /**
     * Islwyn names his price for a new [item].
     */
    private fun offer(plr: Player, item: CrystalItem, ask: String) {
        plr.newDialogue()
            .player(ask)
            .npc(ISLWYN, "They're ${item.coins} coins each for new ones.")
            .options("Pay ${item.coins} coins?", { buy(it, item) },
                     "No, thank you.", { it.resetDialogues() })
            .open()
    }

    /**
     * Sells [plr] a new [item] for Islwyn's price.
     */
    private fun buy(plr: Player, item: CrystalItem) {
        if (plr.inventory.computeAmountForId(COINS) < item.price) {
            plr.newDialogue()
                .player("Sorry but I don't have that much.")
                .npc(ISLWYN, "Well sorry, but I can't let it go for anything less.",
                     if (item == CrystalItem.BOW) "Oh well... see you later." else "Oh well... never mind then.")
                .open()
            return
        }
        val coins = Item(COINS, item.price)
        if (plr.inventory.computeAmountForId(COINS) > item.price && !plr.inventory.hasSpaceFor(Item(item.id))) {
            plr.resetDialogues()
            plr.sendMessage("You don't have enough inventory space.")
            return
        }
        plr.inventory.remove(coins)
        plr.inventory.add(Item(item.id))
        val thanks = if (item == CrystalItem.BOW) "Good hunting." else "Look after yourself."
        val bye = if (item == CrystalItem.BOW) "Thanks... goodbye." else "Thank you... good bye."
        plr.newDialogue()
            .player("Here's ${item.coins} coins.")
            .give(Item(item.id), "You hand over ${item.coins} coins and get a crystal ${item.label} in return.", false)
            .npc(ISLWYN, thanks)
            .player(bye)
            .open()
    }

    /**
     * Talks to Ilfeen, who offers to chant a seed if [plr] carries one.
     */
    fun talkToIlfeen(plr: Player) {
        if (!QuestGates.passes(plr, Quest.ROVING_ELVES)) {
            plr.newDialogue().player("Hello there.").npc(ILFEEN, "Sorry, I don't have time to talk.").open()
            return
        }
        plr.newDialogue()
            .player("Hello again.")
            .npc(ILFEEN, "Hey.")
            .player("Are you still offering to chant seeds?")
            .npc(ILFEEN, "I am, but you'll need to supply the seed.")
            .then { if (SEED in it.inventory) offerChant(it) else it.resetDialogues() }
            .open()
    }

    /**
     * Ilfeen offers to chant the crystal seed [plr] carries.
     */
    fun offerChant(plr: Player) {
        if (!QuestGates.passes(plr, Quest.ROVING_ELVES)) {
            talkToIlfeen(plr)
            return
        }
        plr.newDialogue()
            .player("It just happens I have a seed here, what can you do with it?")
            .npc(ILFEEN, "I can only make shields and bows. I've been trying halberds but so far only Islwyn has " +
                "succeeded.")
            .options("How much for a shield?", { quoteChant(it, CrystalItem.SHIELD, "How much for a shield?") },
                     "How much is the bow?", { quoteChant(it, CrystalItem.BOW, "How much is the bow?") })
            .open()
    }

    /**
     * Ilfeen names her price for chanting the seed into [item].
     */
    private fun quoteChant(plr: Player, item: CrystalItem, ask: String) {
        val price = plr.chantPrice(item)
        val coins = numF(price)
        val quote = if (plr.crystalSeedChants == 0) {
            "As this is the first one I've made for you it will be $coins coins. The cost should get cheaper as I get " +
                "to know your resonance."
        } else {
            "That will be $coins coins."
        }
        plr.newDialogue()
            .player(ask)
            .npc(ILFEEN, quote)
            .options("I'll take it.", { chant(it, item, price) },
                     "I can't afford that.", { it.newDialogue().player("I can't afford that.").open() })
            .open()
    }

    /**
     * Takes the seed and [price] coins from [plr] and gives them a new [item].
     */
    private fun chant(plr: Player, item: CrystalItem, price: Int) {
        val slot = plr.inventory.computeIndexForId(SEED)
        if (slot == -1) {
            plr.resetDialogues()
            return
        }
        if (plr.inventory.computeAmountForId(COINS) < price) {
            plr.newDialogue().player("Sorry but I don't have that much.").open()
            return
        }
        plr.inventory.remove(Item(COINS, price))
        plr.inventory[slot] = Item(item.id)
        plr.crystalSeedChants++
        plr.newDialogue()
            .player("I'll take it.")
            .give(Item(item.id), "Ilfeen hands you a crystal ${item.label}.", false)
            .player("Thanks.")
            .open()
    }
}
