package world.player.skill.fishing

import api.predef.*
import io.luna.game.model.item.Item
import world.player.skill.fishing.catchFish.CatchFishAction
import kotlin.math.floor

/**
 * An enum representing a fish that can be caught using a [Tool].
 *
 * @author lare96
 */
enum class Fish(val id: Int,
                val level: Int,
                val exp: Double,
                val chance: Pair<Int, Int>) {

    KARAMBWANJI(id = 3150,
                level = 5,
                exp = 5.0,
                chance = 100 to 250) {
        override fun toItem(action: CatchFishAction): Item {
            val level = action.mob.fishing.level / 5
            val extraCount = floor(level.toDouble())
            return Item(id, (1 + extraCount).toInt())
        }
    },
    SHRIMP(id = 317,
           level = 1,
           exp = 10.0,
           chance = 24 to 128),
    SARDINE(id = 327,
            level = 5,
            exp = 20.0,
            chance = 24 to 128),
    HERRING(id = 345,
            level = 10,
            exp = 30.0,
            chance = 24 to 128),
    ANCHOVY(id = 321,
            level = 15,
            exp = 40.0,
            chance = 24 to 128),
    MACKEREL(id = 353,
             level = 16,
             exp = 20.0,
             chance = 5 to 65),
    CASKET(id = 405,
           level = 16,
           exp = 10.0,
           chance = 1 to 2),
    OYSTER(id = 407,
           level = 16,
           exp = 10.0,
           chance = 3 to 7),
    LEATHER_BOOTS(id = 1061,
                  level = 16,
                  exp = 1.0,
                  chance = 10 to 10),
    LEATHER_GLOVES(id = 1059,
                   level = 16,
                   exp = 1.0,
                   chance = 10 to 10),
    SEAWEED(id = 401,
            level = 16,
            exp = 1.0,
            chance = 10 to 10),
    TROUT(id = 335,
          level = 20,
          exp = 50.0,
          chance = 32 to 192),
    COD(id = 341,
        level = 23,
        exp = 45.0,
        chance = 4 to 55),
    PIKE(id = 349,
         level = 25,
         exp = 60.0,
         chance = 16 to 96),
    SALMON(id = 331,
           level = 30,
           exp = 70.0,
           chance = 16 to 96),
    TUNA(id = 359,
         level = 35,
         exp = 80.0,
         chance = 8 to 64),
    LOBSTER(id = 377,
            level = 40,
            exp = 90.0,
            chance = 6 to 95),
    BASS(id = 363,
         level = 46,
         exp = 100.0,
         chance = 3 to 40),
    SWORDFISH(id = 371,
              level = 50,
              exp = 100.0,
              chance = 4 to 48),
    MONKFISH(id = 7944,
             level = 62,
             exp = 120.0,
             chance = 5 to 56),
    KARAMBWAN(id = 3142,
              level = 65,
              exp = 50.0,
              chance = 5 to 160) {
        override fun toItem(action: CatchFishAction): Item? {
            return if (rand(3) == 0) {
                // 3 in 1 chance to lose bait.
                action.messages += "A Karambwan deftly snatches the Karambwanji from your vessel!"
                null
            } else {
                Item(id, 1)
            }
        }
    },
    SHARK(id = 383,
          level = 76,
          exp = 110.0,
          chance = 3 to 40);

    /**
     * The formatted name.
     */
    val formattedName = itemName(id).replace("Raw ", "").lowercase().trim()

    /**
     * Retrieves the item instance from this fish.
     */
    open fun toItem(action: CatchFishAction): Item? = Item(id)
}