package game.skill.farming.seed

import api.predef.*
import io.luna.game.model.item.Item

/**
 * Data for allotment seeds. [flowerProtection] is what, standing in the flower patch of the same farming area, protects
 * the crop from disease.
 *
 * The [baseDiseaseChance], [chanceToSaveLow] and [chanceToSaveHigh] values are unchecked.
 *
 * @author lare96
 * @author hydrozoa
 */
enum class AllotmentSeed(override val seedId: Int,
                         override val level: Int,
                         val configIndexOffset: Int,
                         val flowerProtection: Set<FlowerProtection> = emptySet(),
                         val farmerProtection: Item? = null,
                         override val stages: Int,
                         override val produce: Item,
                         override val plantXp: Double,
                         override val harvestXp: Double,
                         override val baseDiseaseChance: Int,
                         override val chanceToSaveLow: Int,
                         override val chanceToSaveHigh: Int,
                         val states: Pair<Int, Int> = Pair(-1, -1)) : Crop {

    // Allotment seeds.
    POTATO(seedId = 5318,
           level = 1,
           configIndexOffset = 5,
           flowerProtection = setOf(FlowerProtection.MARIGOLD),
           farmerProtection = item("Compost", 2),
           stages = 5,
           produce = Item(1942),
           plantXp = 8.0,
           harvestXp = 9.0,
           baseDiseaseChance = 20,
           chanceToSaveLow = 101,
           chanceToSaveHigh = 180,
           states = Pair(0x06, 0x0c)),
    ONION(seedId = 5319,
          level = 5,
          configIndexOffset = 12,
          flowerProtection = setOf(FlowerProtection.MARIGOLD),
          farmerProtection = item("Potatoes(10)"),
          stages = 5,
          produce = Item(1957),
          plantXp = 9.5,
          harvestXp = 10.5,
          baseDiseaseChance = 20,
          chanceToSaveLow = 105,
          chanceToSaveHigh = 180
    ),
    CABBAGE(seedId = 5324,
            level = 7,
            configIndexOffset = 19,
            flowerProtection = setOf(FlowerProtection.ROSEMARY),
            farmerProtection = item("Onions(10)"),
            stages = 5,
            produce = Item(1965),
            plantXp = 10.0,
            harvestXp = 11.5,
            baseDiseaseChance = 20,
            chanceToSaveLow = 107,
            chanceToSaveHigh = 180
    ),
    TOMATO(seedId = 5322,
           level = 12,
           configIndexOffset = 26,
           flowerProtection = setOf(FlowerProtection.MARIGOLD),
           farmerProtection = item("Cabbages(10)", 2),
           stages = 5,
           produce = Item(1982),
           plantXp = 12.5,
           harvestXp = 14.0,
           baseDiseaseChance = 20,
           chanceToSaveLow = 111,
           chanceToSaveHigh = 180
    ),
    SWEETCORN(seedId = 5320,
              level = 20,
              configIndexOffset = 33,
              flowerProtection = setOf(FlowerProtection.SCARECROW),
              farmerProtection = item("Jute fibre", 10),
              stages = 7,
              produce = Item(5986),
              plantXp = 17.0,
              harvestXp = 19.0,
              baseDiseaseChance = 20,
              chanceToSaveLow = 117,
              chanceToSaveHigh = 190
    ),
    STRAWBERRY(seedId = 5323,
               level = 31,
               configIndexOffset = 42,
               farmerProtection = item("Apples(5)"),
               stages = 7,
               produce = Item(5504),
               plantXp = 26.0,
               harvestXp = 29.0,
               baseDiseaseChance = 20,
               chanceToSaveLow = 125,
               chanceToSaveHigh = 200
    ),
    WATERMELON(seedId = 5321,
               level = 47,
               configIndexOffset = 51,
               flowerProtection = setOf(FlowerProtection.NASTURTIUM),
               farmerProtection = item("Curry leaf", 10),
               stages = 9,
               produce = Item(5982),
               plantXp = 48.5,
               harvestXp = 54.5,
               baseDiseaseChance = 20,
               chanceToSaveLow = 132,
               chanceToSaveHigh = 215
    )
}
