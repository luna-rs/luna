package world.player.skill.cooking.cookFood

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enum representing food that can be cooked.e
 */
enum class Food(val raw: Int,
                val cooked: Int,
                val burnt: Int,
                val lvl: Int,
                val burnStopLvl: Int,
                val exp: Double) {
    BREAD(raw = 2307,
          cooked = 2309,
          burnt = 2311,
          lvl = 1,
          burnStopLvl = 38,
          exp = 40.0),
    SEAWEED(raw = 401,
            cooked = 1781,
            burnt = 1781,
            lvl = 1,
            burnStopLvl = 1,
            exp = 1.0),
    EDIBLE_SEAWEED(raw = 403,
                   cooked = 1781,
                   burnt = 1781,
                   lvl = 1,
                   burnStopLvl = 1,
                   exp = 1.0),
    BEEF(raw = 2132,
         cooked = 2142,
         burnt = 2146,
         lvl = 1,
         burnStopLvl = 31,
         exp = 30.0),
    RAT_MEAT(raw = 2134,
             cooked = 2142,
             burnt = 2146,
             lvl = 1,
             burnStopLvl = 31,
             exp = 30.0),
    BEAR_MEAT(raw = 2136,
              cooked = 2142,
              burnt = 2146,
              lvl = 1,
              burnStopLvl = 31,
              exp = 30.0),
    CHICKEN(raw = 2138,
            cooked = 2140,
            burnt = 2144,
            lvl = 1,
            burnStopLvl = 31,
            exp = 30.0),
    SHRIMP(raw = 317,
           cooked = 315,
           burnt = 323,
           lvl = 1,
           burnStopLvl = 33,
           exp = 30.0),
    ANCHOVIES(raw = 321,
              cooked = 319,
              burnt = 323,
              lvl = 1,
              burnStopLvl = 34,
              exp = 30.0),
    SARDINE(raw = 327,
            cooked = 325,
            burnt = 369,
            lvl = 1,
            burnStopLvl = 35,
            exp = 40.0),
    HERRING(raw = 345,
            cooked = 347,
            burnt = 357,
            lvl = 10,
            burnStopLvl = 41,
            exp = 50.0),
    MACKEREL(raw = 353,
             cooked = 355,
             burnt = 357,
             lvl = 10,
             burnStopLvl = 45,
             exp = 60.0),
    TROUT(raw = 335,
          cooked = 333,
          burnt = 343,
          lvl = 15,
          burnStopLvl = 50,
          exp = 70.0),
    COD(raw = 341,
        cooked = 339,
        burnt = 343,
        lvl = 18,
        burnStopLvl = 52,
        exp = 75.0),
    PIKE(raw = 349,
         cooked = 351,
         burnt = 343,
         lvl = 20,
         burnStopLvl = 64,
         exp = 80.0),
    SALMON(raw = 331,
           cooked = 329,
           burnt = 343,
           lvl = 25,
           burnStopLvl = 58,
           exp = 90.0),
    TUNA(raw = 359,
         cooked = 361,
         burnt = 367,
         lvl = 30,
         burnStopLvl = 64,
         exp = 100.0),
    LOBSTER(raw = 377,
            cooked = 379,
            burnt = 381,
            lvl = 38,
            burnStopLvl = 74,
            exp = 115.0),
    BASS(raw = 363,
         cooked = 365,
         burnt = 367,
         lvl = 43,
         burnStopLvl = 80,
         exp = 130.0),
    SWORDFISH(raw = 371,
              cooked = 373,
              burnt = 375,
              lvl = 45,
              burnStopLvl = 86,
              exp = 140.0),
    PITTA_BREAD(raw = 1863,
                cooked = 1865,
                burnt = 1867,
                lvl = 58,
                burnStopLvl = 38,
                exp = 40.0),
    MONKFISH(raw = 7944,
             cooked = 7946,
             burnt = 7948,
             lvl = 62,
             burnStopLvl = 92,
             exp = 150.0),
    SHARK(raw = 383,
          cooked = 385,
          burnt = 387,
          lvl = 80,
          burnStopLvl = 99,
          exp = 210.0),
    REDBERRY_PIE(raw = 2321,
                 cooked = 2325,
                 burnt = 2329,
                 lvl = 10,
                 burnStopLvl = 120,
                 exp = 78.0),
    MEAT_PIE(raw = 2317,
             cooked = 2327,
             burnt = 2329,
             lvl = 20,
             burnStopLvl = 120,
             exp = 104.0),
    APPLE_PIE(raw = 7168,
              cooked = 2323,
              burnt = 2329,
              lvl = 30,
              burnStopLvl = 120,
              exp = 130.0),
    PLAIN_PIZZA(raw = 2287,
                cooked = 2289,
                burnt = 2305,
                lvl = 35,
                burnStopLvl = 120,
                exp = 143.0),
    CAKE(raw = 1889,
         cooked = 1891,
         burnt = 1903,
         lvl = 40,
         burnStopLvl = 120,
         exp = 180.0),
    NETTLE_TEA(raw = 4237,
               cooked = 4239,
               burnt = 1923,
               lvl = 20,
               burnStopLvl = 54,
               exp = 52.0);

    companion object {

        /**
         * A map of raw food identifiers to food.
         */
        val RAW_TO_FOOD = values().associateBy { it.raw }
    }

    /**
     * The raw item.
     */
    val rawItem = Item(raw)

    /**
     * The cooked item.
     */
    val cookedItem = Item(cooked)

    /**
     * The burnt item.
     */
    val burntItem = Item(burnt)

    /**
     * The formatted name.
     */
    val formattedName = itemName(cooked).toLowerCase()
}