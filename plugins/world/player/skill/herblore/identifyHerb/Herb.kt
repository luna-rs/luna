package world.player.skill.herblore.identifyHerb

import io.luna.game.model.item.Item

/**
 * An enum representing an unidentified herb.
 */
enum class Herb(val id: Int,
                val identified: Int,
                val level: Int,
                val exp: Double) {

    GUAM_LEAF(id = 199,
              identified = 249,
              level = 3,
              exp = 2.5),
    MARRENTILL(id = 201,
               identified = 251,
               level = 5,
               exp = 3.8),
    TARROMIN(id = 203,
             identified = 253,
             level = 11,
             exp = 5.0),
    HARRALANDER(id = 205,
                identified = 255,
                level = 20,
                exp = 6.3),
    RANARR_WEED(id = 207,
                identified = 257,
                level = 25,
                exp = 7.5),
    TOADFLAX(id = 3049,
             identified = 2998,
             level = 30,
             exp = 8.0),
    IRIT_LEAF(id = 209,
              identified = 259,
              level = 40,
              exp = 8.8),
    AVANTOE(id = 211,
            identified = 261,
            level = 48,
            exp = 10.0),
    KWUARM(id = 213,
           identified = 263,
           level = 54,
           exp = 11.3),
    SNAPDRAGON(id = 3051,
               identified = 3000,
               level = 59,
               exp = 11.8),
    CADANTINE(id = 215,
              identified = 265,
              level = 65,
              exp = 12.5),
    LANTADYME(id = 2485,
              identified = 2481,
              level = 67,
              exp = 13.1),
    DWARF_WEED(id = 217,
               identified = 267,
               level = 70,
               exp = 13.8),
    TORSTOL(id = 219,
            identified = 269,
            level = 75,
            exp = 15.0);

    companion object {

        /**
         * Mappings of [Herb.id] to [Herb] instances.
         */
        val UNID_TO_HERB = values().associateBy { it.id }
    }

    /**
     * The unidentified item.
     */
    val idItem = Item(id)

    /**
     * The identified item.
     */
    val identifiedItem = Item(identified)
}