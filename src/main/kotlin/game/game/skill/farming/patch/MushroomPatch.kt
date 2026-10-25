package game.skill.farming.patch

import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents the mushroom patch.
 * Uses varp [Farming.SPECIAL_PATCH_VARP].
 *
 * Fully grown bittercaps are always [MUSHROOMS] mushrooms, picked one at a time. They are tracked as harvest lives that
 * are never saved, so compost doesn't change the yield, and the client shows how many are left.
 *
 * @author hydrozoa
 */
class MushroomPatch(location: SpecialPatchLocation) : FarmingPatch(location) {

    companion object {

        /**
         * How many mushrooms a fully grown patch has.
         */
        const val MUSHROOMS = 6
    }

    override val cycleMinutes = 40
    override val crops: List<Crop> = listOf(SpecialSeed.BITTERCAP)
    override val patchName = "mushroom patch"
    override val harvestAnimation = Farming.PICK_ANIMATION

    override fun startingLives(): Int = MUSHROOMS

    override fun chanceToSave(level: Int): Double = 0.0

    /**
     * Weeds are 0 (full weeds) to 3 (raked). The spore is 4 and the growing stages 5 to 9. A fully grown patch is 16
     * minus the mushrooms left, so 10 is six mushrooms and 15 is one. The diseased growing stages are 16 to 20 and the
     * dead ones 21 to 25.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        if (crop == null) {
            return MAX_WEEDS
        }
        return when {
            dead -> 21 + stage - 2
            diseased -> 16 + stage - 2
            !harvestReady() -> 4 + stage - 1
            else -> 16 - harvestLives.coerceIn(1, MUSHROOMS)
        }
    }
}
