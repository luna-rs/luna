package game.skill.farming.special

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Represents the belladonna patch.
 * Uses varp [Farming.SPECIAL_PATCH_VARP].
 *
 * Belladonna is harvested like herbs, with harvest lives, but only by a player wearing gloves.
 *
 * @author hydrozoa
 */
class BelladonnaPatch(location: SpecialPatchLocation) : FarmingPatch(location) {

    override val cycleMinutes = 80
    override val crops: List<Crop> = listOf(SpecialSeed.BELLADONNA)
    override val patchName = "belladonna patch"
    override val harvestAnimation = Farming.PICK_ANIMATION

    /**
     * Weeds are 0 (full weeds) to 3 (raked). The seedling is 4, the growing stages 5 to 7 and fully grown 8. The
     * diseased growing stages are 9 to 11 and the dead ones 12 to 14.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        if (crop == null) {
            return MAX_WEEDS
        }
        return when {
            dead -> 12 + stage - 2
            diseased -> 9 + stage - 2
            else -> 4 + stage - 1
        }
    }
}
