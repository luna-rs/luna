package game.skill.farming.seed

import io.luna.game.model.item.Item

/**
 * Data for the crops grown in the special patches: a cactus, bittercap mushrooms and belladonna, one for each patch.
 *
 * The [baseDiseaseChance] is unchecked. The belladonna [chanceToSaveLow] and [chanceToSaveHigh] values are also unchecked.
 *
 * @author hydrozoa
 */
enum class SpecialSeed(override val seedId: Int,
                       override val level: Int,
                       override val stages: Int,
                       override val produce: Item,
                       override val plantXp: Double,
                       override val harvestXp: Double,
                       override val checkHealthXp: Double = 0.0,
                       override val chanceToSaveLow: Int = 0,
                       override val chanceToSaveHigh: Int = 0) : Crop {

    /**
     * Seven growth cycles of 80 minutes to a cactus waiting to have its health checked, then up to three spines.
     */
    CACTUS(seedId = 5280,
           level = 55,
           stages = 8,
           produce = Item(6016),
           plantXp = 66.5,
           harvestXp = 25.0,
           checkHealthXp = 374.0),

    /**
     * Six growth cycles of 40 minutes to six bittercap mushrooms.
     */
    BITTERCAP(seedId = 5282,
              level = 53,
              stages = 7,
              produce = Item(6004),
              plantXp = 61.5,
              harvestXp = 57.7),

    /**
     * Four growth cycles of 80 minutes to belladonna, harvested like herbs.
     */
    BELLADONNA(seedId = 5281,
               level = 63,
               stages = 5,
               produce = Item(2398),
               plantXp = 91.0,
               harvestXp = 512.0,
               chanceToSaveLow = 50,
               chanceToSaveHigh = 90);

    /**
     * The chance out of 128 of the crop becoming diseased in a growth cycle, before compost.
     */
    override val baseDiseaseChance = 20

    companion object {
        private val seeds = values().associateBy(SpecialSeed::seedId)
        fun lookup(seedId: Int): SpecialSeed? = seeds[seedId]
    }
}
