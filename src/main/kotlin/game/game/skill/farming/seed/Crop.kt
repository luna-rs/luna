package game.skill.farming.seed

import io.luna.game.model.item.Item

/**
 * Something that can be planted in a farming patch, implemented by the seed enums such as [AllotmentSeed] and
 * [HerbSeed]. A patch type only reads the values it uses, so anything it does not use can stay at its default.
 *
 * @author hydrozoa
 */
interface Crop {

    /**
     * The unique name of this crop, used to save and load it. Provided by the implementing enum.
     */
    val name: String

    /**
     * The id of the seed planted to grow this crop.
     */
    val seedId: Int

    /**
     * The Farming level required to plant this crop.
     */
    val level: Int

    /**
     * The experience given once, when the patch is fully harvested.
     */
    val plantXp: Double

    /**
     * The experience given for every crop harvested.
     */
    val harvestXp: Double

    /**
     * The stage at which this crop is fully grown, where the seed is stage 1.
     */
    val stages: Int

    /**
     * The item given for every crop harvested.
     */
    val produce: Item

    /**
     * The chance out of 128 that this crop becomes diseased in a growth cycle, before compost and watering.
     */
    val baseDiseaseChance: Int
        get() = 0

    /**
     * The chance to save a harvest life at Farming level 1, out of 256. See [chanceToSaveHigh].
     */
    val chanceToSaveLow: Int
        get() = 0

    /**
     * The chance to save a harvest life at Farming level 99, out of 256. See [chanceToSaveLow].
     */
    val chanceToSaveHigh: Int
        get() = 0
}
