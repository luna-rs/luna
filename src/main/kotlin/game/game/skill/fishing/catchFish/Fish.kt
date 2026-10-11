package game.skill.fishing.catchFish

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enum representing a fish (or other catch) that can be caught using a [Tool].
 *
 * @author lare96
 */
enum class Fish(val id: Int,
                val level: Int,
                val exp: Double,
                val chance: Pair<Int, Int>,
                val message: String) {

    KARAMBWANJI(id = 3150,
                level = 5,
                exp = 5.0,
                chance = 100 to 250,
                message = "You catch a Karambwanji."),
    SHRIMP(id = 317,
           level = 1,
           exp = 10.0,
           chance = 48 to 256,
           message = "You catch some shrimps."),
    SARDINE(id = 327,
            level = 5,
            exp = 20.0,
            chance = 32 to 192,
            message = "You catch a sardine."),
    HERRING(id = 345,
            level = 10,
            exp = 30.0,
            chance = 24 to 128,
            message = "You catch a herring."),
    ANCHOVY(id = 321,
            level = 15,
            exp = 40.0,
            chance = 24 to 128,
            message = "You catch some anchovies."),
    MACKEREL(id = 353,
             level = 16,
             exp = 20.0,
             chance = 5 to 65,
             message = "You catch a mackerel."),
    CASKET(id = 405,
           level = 16,
           exp = 10.0,
           chance = 1 to 2,
           message = "You catch a casket."),
    OYSTER(id = 407,
           level = 16,
           exp = 10.0,
           chance = 3 to 7,
           message = "You catch an oyster shell."),
    LEATHER_BOOTS(id = 1061,
                  level = 16,
                  exp = 1.0,
                  chance = 10 to 10,
                  message = "You catch some boots."),
    LEATHER_GLOVES(id = 1059,
                   level = 16,
                   exp = 1.0,
                   chance = 5 to 5,
                   message = "You catch some gloves."),
    SEAWEED(id = 401,
            level = 16,
            exp = 1.0,
            chance = 10 to 10,
            message = "You catch some seaweed."),
    TROUT(id = 335,
          level = 20,
          exp = 50.0,
          chance = 32 to 192,
          message = "You catch a trout."),
    COD(id = 341,
        level = 23,
        exp = 45.0,
        chance = 4 to 55,
        message = "You catch a cod."),
    PIKE(id = 349,
         level = 25,
         exp = 60.0,
         chance = 16 to 96,
         message = "You catch a pike."),
    SALMON(id = 331,
           level = 30,
           exp = 70.0,
           chance = 16 to 96,
           message = "You catch a salmon."),
    TUNA(id = 359,
         level = 35,
         exp = 80.0,
         chance = 8 to 64,
         message = "You catch a tuna."),
    LOBSTER(id = 377,
            level = 40,
            exp = 90.0,
            chance = 6 to 95,
            message = "You catch a lobster."),
    BASS(id = 363,
         level = 46,
         exp = 100.0,
         chance = 3 to 40,
         message = "You catch a bass."),
    SWORDFISH(id = 371,
              level = 50,
              exp = 100.0,
              chance = 4 to 48,
              message = "You catch a swordfish."),
    MONKFISH(id = 7944,
             level = 62,
             exp = 120.0,
             chance = 48 to 90,
             message = "You catch some monkfish."),
    KARAMBWAN(id = 3142,
              level = 65,
              exp = 105.0,
              chance = 5 to 160,
              message = "You catch a Karambwan!") {
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
          chance = 3 to 40,
          message = "You catch a shark!");

    /**
     * The formatted name.
     */
    val formattedName = itemName(id).replace("Raw ", "").lowercase().trim()

    /**
     * Retrieves the item instance from this fish.
     */
    open fun toItem(action: CatchFishAction): Item? = Item(id)
}
