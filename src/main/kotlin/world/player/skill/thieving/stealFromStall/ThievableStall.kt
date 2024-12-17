package world.player.skill.thieving.stealFromStall

import api.item.dropTable.DropTable
import api.item.dropTable.DropTableHandler
import api.item.dropTable.GenericDropTables
import api.predef.ext.*
import com.google.common.collect.ImmutableMap

/**
 * Represents a stall that can be stolen from.
 */
enum class ThievableStall(val stalls: Set<Pair<Int, Int>>,
                          val level: Int,
                          val xp: Double,
                          val respawnTicks: Int,
                          val drops: DropTable,
                          val globalRefresh: Boolean = false) {
    VEGETABLE(stalls = setOf(4706 to 634,
                             4708 to 634),
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
    BAKERY(stalls = setOf(630 to 634,
                          2561 to 634,
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
        respawnTicks = 4,
        drops = DropTableHandler.createSingleton { "Cup of tea" x 1 }),
    CRAFTING(stalls = setOf(4874 to -1,
                            6166 to 6984),
             level = 5,
             xp = 20.0,
             respawnTicks = 8,
             drops = DropTableHandler.createSimple {
                 "Amulet mould" x 1 chance (111 of 500)
                 "Necklace mould" x 1 chance (51 of 250)
                 "Ring mould" x 1 chance (51 of 250)
                 "Chisel" x 1 chance (1 of 9)
                 "Gold bar" x 1 chance (1 of 27)
             }),
    SILK(stalls = setOf(629 to 634,
                        2560 to 634,
                        6568 to 6573),
         level = 20,
         xp = 24.0,
         respawnTicks = 8,
         drops = DropTableHandler.createSingleton { "Silk" x 1 }),
    WINE(stalls = setOf(14011 to 634),
         level = 22,
         xp = 27.0,
         respawnTicks = 8,
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
         respawnTicks = 4,
         drops = GenericDropTables.generalSeedDropTable(false)
    ),
    FUR(stalls = setOf(632 to 634,
                       2563 to 634,
                       4278 to 634,
                       6571 to 6573),
        level = 35,
        xp = 45.0,
        respawnTicks = 12,
        drops = DropTableHandler.createSingleton { "Grey wolf fur" x 1 }),
    FISH(stalls = setOf(4277 to 634,
                        4705 to 634,
                        4707 to 634),
         level = 42,
         xp = 42.0,
         respawnTicks = 12,
         drops = DropTableHandler.createSimple {
             "Raw salmon" x 1 chance (7 of 10)
             "Raw tuna" x 1 chance (1 of 4)
             "Raw lobster" x 1 chance (1 of 20)
         }),
    SILVER(stalls = setOf(2565 to 634,
                          6164 to 634),
           level = 50,
           xp = 205.0,
           respawnTicks = 32,
           drops = DropTableHandler.createSimple {
               "Silver ore" x 1 chance (4 of 5)
               "Silver bar" x 1 chance (15 of 100)
               "Tiara" x 1 chance (1 of 20)
           }),
    SPICE(stalls = setOf(2564 to 634,
                         6572 to 6573),
          level = 65,
          xp = 92.0,
          respawnTicks = 10,
          drops = DropTableHandler.createSingleton { "Spice" x 1 }),
    MAGIC(stalls = setOf(4877 to -1),
          level = 65,
          xp = 90.0,
          respawnTicks = 12,
          drops = DropTableHandler.createSimple {
              "Air rune" x 1 chance (15 of 50)
              "Earth rune" x 1 chance (15 of 50)
              "Fire rune" x 1 chance (15 of 50)
              "Law rune" x 1 chance (1 of 20)
              "Nature rune" x 1 chance (1 of 20)
          }),
    SCIMITAR(stalls = setOf(4878 to -1),
             level = 65,
             xp = 210.0,
             respawnTicks = 32,
             drops = DropTableHandler.createSimple {
                 "Iron scimitar" x 1 chance (23 of 40)
                 "Steel scimitar" x 1 chance (13 of 40)
                 "Mithril scimitar" x 1 chance (3 of 40)
                 "Adamant scimitar" x 1 chance (1 of 40)
             }),
    GEM(stalls = setOf(2562 to 634,
                       6162 to 6984),
        level = 75,
        xp = 408.0,
        respawnTicks = 100,
        drops = DropTableHandler.createSimple {
            "Uncut sapphire" x 1 chance (41 of 50)
            "Uncut emerald" x 1 chance (133 of 1000)
            "Uncut ruby" x 1 chance (391 of 10_000)
            "Uncut diamond" x 1 chance (1 of 128)
        });

    companion object {

        /**
         * Mappings of all full stall ids to [ThievableStall] instances.
         */
        val FULL_STALLS: ImmutableMap<Int, ThievableStall> = run {
            val map = HashMap<Int, ThievableStall>()
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