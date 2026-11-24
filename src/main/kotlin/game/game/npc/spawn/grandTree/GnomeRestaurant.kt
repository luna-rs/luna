package game.npc.spawn.grandTree

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Messages
import game.skill.cooking.gnomeCooking.GnomeDish
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Cooking for Aluft Gianne's restaurant in the Grand Tree. A player first cooks five dishes for him as a trial, then
 * takes his orders for coins and Cooking experience.
 */
object GnomeRestaurant {

    const val ALUFT = 850
    const val COINS = 995
    const val COOK_BOOK = 2167

    /**
     * The Cooking experience for each dish handed in on an order.
     */
    const val EXP_PER_DISH = 60.0

    const val WORM_BATTA = 2253
    const val TOAD_BATTA = 2255
    const val CHEESE_TOM_BATTA = 2259
    const val FRUIT_BATTA = 2277
    const val VEGETABLE_BATTA = 2281
    const val WORM_CRUNCHIES = 2205
    const val CHOCCHIP_CRUNCHIES = 2209
    const val SPICY_CRUNCHIES = 2213
    const val TOAD_CRUNCHIES = 2217
    const val CHOCOLATE_BOMB = 2185
    const val TANGLED_TOADS_LEGS = 2187
    const val WORM_HOLE = 2191
    const val VEG_BALL = 2195

    /**
     * Every dish a player can cook for Aluft. Using one on him starts his dialogue.
     */
    val DISHES = listOf(WORM_BATTA, TOAD_BATTA, CHEESE_TOM_BATTA, FRUIT_BATTA, VEGETABLE_BATTA, WORM_CRUNCHIES,
        CHOCCHIP_CRUNCHIES, SPICY_CRUNCHIES, TOAD_CRUNCHIES, CHOCOLATE_BOMB, TANGLED_TOADS_LEGS, WORM_HOLE, VEG_BALL)

    /**
     * A dish Aluft asks for in his trial.
     *
     * @property dish The dish the player must cook.
     * @property premade The same dish sold by his waiter, which he won't accept.
     * @property utensil The tin, tray or mould the dish is cooked in.
     * @property noun What Aluft calls the dish when he spots a premade one.
     */
    enum class TrialDish(val dish: Int, val premade: Int, val utensil: GnomeDish, val noun: String) {
        CHEESE_TOM_BATTA(GnomeRestaurant.CHEESE_TOM_BATTA, 2223, GnomeDish.BATTA, "batta"),
        CHOCOLATE_BOMB(GnomeRestaurant.CHOCOLATE_BOMB, 2229, GnomeDish.BOWL, "gnomebowl"),
        TOAD_BATTA(GnomeRestaurant.TOAD_BATTA, 2221, GnomeDish.BATTA, "batta"),
        WORM_HOLE(GnomeRestaurant.WORM_HOLE, 2233, GnomeDish.BOWL, "gnomebowl"),
        TOAD_CRUNCHIES(GnomeRestaurant.TOAD_CRUNCHIES, 2243, GnomeDish.CRUNCHIES, "crunchies")
    }

    /**
     * One of the orders Aluft gives once the player is hired.
     *
     * @property request What Aluft says when he gives the order.
     * @property reply What the player answers.
     * @property dishes The dishes and how many of each.
     * @property description How the order is worded when it's handed in or Aluft repeats it.
     * @property reminder How Aluft repeats the order, around [description].
     * @property coins What Aluft pays for it.
     * @property greeting What Aluft asks when the player comes back.
     * @property handIn What the player says when handing it in.
     */
    enum class Order(val request: String,
                     val reply: String,
                     val dishes: Map<Int, Int>,
                     val description: String,
                     val reminder: Pair<String, String>,
                     val coins: Int,
                     val greeting: Greeting,
                     val handIn: String) {
        BATTAS("Can you make me two worm batta's, a toad batta, and a vegetable batta please?",
               "Ok then.",
               mapOf(WORM_BATTA to 2, TOAD_BATTA to 1, VEGETABLE_BATTA to 1),
               "two worm batta's, a toad batta, and a vegetable batta",
               "I need " to ", be as quick as you can.", 45, Greeting.READY, "All done, here you go."),
        BOMB_AND_CRUNCHIES("Ok, I need a chocolate bomb, two portions of choc chip crunchies and two portions of toad crunchies.",
                           "No problem.",
                           mapOf(CHOCOLATE_BOMB to 1, CHOCCHIP_CRUNCHIES to 2, TOAD_CRUNCHIES to 2),
                           "a chocolate bomb, two portions of choc chip crunchies and two portions of toad crunchies",
                           "Ok, I need " to ". Don't take too long, it's a full house tonight.", 75, Greeting.READY,
                           "Here you go Aluft."),
        CHOCCHIP_PAIR("I need two portions of choc chip crunchies please.",
                      "No problem.",
                      mapOf(CHOCCHIP_CRUNCHIES to 2),
                      "two portions of choc chip crunchies",
                      "I just need " to ". Should be easy.", 30, Greeting.HOW_DID_YOU_DO, "Here you go Aluft."),
        BOMB_AND_CHOCCHIP("I just need a chocolate bomb and two portions of choc chip crunchies please.",
                          "No problem.",
                          mapOf(CHOCOLATE_BOMB to 1, CHOCCHIP_CRUNCHIES to 2),
                          "a chocolate bomb and two portions of choc chip crunchies",
                          "I need " to " please.", 45, Greeting.HOW_DID_YOU_DO, "Here you go Aluft."),
        VEG_BATTAS_AND_WORM_HOLE("Excellent, I need two vegetable batta's and a worm hole.",
                                 "No problem.",
                                 mapOf(VEGETABLE_BATTA to 2, WORM_HOLE to 1),
                                 "two vegetable batta's and a worm hole",
                                 "Ok, I need " to ", but try not to take too long, it's a full house tonight.", 45,
                                 Greeting.HOW_DID_YOU_DO, "Here you go Aluft."),
        BOWLS("can you make me a a veg ball, a tangled toad's legs, and a worm hole please?",
              "Ok then.",
              mapOf(VEG_BALL to 1, TANGLED_TOADS_LEGS to 1, WORM_HOLE to 1),
              "a veg ball, a tangled toad's legs, and a worm hole",
              "I need " to " please.", 45, Greeting.READY, "All done, here you go."),
        MIXED("I need a cheese and tomato batta, a veg ball, and two portions of worm crunchies please.",
              "Ok, I'll do my best.",
              mapOf(CHEESE_TOM_BATTA to 1, VEG_BALL to 1, WORM_CRUNCHIES to 2),
              "a cheese and tomato batta, a veg ball, and two portions of worm crunchies",
              "I need " to " please.", 60, Greeting.HOW_DID_YOU_DO, "Here you go Aluft."),
        FULL_MENU("Can you make a two portions of spicy crunchies, a fruit batta, a chocolate bomb, and a veg ball please chef.",
                  "I'll try.",
                  mapOf(SPICY_CRUNCHIES to 2, FRUIT_BATTA to 1, CHOCOLATE_BOMB to 1, VEG_BALL to 1),
                  "two portions of spicy crunchies, a fruit batta, a chocolate bomb, and a veg ball",
                  "I need " to " please.", 45, Greeting.READY, "Here you go Aluft."),
        TANGLED_AND_WORM_CRUNCHIES("I just need a tangled toad's legs and two portions of worm crunchies please.",
                                   "Ok, I'll do my best.",
                                   mapOf(TANGLED_TOADS_LEGS to 1, WORM_CRUNCHIES to 2),
                                   "a tangled toad's legs and two portions of worm crunchies",
                                   "I just need " to " please.", 45, Greeting.READY, "Here you go Aluft.");

        /**
         * Whether [plr] carries every dish of this order.
         */
        fun isReady(plr: Player) = dishes.all { (id, amount) -> plr.inventory.computeAmountForId(id) >= amount }

        /**
         * How many dishes this order holds.
         */
        val size = dishes.values.sum()
    }

    /**
     * What Aluft asks when a player comes back with an order outstanding.
     */
    enum class Greeting {
        READY,
        HOW_DID_YOU_DO;

        fun text(plr: Player) = when (this) {
            READY -> "Hello again, are the dishes ready?"
            HOW_DID_YOU_DO -> "Hello again ${plr.username} how did you do?"
        }
    }

    /**
     * How far through the restaurant the player is: 0 before they meet Aluft, [TRIAL_START] to [HIRED] through the
     * trial, then [HIRED] plus the order's number while one is outstanding.
     */
    var Player.gnomeRestaurantProgress by Attr.int().persist("gnome_restaurant_progress")

    /**
     * The player has the cook book and is about to be given the first trial dish.
     */
    const val TRIAL_START = 1

    /**
     * The player has finished the trial and has no order outstanding.
     */
    const val HIRED = 7

    /**
     * The trial dish the player owes Aluft, or `null` outside the trial.
     */
    fun trialDish(plr: Player) = TrialDish.values().getOrNull(plr.gnomeRestaurantProgress - TRIAL_START - 1)

    /**
     * The order the player owes Aluft, or `null` if they have none.
     */
    fun order(plr: Player) = Order.values().getOrNull(plr.gnomeRestaurantProgress - HIRED - 1)

    /**
     * Gives [plr] a random order.
     */
    fun assignOrder(plr: Player): Order {
        val order = Order.values().random()
        plr.gnomeRestaurantProgress = HIRED + order.ordinal + 1
        return order
    }

    /**
     * Whether [plr] has lost [utensil]: they carry it neither empty nor with a dish in it.
     */
    fun lost(plr: Player, utensil: GnomeDish) =
        listOf(utensil.tin, utensil.raw, utensil.halfBaked).none { plr.inventory.contains(it) }

    /**
     * The utensils [plr] has lost.
     */
    fun lostUtensils(plr: Player) = GnomeDish.values().filter { lost(plr, it) }

    /**
     * What a player calls their lost [utensils] when asking for new ones, and what they call them aloud.
     */
    fun utensilNames(utensils: List<GnomeDish>) = when (utensils.singleOrNull()) {
        GnomeDish.BATTA -> "batta tin" to "batta tin"
        GnomeDish.CRUNCHIES -> "crunchy tray" to "crunchie tray"
        GnomeDish.BOWL -> "gnomebowl mould" to "gnomebowl mould"
        null -> "cooking utensils" to "cooking utensils"
    }

    /**
     * Takes [take] from [plr] and gives them [give]. Takes nothing, and returns `false`, if [plr] lacks an item in
     * [take] or has no room for [give].
     */
    fun exchange(plr: Player, take: List<Item>, give: List<Item>): Boolean {
        val inventory = plr.inventory
        if (!take.all { inventory.computeAmountForId(it.id) >= it.amount }) {
            return false
        }
        inventory.removeAll(take)
        if (!inventory.hasSpaceForAll(give)) {
            inventory.addAll(take)
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        inventory.addAll(give)
        return true
    }
}
