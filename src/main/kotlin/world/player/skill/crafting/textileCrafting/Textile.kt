package world.player.skill.crafting.textileCrafting

import io.luna.game.model.item.Item

/**
 * Represents a textile based material.
 */
enum class Textile(val rawItem: Item, val processedItem: Item, val level: Int, val exp: Double, val type: TextileType) {
    // Spinning wheel
    BALL_OF_WOOL(rawItem = Item(1737),
                 processedItem = Item(1759),
                 level = 1,
                 exp = 2.5,
                 type = TextileType.SPINNING_WHEEL),
    BOWSTRING(rawItem = Item(1779),
              processedItem = Item(1777),
              level = 10,
              exp = 15.0,
              type = TextileType.SPINNING_WHEEL),
    MAGIC_STRING(rawItem = Item(6051),
                 processedItem = Item(6038),
                 level = 19,
                 exp = 30.0,
                 type = TextileType.SPINNING_WHEEL),

    // Loom
    CLOTH(rawItem = Item(1759, 4),
          processedItem = Item(3224),
          level = 10,
          exp = 12.0,
          type = TextileType.LOOM),
    EMPTY_SACK(rawItem = Item(5931, 4),
               processedItem = Item(5418),
               level = 21,
               exp = 38.0,
               type = TextileType.LOOM),
    BASKET(rawItem = Item(5933, 6),
           processedItem = Item(5376),
           level = 36,
           exp = 56.0,
           type = TextileType.LOOM);

    companion object {

        /**
         * A map of [rawItem] IDs to [Textile].
         */
        val RAW_IDS_TO_TEXTILES = values().associateBy { it.rawItem.id }

        /**
         * A map of [processedItem] IDs to [Textile].
         */
        val PROCESSED_IDS_TO_TEXTILES = values().associateBy { it.processedItem.id }

        /**
         * An array of spinning wheel based processed items.
         */
        val SPINNING_WHEEL_PROCESSED_IDS = values().filter { it.type == TextileType.SPINNING_WHEEL }.map { it.processedItem.id }.toIntArray()

        /**
         * An array of loom based processed items.
         */
        val LOOM_PROCESSED_IDS = values().filter { it.type == TextileType.LOOM }.map { it.processedItem.id }.toIntArray()
    }
}