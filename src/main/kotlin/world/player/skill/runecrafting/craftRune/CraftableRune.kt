package world.player.skill.runecrafting.craftRune

/**
 * An enum representing a rune that can be crafted at an [Altar].
 */
enum class CraftableRune(val id: Int,
                         val altar: Int,
                         val multiplier: Int,
                         val level: Int,
                         val exp: Double) {

    AIR(id = 556,
        altar = 2478,
        multiplier = 11,
        level = 1,
        exp = 5.0),
    MIND(id = 558,
         altar = 2479,
         multiplier = 14,
         level = 2,
         exp = 5.5),
    WATER(id = 555,
          altar = 2480,
          multiplier = 19,
          level = 5,
          exp = 6.0),
    EARTH(id = 557,
          altar = 2481,
          multiplier = 26,
          level = 9,
          exp = 6.5),
    FIRE(id = 554,
         altar = 2482,
         multiplier = 35,
         level = 14,
         exp = 7.0),
    BODY(id = 559,
         altar = 2483,
         multiplier = 46,
         level = 20,
         exp = 7.5),
    COSMIC(id = 564,
           altar = 2484,
           multiplier = 59,
           level = 27,
           exp = 8.0),
    CHAOS(id = 562,
          altar = 2487,
          multiplier = 74,
          level = 35,
          exp = 8.5),
    NATURE(id = 561,
           altar = 2486,
           multiplier = 91,
           level = 44,
           exp = 9.0),
    LAW(id = 563,
        altar = 2485,
        multiplier = 99,
        level = 54,
        exp = 9.5),
    DEATH(id = 560,
          altar = 2488,
          multiplier = 99,
          level = 65,
          exp = 10.0),
    BLOOD(id = 565,
          altar = 2490,
          multiplier = 99,
          level = 80,
          exp = 10.5),
    SOUL(id = 566,
         altar = 2489,
         multiplier = 99,
         level = 95,
         exp = 11.0);

    companion object {

        /**
         * Mappings of [CraftableRune.altar] to [CraftableRune] instances.
         */
        val ALTAR_TO_RUNE = values().associateBy { it.altar }
    }
}