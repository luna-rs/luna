package game.skill.farming.herb

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Represents any herb patch.
 * Growths stage can go from 1 to 5 for all herbs.
 *
 * @hydrozoa
 */
class HerbPatch(location: HerbPatchLocation) : FarmingPatch(location) {

    override val cycleMinutes = 20
    override val crops: List<Crop> = HerbSeed.values().toList()
    override val patchName = "herb patch"
    override val harvestAnimation = Farming.PICK_ANIMATION

    /**
     * Weeds are 0 (full weeds) to 3 (raked). A herb is drawn as its [HerbSeed.configIndexOffset] plus its stage.
     * Diseased herbs take three values each from 128, in [HerbSeed] order, and dead herbs share the values 170 to 172.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? HerbSeed ?: return MAX_WEEDS
        return when {
            dead -> 170 + stage - 2
            diseased -> 128 + seed.ordinal * 3 + stage - 2
            else -> seed.configIndexOffset + stage
        }
    }
}
