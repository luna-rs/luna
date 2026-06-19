package game.skill.farming.seed

import api.predef.*
import game.skill.farming.*
import io.luna.game.model.item.Item

/**
 * // todo remove patchtype from this, probably
 * @author lare96
 * @author hydrozoa
 */
enum class AllotmentSeed(val seed: Int,
                         val level: Int,
                         val configIndexOffset: Int,
                         val flowerProtection: Set<String> = emptySet(),
                         val farmerProtection: Item? = null,
                         val growthStages: Int,
                         val crop: Item,
                         val plantXp: Double,
                         val harvestXp: Double,
                         val patch: PatchType,
                         val states: Pair<Int, Int> = Pair(-1, -1)) {

    // Allotment seeds.
    POTATO(seed = 5318,
           level = 1,
           configIndexOffset = 5,
           flowerProtection = setOf("Marigold", "White lily"),
           farmerProtection = item("Compost", 2),
           growthStages = 5,
           crop = item("Potato"),
           plantXp = 8.0,
           harvestXp = 9.0,
           patch = PatchType.ALLOTMENT,
           states = Pair(0x06, 0x0c)),
    ONION(seed = 5319,
          level = 5,
          configIndexOffset = 12,
          flowerProtection = setOf("Marigold", "White lily"),
          farmerProtection = item("Potatoes(10)"),
          growthStages = 5,
          crop = item("Onion"),
          plantXp = 9.5,
          harvestXp = 10.5,
          patch = PatchType.ALLOTMENT
    ),
    CABBAGE(seed = 5324,
            level = 7,
            configIndexOffset = 19,
            flowerProtection = setOf("Rosemary", "White lily"),
            farmerProtection = item("Onions(10)"),
            growthStages = 5,
            crop = item("Cabbage"),
            plantXp = 10.0,
            harvestXp = 11.5,
            patch = PatchType.ALLOTMENT
    ),
    TOMATO(seed = 5322,
           level = 12,
           configIndexOffset = 26,
           flowerProtection = setOf("Marigold", "White lily"),
           farmerProtection = item("Cabbages(10)", 2),
           growthStages = 5,
           crop = item("Tomato"),
           plantXp = 12.5,
           harvestXp = 14.0,
           patch = PatchType.ALLOTMENT
    ),
    SWEETCORN(seed = 5320,
              level = 20,
              configIndexOffset = 33,
              flowerProtection = setOf("Scarecrow", "White lily"),
              farmerProtection = item("Jute fibre", 10),
              growthStages = 7,
              crop = item("Sweetcorn"),
              plantXp = 17.0,
              harvestXp = 19.0,
              patch = PatchType.ALLOTMENT
    ),
    STRAWBERRY(seed = 5323,
               level = 31,
               configIndexOffset = 42,
               flowerProtection = setOf("White lily"),
               farmerProtection = item("Apples(5)"),
               growthStages = 7,
               crop = item("Strawberry"),
               plantXp = 26.0,
               harvestXp = 29.0,
               patch = PatchType.ALLOTMENT
    ),
    WATERMELON(seed = 5321,
               level = 47,
               configIndexOffset = 51,
               flowerProtection = setOf("Nasturtiums", "White lily"),
               farmerProtection = item("Curry leaf", 10),
               growthStages = 9,
               crop = item("Watermelon"),
               plantXp = 48.5,
               harvestXp = 54.5,
               patch = PatchType.ALLOTMENT
    )
}