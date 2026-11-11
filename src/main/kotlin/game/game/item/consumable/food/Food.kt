package game.player.item.consume.food

import api.attr.Attr
import api.predef.*
import game.player.Sound
import io.luna.game.model.mob.Player

/**
 * An enum representing food that can be eaten.
 *
 * @author lare96
 */
enum class Food(val heal: Int,
                val delay: Int,
                val id: Int,
                val id2: Int? = null,
                val id3: Int? = null,
                val id4: Int? = null,
                val leftover: Int? = null,
                val sound: Sound = Sound.EAT) {

    BEER(heal = 1,
         delay = 3,
         id = 1917,
         leftover = 1919,
         sound = Sound.LIQUID) {
        override fun effect(plr: Player) {
            plr.strength.boost(1 + plr.strength.staticLevel * 2 / 100)
            plr.attack.adjustLevel(-(1 + plr.attack.level * 6 / 100))
            plr.sendMessage("...and slightly dizzy too.")
        }

        override fun consumeMessage(name: String) = "You drink the $name.  You feel slightly reinvigorated..."
        override fun healMessage(name: String): String? = null
    },
    KEBAB(heal = 0,
          delay = 3,
          id = 1971) {
        override fun effect(plr: Player) = plr.onKebab()
        override fun healMessage(name: String): String? = null
    },
    WINE(heal = 11,
         delay = 3,
         id = 1993,
         leftover = 1935,
         sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(2)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String) = "It makes you feel a bit dizzy, but it heals some health."
    },
    HALF_WINE(heal = 7,
              delay = 3,
              id = 1989,
              leftover = 1935,
              sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(2)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String) = "It makes you feel a bit dizzy, but it heals some health."
    },
    BAD_WINE(heal = 0,
             delay = 3,
             id = 1991,
             leftover = 1935,
             sound = Sound.LIQUID) {
        override fun effect(plr: Player) = plr.onWine(3, false)
        override fun consumeMessage(name: String) = "You drink the wine."
        override fun healMessage(name: String): String? = null
    },

    BANANA(heal = 2,
           delay = 3,
           id = 1963),
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
    PEACH(heal = 8,
          delay = 3,
          id = 6883),
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
    SUMMER_PIE(heal = 11,
               delay = 1,
               id = 7218, id2 = 7220,
               leftover = 2313) {
        override fun effect(plr: Player) {
            plr.increaseRunEnergy(10.0)
            plr.agility.boost(5)
        }

        override fun consumeMessage(name: String, portion: Int) =
            if (portion == id) "You eat half the $name." else "You eat the remaining $name."

        override fun healMessage(name: String): String? = null
    },
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
         * The highest-healing food currently defined.
         *
         * This is used as the fallback upper bound when a bot needs to know the best available food type without
         * caring about cost, availability, or combat level.
         */
        val BEST_FOOD = Food.values()
            .maxByOrNull { it.heal }
            ?.let { it to it.heal }
            ?: (MEAT to MEAT.heal)

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

        /**
         * Applies wine's Attack drain and the message used when it cannot restore health.
         */
        private fun Player.onWine(drain: Int, heals: Boolean = true) {
            attack.adjustLevel(-drain)
            if (!heals || hitpoints.level >= hitpoints.staticLevel) {
                sendMessage("It makes you feel a bit dizzy.")
            }
        }

        /**
         * Applies a kebab's weighted effects, including the Hitpoints penalties used before July 2007.
         * Healing is handled here because each kebab rolls one outcome instead of restoring a fixed amount.
         */
        private fun Player.onKebab() {
            val hp = hitpoints
            val heal = when (rand(31)) {
                0 -> {
                    sendMessage("Wow, that was an amazing kebab! You feel really invigorated.")
                    attack.boost(2)
                    strength.boost(2)
                    defence.boost(2)
                    7 + hp.staticLevel * 24 / 100
                }
                in 1..8 -> {
                    sendMessage("That was a good kebab. You feel a lot better.")
                    6 + hp.staticLevel * 14 / 100
                }
                in 9..28 -> {
                    if (hp.level < hp.staticLevel) {
                        sendMessage("It heals some health.")
                    }
                    3 + hp.staticLevel * 7 / 100
                }
                29 -> {
                    sendMessage("That tasted very dodgy. You feel very ill.")
                    attack.adjustLevel(-3)
                    strength.adjustLevel(-3)
                    defence.adjustLevel(-3)
                    if (hp.level > 2) {
                        damage(minOf(4, hp.level - 2))
                    }
                    0
                }
                30 -> {
                    if (hp.level > 4) {
                        sendMessage("That tasted a bit dodgy. You feel a bit ill.")
                        damage(3)
                    } else {
                        sendMessage("The kebab didn't seem to do a lot.")
                    }
                    0
                }
                else -> {
                    sendMessage("The kebab didn't seem to do a lot.")
                    0
                }
            }
            hp.adjustLevel(heal)
        }
    }

    /**
     * The identifier set.
     */
    val ids = newIdSet()

    /**
     * The mappings of formatted names.
     */
    val formattedName = itemName(id).lowercase()

    /**
     * Invoked when the food is eaten.
     */
    open fun effect(plr: Player) {}

    /**
     * The message sent when the food is eaten.
     */
    open fun consumeMessage(name: String) = "You eat the $name."

    /**
     * The message sent when the [portion] identifier of this food is eaten.
     */
    open fun consumeMessage(name: String, portion: Int) = consumeMessage(name)

    /**
     * The message sent when the food heals health, or `null` for none.
     */
    open fun healMessage(name: String): String? = "It heals some health."

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
