package game.npc.spawn.grandTree

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.mob.Player

/**
 * Bar work for Blurberry in the Grand Tree. A player first mixes five cocktails for him as a trial, then takes his
 * orders for coins.
 */
object GnomeBar {

    const val BLURBERRY = 848
    const val COCKTAIL_GUIDE = 2023
    const val KNIFE = 946

    const val FRUIT_BLAST = 2084
    const val DRUNK_DRAGON = 2092
    const val SGG = 2080
    const val CHOC_SATURDAY = 2074
    const val BLURBERRY_SPECIAL = 2064
    const val PINEAPPLE_PUNCH = 2048
    const val WIZARD_BLIZZARD = 2054

    /**
     * Every cocktail, finished or not, including the ones the bar sells. Using one on Blurberry starts his dialogue.
     */
    val COCKTAILS = (2028..2094 step 2).toList()

    /**
     * A cocktail Blurberry asks for in his trial.
     *
     * @property cocktail The cocktail the player must mix.
     * @property premade The same cocktail sold by the barman, which Blurberry won't accept.
     * @property spoken What Blurberry calls it.
     */
    enum class TrialCocktail(val cocktail: Int, val premade: Int, val spoken: String) {
        FRUIT_BLAST(GnomeBar.FRUIT_BLAST, 2034, "Fruit Blast"),
        DRUNK_DRAGON(GnomeBar.DRUNK_DRAGON, 2032, "Drunk Dragon"),
        SGG(GnomeBar.SGG, 2038, "SGG"),
        CHOC_SATURDAY(GnomeBar.CHOC_SATURDAY, 2030, "Choc Saturday"),
        BLURBERRY_SPECIAL(GnomeBar.BLURBERRY_SPECIAL, 2028, "Blurberry Special")
    }

    /**
     * One of the orders Blurberry gives once the player is hired.
     *
     * @property request What Blurberry says when he gives the order.
     * @property reply What the player answers.
     * @property cocktails The cocktails and how many of each.
     * @property description How the order is worded when it's handed in or Blurberry repeats it.
     * @property praise What Blurberry says when it's handed in.
     * @property coins What Blurberry pays for it.
     */
    enum class Order(val request: String,
                     val reply: String,
                     val cocktails: Map<Int, Int>,
                     val description: String,
                     val praise: String,
                     val coins: Int) {
        PUNCH_SATURDAY_DRAGON("Can you make me one Pineapple Punch, one Choc Saturday and one Drunk Dragon!",
                              "Ok then I'll be back soon.",
                              mapOf(PINEAPPLE_PUNCH to 1, CHOC_SATURDAY to 1, DRUNK_DRAGON to 1),
                              "one Pineapple Punch, one Choc Saturday and one Drunk Dragon",
                              "That's blurberry-tastic!", 100),
        BLIZZARDS_AND_SGG("Ok, I need two Wizard Blizzards and an SGG!",
                          "No problem!",
                          mapOf(WIZARD_BLIZZARD to 2, SGG to 1),
                          "two Wizard Blizzards and an SGG",
                          "That's excellent, here's your share of the profit!", 150),
        FULL_ROUND("Ok, I need one Wizard Blizzard, one Pineapple Punch, one Blurberry Special and two Fruit Blasts!",
                   "I'll do my best.",
                   mapOf(WIZARD_BLIZZARD to 1, PINEAPPLE_PUNCH to 1, BLURBERRY_SPECIAL to 1, GnomeBar.FRUIT_BLAST to 2),
                   "one Wizard Blizzard, one Pineapple Punch, one Blurberry Special and two Fruit Blasts",
                   "Wow fantastic, here's your share of the profit!", 179),
        SGGS_AND_SPECIAL("I just need two SGG's and one Blurberry Special!",
                         "No problem!",
                         mapOf(SGG to 2, BLURBERRY_SPECIAL to 1),
                         "two SGG's and one Blurberry Special",
                         "Great, here's your share of the profit!", 120),
        ONE_FRUIT_BLAST("I just need one Fruit Blast!",
                    "No problem!",
                    mapOf(GnomeBar.FRUIT_BLAST to 1),
                    "one Fruit Blast",
                    "that's frutty-licious!", 10);

        /**
         * Whether [plr] carries every cocktail of this order.
         */
        fun isReady(plr: Player) =
            cocktails.all { (id, amount) -> plr.inventory.computeAmountForId(id) >= amount }
    }

    /**
     * How far through the bar work the player is: 0 before they meet Blurberry, [TRIAL_START] to [HIRED] through the
     * trial, then [HIRED] plus the order's number while one is outstanding.
     */
    var Player.gnomeBarProgress by Attr.int().persist("gnome_bar_progress")

    /**
     * The player has the cocktail guide and is about to be given the first trial cocktail.
     */
    const val TRIAL_START = 1

    /**
     * The player has finished the trial and has no order outstanding.
     */
    const val HIRED = 7

    /**
     * The trial cocktail the player owes Blurberry, or `null` outside the trial.
     */
    fun trialCocktail(plr: Player) = TrialCocktail.values().getOrNull(plr.gnomeBarProgress - TRIAL_START - 1)

    /**
     * The order the player owes Blurberry, or `null` if they have none.
     */
    fun order(plr: Player) = Order.values().getOrNull(plr.gnomeBarProgress - HIRED - 1)

    /**
     * Gives [plr] a random order.
     */
    fun assignOrder(plr: Player): Order {
        val order = Order.values().random()
        plr.gnomeBarProgress = HIRED + order.ordinal + 1
        return order
    }
}
