package world.player.skill.herblore.makeUnfPotion

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enum representing an unfinished potion.
 */
enum class UnfPotion(val id: Int, val herb: Int, val level: Int) {
    GUAM(id = 91,
         herb = 249,
         level = 3),
    MARRENTILL(id = 93,
               herb = 251,
               level = 5),
    TARROMIN(id = 95,
             herb = 253,
             level = 8),
    HARRALANDER(id = 97,
                herb = 255,
                level = 15),
    RANARR(id = 99,
           herb = 257,
           level = 30),
    TOADFLAX(id = 3002,
             herb = 2998,
             level = 34),
    IRIT(id = 101,
         herb = 259,
         level = 45),
    AVANTOE(id = 103,
            herb = 261,
            level = 50),
    KWUARM(id = 105,
           herb = 263,
           level = 55),
    SNAPDRAGON(id = 3004,
               herb = 3000,
               level = 63),
    CADANTINE(id = 107,
              herb = 265,
              level = 66),
    LANTADYME(id = 2483,
              herb = 2481,
              level = 69),
    DWARF_WEED(id = 109,
               herb = 267,
               level = 72),
    TORSTOL(id = 111,
            herb = 269,
            level = 78);

    companion object {

        /**
         * Mappings of [UnfPotion.herb] to [UnfPotion] instances.
         */
        val HERB_TO_UNF = values().associateBy { it.herb }

        /**
         * The vial of water identifier.
         */
        const val VIAL_OF_WATER = 227

    }

    /**
     * The herb item.
     */
    val herbItem = Item(herb)

    /**
     * The unf. potion item.
     */
    val idItem = Item(id)

    /**
     * The herb's name.
     */
    val herbName = itemName(herb)
}

