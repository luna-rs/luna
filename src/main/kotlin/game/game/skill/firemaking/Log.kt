package game.skill.firemaking

import com.google.common.collect.ImmutableList

/**
 * An enum representing logs that can be burned.
 *
 * @author lare96
 */
enum class Log(val id: Int, val level: Int, val exp: Double, val chance: Pair<Int, Int>) {
    NORMAL(id = 1511,
           level = 1,
           exp = 40.0,
           chance = 88 to 255),
    OAK(id = 1521,
        level = 15,
        exp = 60.0,
        chance = 78 to 255),
    WILLOW(id = 1519,
           level = 30,
           exp = 90.0,
           chance = 68 to 255),
    TEAK(id = 6333,
         level = 35,
         exp = 105.0,
         chance = 65 to 255),
    MAPLE(id = 1517,
          level = 45,
          exp = 135.0,
          chance = 53 to 255),
    MAHOGANY(id = 6332,
             level = 50,
             exp = 157.5,
             chance = 50 to 255),
    YEW(id = 1515,
        level = 60,
        exp = 202.5,
        chance = 40 to 120),
    MAGIC(id = 1513,
          level = 75,
          exp = 303.8,
          chance = 25 to 120);

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())
    }
}
