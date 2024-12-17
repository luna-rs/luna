package world.player.skill.thieving.searchForTraps

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.predef.*

/**
 * Represents a chest that can be stolen from.
 */
enum class ThievableChest(val id: Int,
                          val level: Int,
                          val xp: Double,
                          val respawnTicks: Int,
                          val drops: DropTable,
                          val globalRefresh: Boolean = false) {

    TEN_COIN(id = 2566,
             level = 13,
             xp = 7.8,
             respawnTicks = 6,
             drops = DropTableHandler.createSingleton { "Coins" x 10 }),
    NATURE_RUNE(id = 2567,
                level = 28,
                xp = 25.0,
                respawnTicks = 14,
                drops = DropTableHandler.createSimple {
                    "Coins" x 3 chance ALWAYS
                    "Nature rune" x 1 chance ALWAYS
                }),
    FIFTY_COIN(id = 2568,
               level = 43,
               xp = 125.0,
               respawnTicks = 75,
               drops = DropTableHandler.createSingleton { "Coins" x 50 }),
    STEEL_ARROWTIPS(id = 2573,
                    level = 47,
                    xp = 150.0,
                    respawnTicks = 125,
                    drops = DropTableHandler.createSimple {
                        "Coins" x 20 chance ALWAYS
                        "Steel arrowtips" x 5 chance ALWAYS
                    }),
    BLOOD_RUNES(id = 2569,
                level = 59,
                xp = 250.0,
                respawnTicks = 158,
                drops = DropTableHandler.createSimple {
                    "Coins" x 500 chance ALWAYS
                    "Blood rune" x 2 chance ALWAYS
                }),
    ARDOUGNE_CASTLE(id = 2570,
                    level = 72,
                    xp = 500.0,
                    respawnTicks = 833,
                    drops = DropTableHandler.createSimple {
                        "Coins" x 1000 chance ALWAYS
                        "Raw shark" x 1 chance ALWAYS
                        "Adamantite ore" x 1 chance ALWAYS
                        "Uncut sapphire" x 1 chance ALWAYS
                    });

    companion object {

        /**
         * The chest IDs mapped to their instances.
         */
        val CHESTS = values().associateBy { it.id }
    }
}