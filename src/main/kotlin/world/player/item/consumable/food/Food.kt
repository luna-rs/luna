package world.player.item.consume.food

import api.attr.Attr
import api.predef.*
import io.luna.game.model.mob.Player
import kotlin.collections.set

/**
 * An enum representing food that can be eaten.
 */
enum class Food(val heal: Int,
                val delay: Int,
                val id: Int,
                val id2: Int? = null,
                val id3: Int? = null,
                val id4: Int? = null) {

    MEAT(heal = 2,
         delay = 3,
         id = 2142),
    CHICKEN(heal = 2,
            delay = 3,
            id = 2140),
    HERRING(heal = 2,
            delay = 3,
            id = 347),
    ANCHOVIES(heal = 2,
              delay = 3,
              id = 319),
    REDBERRY_PIE(heal = 2,
                 delay = 1,
                 id = 2325, id2 = 2333),
    SHRIMP(heal = 3,
           delay = 3,
           id = 315),
    CAKE(heal = 4,
         delay = 3,
         id = 1891, id2 = 1893, id3 = 1895),
    COD(heal = 4,
        delay = 3,
        id = 339),
    PIKE(heal = 4,
         delay = 3,
         id = 351),
    CHOCOLATE_CAKE(heal = 5,
                   delay = 3,
                   id = 1897, id2 = 1899, id3 = 1901),
    MACKEREL(heal = 6,
             delay = 3,
             id = 355),
    MEAT_PIE(heal = 6,
             delay = 1,
             id = 2327, id2 = 2331),
    PLAIN_PIZZA(heal = 7,
                delay = 3,
                id = 2289, id2 = 2291),
    APPLE_PIE(heal = 7,
              delay = 1,
              id = 2323, id2 = 2335),
    TROUT(heal = 7,
          delay = 3,
          id = 333),
    MEAT_PIZZA(heal = 8,
               delay = 3,
               id = 2293, id2 = 2295),
    ANCHOVY_PIZZA(heal = 9,
                  delay = 3,
                  id = 2297, id2 = 2299),
    SALMON(heal = 9,
           delay = 3,
           id = 329),
    BASS(heal = 9,
         delay = 3,
         id = 365),
    TUNA(heal = 10,
         delay = 3,
         id = 361),
    PINEAPPLE_PIZZA(heal = 11,
                    delay = 3,
                    id = 2301, id2 = 2303),
    LOBSTER(heal = 12,
            delay = 3,
            id = 379),
    SWORDFISH(heal = 14,
              delay = 3,
              id = 373),
    MONKFISH(heal = 16,
             delay = 3,
             id = 7946),
    KARAMBWAN(heal = 18,
              delay = 1,
              id = 3144),
    SHARK(heal = 20,
          delay = 3,
          id = 385),
    MANTA_RAY(heal = 22,
              delay = 3,
              id = 391),
    SEA_TURTLE(heal = 22,
               delay = 3,
               id = 397),
    TUNA_POTATO(heal = 22,
                delay = 3,
                id = 7060),
    PURPLE_SWEETS(heal = 3,
                  delay = 3,
                  id = 4561) {
        override fun effect(plr: Player) = plr.increaseRunEnergy(10.0)
        override fun consumeMessage(name: String) = "You eat the sweets."
        override fun healMessage(name: String) = "The sugary goodness heals some energy."
    };

    companion object {

        /**
         * Mappings of [Food.ids] to [Food].
         */
        val ID_TO_FOOD = HashMap<Int, Food>().apply {
            for (food in Food.values()) {
                for (id in food.ids) {
                    this[id] = food
                }
            }
        }

        /**
         * Throttles how often the player can eat.
         */
        val Player.lastEat by Attr.timeSource()
    }

    /**
     * The identifier set.
     */
    val ids = newIdSet()

    /**
     * The mappings of formatted names.
     */
    val formattedName = itemName(id).toLowerCase()

    /**
     * Invoked when the food is eaten.
     */
    open fun effect(plr: Player) {}

    /**
     * The message sent when the food is eaten.
     */
    open fun consumeMessage(name: String) = "You eat the $name."

    /**
     * The message sent when the food heals health.
     */
    open fun healMessage(name: String) = "It heals some health."

    /**
     * Computes and returns the next food portion identifier.
     */
    fun getNextId(current: Int) =
        when (current) {
            id -> id2
            id2 -> id3
            id3 -> id4
            else -> null
        }

    /**
     * Computes and returns a new set of this food's identifiers.
     */
    private fun newIdSet(): Set<Int> {
        val ids = HashSet<Int>(4)
        ids += id
        if (id2 != null) {
            ids += id2
        }
        if (id3 != null) {
            ids += id3
        }
        if (id4 != null) {
            ids += id4
        }
        return ids
    }
}
