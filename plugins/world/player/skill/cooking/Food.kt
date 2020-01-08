package world.player.skill.cooking

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enum representing food that can be cooked.e
 */
enum class Food(val raw: Int,
                val cooked: Int,
                val burnt: Int,
                val reqLevel: Int,
                val masterLevel: Int,
                val exp: Double) {
    BEEF(raw = 2132,
         cooked = 2142,
         burnt = 2146,
         reqLevel = 1,
         masterLevel = 31,
         exp = 30.0),
    RAT_MEAT(raw = 2134,
             cooked = 2142,
             burnt = 2146,
             reqLevel = 1,
             masterLevel = 31,
             exp = 30.0),
    BEAR_MEAT(raw = 2136,
              cooked = 2142,
              burnt = 2146,
              reqLevel = 1,
              masterLevel = 31,
              exp = 30.0),
    CHICKEN(raw = 2138,
            cooked = 2140,
            burnt = 2144,
            reqLevel = 1,
            masterLevel = 31,
            exp = 30.0),
    SHRIMP(raw = 317,
           cooked = 315,
           burnt = 323,
           reqLevel = 1,
           masterLevel = 33,
           exp = 30.0),
    ANCHOVIES(raw = 321,
              cooked = 329,
              burnt = 323,
              reqLevel = 1,
              masterLevel = 34,
              exp = 30.0),
    SARDINE(raw = 327,
            cooked = 325,
            burnt = 369,
            reqLevel = 1,
            masterLevel = 35,
            exp = 40.0),
    HERRING(raw = 345,
            cooked = 347,
            burnt = 357,
            reqLevel = 10,
            masterLevel = 41,
            exp = 50.0),
    MACKEREL(raw = 353,
             cooked = 355,
             burnt = 357,
             reqLevel = 10,
             masterLevel = 45,
             exp = 60.0),
    TROUT(raw = 335,
          cooked = 333,
          burnt = 343,
          reqLevel = 15,
          masterLevel = 50,
          exp = 70.0),
    COD(raw = 341,
        cooked = 339,
        burnt = 343,
        reqLevel = 18,
        masterLevel = 52,
        exp = 75.0),
    PIKE(raw = 349,
         cooked = 351,
         burnt = 343,
         reqLevel = 20,
         masterLevel = 64,
         exp = 80.0),
    SALMON(raw = 331,
           cooked = 329,
           burnt = 343,
           reqLevel = 25,
           masterLevel = 58,
           exp = 90.0),
    TUNA(raw = 359,
         cooked = 361,
         burnt = 367,
         reqLevel = 30,
         masterLevel = 64,
         exp = 100.0),
    LOBSTER(raw = 377,
            cooked = 379,
            burnt = 381,
            reqLevel = 38,
            masterLevel = 74,
            exp = 115.0),
    BASS(raw = 363,
         cooked = 365,
         burnt = 367,
         reqLevel = 43,
         masterLevel = 80,
         exp = 130.0),
    SWORDFISH(raw = 371,
              cooked = 373,
              burnt = 375,
              reqLevel = 45,
              masterLevel = 86,
              exp = 140.0),
    MONKFISH(raw = 7944,
             cooked = 7946,
             burnt = 7948,
             reqLevel = 62,
             masterLevel = 92,
             exp = 150.0),
    SHARK(raw = 383,
          cooked = 385,
          burnt = 387,
          reqLevel = 80,
          masterLevel = 99,
          exp = 210.0),
    REDBERRY_PIE(raw = 2321,
                 cooked = 2325,
                 burnt = 2329,
                 reqLevel = 10,
                 masterLevel = 120,
                 exp = 78.0),
    MEAT_PIE(raw = 2317,
             cooked = 2327,
             burnt = 2329,
             reqLevel = 20,
             masterLevel = 120,
             exp = 104.0),
    APPLE_PIE(raw = 7168,
              cooked = 2323,
              burnt = 2329,
              reqLevel = 30,
              masterLevel = 120,
              exp = 130.0),
    PLAIN_PIZZA(raw = 2287,
                cooked = 2289,
                burnt = 2305,
                reqLevel = 35,
                masterLevel = 120,
                exp = 143.0),
    CAKE(raw = 1889,
         cooked = 1891,
         burnt = 1903,
         reqLevel = 40,
         masterLevel = 120,
         exp = 180.0);

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
    val formattedName = itemDef(cooked).name.toLowerCase()
}