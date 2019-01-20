package world.player.skill.fishing

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enum representing a fish that can be caught using a [Tool].
 */
enum class Fish(val id: Int,
                val level: Int,
                val exp: Double) {

    KARAMBWANJI(id = 3150,
                level = 5,
                exp = 5.0) {
        override fun toItem(action: FishAction): Item? {
            val level = action.mob.fishing.level / 5
            val extraCount = Math.floor(level.toDouble())
            return Item(id, (1 + extraCount).toInt())
        }
    },
    SHRIMP(id = 317,
           level = 1,
           exp = 10.0),
    SARDINE(id = 327,
            level = 5,
            exp = 20.0),
    HERRING(id = 345,
            level = 10,
            exp = 30.0),
    ANCHOVY(id = 321,
            level = 15,
            exp = 40.0),
    MACKEREL(id = 353,
             level = 16,
             exp = 20.0),
    CASKET(id = 405,
           level = 16,
           exp = 10.0),
    OYSTER(id = 407,
           level = 16,
           exp = 10.0),
    LEATHER_BOOTS(id = 1061,
                  level = 16,
                  exp = 1.0),
    LEATHER_GLOVES(id = 1059,
                   level = 16,
                   exp = 1.0),
    SEAWEED(id = 401,
            level = 16,
            exp = 1.0),
    TROUT(id = 335,
          level = 20,
          exp = 50.0),
    COD(id = 341,
        level = 23,
        exp = 45.0),
    PIKE(id = 349,
         level = 25,
         exp = 60.0),
    SALMON(id = 331,
           level = 30,
           exp = 70.0),
    TUNA(id = 359,
         level = 35,
         exp = 80.0),
    LOBSTER(id = 377,
            level = 40,
            exp = 90.0),
    BASS(id = 363,
         level = 46,
         exp = 100.0),
    SWORDFISH(id = 371,
              level = 50,
              exp = 100.0),
    MONKFISH(id = 7944,
             level = 62,
             exp = 120.0),
    KARAMBWAN(id = 3142,
              level = 65,
              exp = 50.0) {
        override fun toItem(action: FishAction): Item? {
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
          exp = 110.0);

    /**
     * The formatted name.
     */
    val formattedName = name.toLowerCase().replace('_', ' ')

    /**
     * The default catch message.
     */
    val catchMessage = "You catch ${addArticle(formattedName)}."

    /**
     * Retrieves the item instance from this fish.
     */
    open fun toItem(action: FishAction): Item? = Item(id)
}