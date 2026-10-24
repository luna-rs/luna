package game.skill.farming.patch

import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents any allotment patch.
 * Uses varps [Farming.ALLOTMENT_VARP_1] and [Farming.ALLOTMENT_VARP_2]
 *
 * @author hydrozoa
 */
class AllotmentPatch(location: AllotmentPatchLocation) : FarmingPatch(location) {

    override val cycleMinutes = 10
    override val crops: List<Crop> = AllotmentSeed.values().toList()
    override val patchName = "allotment"
    override val harvestAnimation = Farming.DIG_ANIMATION
    override val harvestTool = Farming.SPADE
    override val seedsRequired = 3
    override val canBeWatered = true

    /**
     * Weeds are 0 (full weeds) to 3 (raked). A crop is its [AllotmentSeed.configIndexOffset] plus its stage,
     * plus 64 when watered, 128 when diseased, or 192 when dead.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? AllotmentSeed ?: return MAX_WEEDS
        val value = seed.configIndexOffset + stage
        return when {
            dead -> value + 192
            diseased -> value + 128
            watered -> value + 64
            else -> value
        }
    }
}
