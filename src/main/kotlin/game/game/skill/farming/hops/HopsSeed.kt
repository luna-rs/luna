package game.skill.farming.hops

import game.skill.farming.patch.*
import io.luna.game.model.item.Item

/**
 * Data for hops seeds, planted in hops patches.
 *
 * The [baseDiseaseChance], [chanceToSaveLow] and [chanceToSaveHigh] values are unchecked placeholders, the wiki gives none
 * for hops.
 *
 * @author hydrozoa
 */
enum class HopsSeed(override val seedId: Int,
                    override val level: Int,
                    val configIndexOffset: Int,
                    val seeds: Int,
                    override val stages: Int,
                    override val produce: Item,
                    override val plantXp: Double,
                    override val harvestXp: Double,
                    override val chanceToSaveLow: Int,
                    override val chanceToSaveHigh: Int) : Crop {

    BARLEY(seedId = 5305,
           level = 3,
           configIndexOffset = 48,
           seeds = 4,
           stages = 5,
           produce = Item(6006),
           plantXp = 8.5,
           harvestXp = 9.5,
           chanceToSaveLow = 103,
           chanceToSaveHigh = 180),
    HAMMERSTONE(seedId = 5307,
                level = 4,
                configIndexOffset = 3,
                seeds = 4,
                stages = 5,
                produce = Item(5994),
                plantXp = 9.0,
                harvestXp = 10.0,
                chanceToSaveLow = 104,
                chanceToSaveHigh = 180),
    ASGARNIAN(seedId = 5308,
              level = 8,
              configIndexOffset = 10,
              seeds = 4,
              stages = 6,
              produce = Item(5996),
              plantXp = 10.9,
              harvestXp = 12.0,
              chanceToSaveLow = 107,
              chanceToSaveHigh = 180),
    JUTE(seedId = 5306,
         level = 13,
         configIndexOffset = 55,
         seeds = 3,
         stages = 6,
         produce = Item(5931),
         plantXp = 13.0,
         harvestXp = 14.5,
         chanceToSaveLow = 110,
         chanceToSaveHigh = 180),
    YANILLIAN(seedId = 5309,
              level = 16,
              configIndexOffset = 18,
              seeds = 4,
              stages = 7,
              produce = Item(5998),
              plantXp = 14.5,
              harvestXp = 16.0,
              chanceToSaveLow = 112,
              chanceToSaveHigh = 185),
    KRANDORIAN(seedId = 5310,
               level = 21,
               configIndexOffset = 27,
               seeds = 4,
               stages = 8,
               produce = Item(6000),
               plantXp = 17.5,
               harvestXp = 19.5,
               chanceToSaveLow = 115,
               chanceToSaveHigh = 190),
    WILDBLOOD(seedId = 5311,
              level = 28,
              configIndexOffset = 37,
              seeds = 4,
              stages = 9,
              produce = Item(6002),
              plantXp = 23.0,
              harvestXp = 26.0,
              chanceToSaveLow = 120,
              chanceToSaveHigh = 195);

    /**
     * The chance out of 128 of hops becoming diseased in a growth cycle, before compost and watering.
     */
    override val baseDiseaseChance = 20

    companion object {
        private val seeds = values().associateBy(HopsSeed::seedId)
        fun lookup(seedId: Int): HopsSeed? = seeds[seedId]
    }
}
