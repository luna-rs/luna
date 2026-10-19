package game.skill.thieving.stealFromStall

import api.drops.DropTable
import api.drops.DropTableHandler
import api.predef.ext.*
import com.google.common.collect.ImmutableMap

/**
 * Represents a stall that can be stolen from. The [globalRefresh] property determines if stalls become empty for all
 * players, or just the player stealing.
 *
 * @author lare96
 */
enum class ThievingStallType(val stalls: Set<Pair<Int, Int>>,
                             val level: Int,
                             val xp: Double,
                             val respawnTicks: Int,
                             val drops: DropTable,
                             val globalRefresh: Boolean = true) {
    VEGETABLE(stalls = setOf(4706 to 4276,
                             4708 to 4276),
              level = 2,
              xp = 10.0,
              respawnTicks = 2,
              drops = DropTableHandler.createSimple {
                  "Potato" x 1 chance (3 of 10)
                  "Cabbage" x 1 chance (1 of 5)
                  "Onion" x 1 chance (1 of 5)
                  "Tomato" x 1 chance (1 of 5)
                  "Garlic" x 1 chance (1 of 10)
              }),
    BAKERY(stalls = setOf(2561 to 634,
                          6163 to 6984),
           level = 5,
           xp = 16.0,
           respawnTicks = 4,
           drops = DropTableHandler.createSimple {
               "Cake" x 1 chance (13 of 20)
               "Bread" x 1 chance (1 of 4)
               "Chocolate slice" x 1 chance (1 of 10)
           }),
    TEA(stalls = setOf(635 to 634,
                       6574 to 6573),
        level = 5,
        xp = 16.0,
        respawnTicks = 8,
        drops = DropTableHandler.createSingleton { "Cup of tea" x 1 }),
    CRAFTING(stalls = setOf(4874 to -1,
                            6166 to 6984),
             level = 5,
             xp = 16.0,
             respawnTicks = 16,
             drops = DropTableHandler.createSimple {
                 "Chisel" x 1 chance (1 of 3)
                 "Ring mould" x 1 chance (1 of 3)
                 "Necklace mould" x 1 chance (1 of 3)
             }),
    SILK(stalls = setOf(2560 to 634),
         level = 20,
         xp = 24.0,
         respawnTicks = 8,
         drops = DropTableHandler.createSingleton { "Silk" x 1 }),
    WINE(stalls = setOf(14011 to 634),
         level = 22,
         xp = 27.0,
         respawnTicks = 16,
         drops = DropTableHandler.createSimple {
             "Jug" x 1 chance (39 of 100)
             "Jug of water" x 1 chance (1 of 5)
             "Grapes" x 1 chance (17 of 100)
             "Jug of wine" x 1 chance (13 of 100)
             "Bottle of wine" x 1 chance (11 of 100)
         }),
    SEED(stalls = setOf(7053 to 634),
         level = 27,
         xp = 10.0,
         respawnTicks = 16,
         drops = DropTableHandler.createSimple {
             "Potato seed" x 1 chance (119 of 1000)
             "Marigold seed" x 1 chance (119 of 1000)
             "Barley seed" x 1 chance (119 of 1000)
             "Hammerstone seed" x 1 chance (119 of 1000)
             "Onion seed" x 1 chance (90 of 1000)
             "Asgarnian seed" x 1 chance (82 of 1000)
             "Cabbage seed" x 1 chance (71 of 1000)
             "Yanillian seed" x 1 chance (47 of 1000)
             "Rosemary seed" x 1 chance (36 of 1000)
             "Nasturtium seed" x 1 chance (35 of 1000)
             "Tomato seed" x 1 chance (35 of 1000)
             "Jute seed" x 1 chance (35 of 1000)
             "Sweetcorn seed" x 1 chance (30 of 1000)
             "Krandorian seed" x 1 chance (24 of 1000)
             "Strawberry seed" x 1 chance (18 of 1000)
             "Wildblood seed" x 1 chance (12 of 1000)
             "Watermelon seed" x 1 chance (9 of 1000)
         }
    ),
    FUR(stalls = setOf(2563 to 634,
                       4278 to 4276),
        level = 35,
        xp = 36.0,
        respawnTicks = 16,
        drops = DropTableHandler.createSingleton { "Grey wolf fur" x 1 }),
    FISH(stalls = setOf(4277 to 4276,
                        4705 to 4276,
                        4707 to 4276),
         level = 42,
         xp = 42.0,
         respawnTicks = 16,
         drops = DropTableHandler.createSimple {
             "Raw salmon" x 1 chance (7 of 10)
             "Raw tuna" x 1 chance (1 of 4)
             "Raw lobster" x 1 chance (1 of 20)
         }),
    SILVER(stalls = setOf(2565 to 634,
                          6164 to 6984),
           level = 50,
           xp = 54.0,
           respawnTicks = 32,
           drops = DropTableHandler.createSingleton { "Silver ore" x 1 }),
    SPICE(stalls = setOf(2564 to 634),
          level = 65,
          xp = 81.0,
          respawnTicks = 64,
          drops = DropTableHandler.createSingleton { "Spice" x 1 }),
    MAGIC(stalls = setOf(4877 to -1),
          level = 65,
          xp = 100.0,
          respawnTicks = 64,
          drops = DropTableHandler.createSimple {
              "Air rune" x 1 chance (1 of 3)
              "Earth rune" x 1 chance (1 of 3)
              "Fire rune" x 1 chance (1 of 3)
          }),
    SCIMITAR(stalls = setOf(4878 to -1),
             level = 65,
             xp = 160.0,
             respawnTicks = 64,
             drops = DropTableHandler.createSimple {
                 "Iron scimitar" x 1 chance (2 of 3)
                 "Steel scimitar" x 1 chance (1 of 3)
             }),
    GEM(stalls = setOf(2562 to 634,
                       6162 to 6984),
        level = 75,
        xp = 16.0,
        respawnTicks = 180,
        drops = DropTableHandler.createSimple {
            "Uncut sapphire" x 1 chance (105 of 128)
            "Uncut emerald" x 1 chance (17 of 128)
            "Uncut ruby" x 1 chance (5 of 128)
            "Uncut diamond" x 1 chance (1 of 128)
        });

    companion object {

        /**
         * Mappings of all full stall ids to [ThievingStallType] instances.
         */
        val FULL_STALLS: ImmutableMap<Int, ThievingStallType> = run {
            val map = HashMap<Int, ThievingStallType>()
            for (stall in values()) {
                for (ids in stall.stalls) {
                    map[ids.first] = stall
                }
            }
            ImmutableMap.copyOf(map)
        }

        /**
         * Mappings of all full stall ids to empty ids.
         */
        val FULL_TO_EMPTY: ImmutableMap<Int, Int> = run {
            val map = HashMap<Int, Int>()
            for (stall in values()) {
                for (ids in stall.stalls) {
                    map[ids.first] = ids.second
                }
            }
            ImmutableMap.copyOf(map)
        }
    }
}