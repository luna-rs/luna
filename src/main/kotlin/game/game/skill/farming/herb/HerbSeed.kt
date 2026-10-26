package game.skill.farming.herb

import game.skill.farming.patch.*
import io.luna.game.model.item.*

/**
 * Data for herb seeds. The seeds are declared in the order the herb patch varp lays them out, which
 * [game.skill.farming.patch.HerbPatch] relies on to draw diseased herbs.
 *
 * The [chanceToSaveLow] and [chanceToSaveHigh] values are unchecked placeholders, they are not from the wiki.
 *
 * @author hydrozoa
 */
enum class HerbSeed(override val seedId: Int,
                    override val level: Int,
                    val configIndexOffset: Int,
                    override val produce: Item,
                    override val plantXp: Double,
                    override val harvestXp: Double,
                    override val chanceToSaveLow: Int,
                    override val chanceToSaveHigh: Int) : Crop {

    GUAM(5291, 9, 3, Item(199), 11.5, 13.5, 25, 80),
    MARRENTILL(5292, 14, 10, Item(201), 13.5, 15.0, 28, 80),
    TARROMIN(5293, 19, 17, Item(203), 16.0, 18.0, 31, 80),
    HARRALANDER(5294, 26, 24, Item(205), 21.5, 24.0, 36, 80),
    RANARR(5295, 32, 31, Item(207), 27.0, 30.5, 39, 80),
    TOADFLAX(5296, 38, 38, Item(3049), 34.0, 38.5, 43, 80),
    IRIT(5297, 44, 45, Item(209), 43.0, 48.5, 46, 80),
    AVANTOE(5298, 50, 52, Item(211), 54.5, 61.5, 50, 80),
    KWUARM(5299, 56, 67, Item(213), 69.0, 77.5, 54, 80),
    SNAPDRAGON(5300, 62, 74, Item(3051), 87.5, 98.5, 57, 80),
    CADENTINE(5301, 67, 81, Item(215), 106.5, 120.0, 60, 80),
    LANTADYME(5302, 73, 88, Item(2485), 134.5, 151.5, 64, 80),
    DWARF_WEED(5303, 79, 95, Item(217), 170.5, 192.0, 67, 80),
    TORSTOL(5304, 85, 102, Item(219), 199.5, 224.5, 71, 80);

    /**
     * Every herb is fully grown at stage 5: the seed, three growing stages, then fully grown.
     */
    override val stages = 5

    /**
     * The chance out of 128 of a herb becoming diseased in a growth cycle (wiki).
     */
    override val baseDiseaseChance = 27

    companion object {
        private val seeds = values().associateBy(HerbSeed::seedId)
        fun lookup(seedId: Int): HerbSeed? = seeds[seedId]
    }
}
