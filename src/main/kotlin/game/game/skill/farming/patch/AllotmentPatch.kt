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
     * The location of the flower patch of this allotment's farming area.
     */
    private val flowerPatchLocation = location.flowerPatch

    /**
     * The flower patch of this allotment's farming area, which can protect it from disease.
     */
    private var flowerPatch: FlowerPatch? = null

    override fun link(patches: Map<PatchLocation, FarmingPatch>) {
        flowerPatch = patches[flowerPatchLocation] as? FlowerPatch
    }

    /**
     * The crop is protected when the flower patch of the same farming area holds what protects it, see
     * [AllotmentSeed.flowerProtection].
     */
    override fun isProtected(): Boolean {
        val seed = crop as? AllotmentSeed ?: return false
        val protection = flowerPatch?.protection() ?: return false
        return protection in seed.flowerProtection
    }

    /**
     * Weeds are 0 (full weeds) to 3 (raked). A crop is its [AllotmentSeed.configIndexOffset] plus its stage,
     * plus 64 when watered, 128 when diseased, or 192 when dead. See [encodeWaterable].
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? AllotmentSeed ?: return MAX_WEEDS
        return encodeWaterable(seed.configIndexOffset)
    }
}
