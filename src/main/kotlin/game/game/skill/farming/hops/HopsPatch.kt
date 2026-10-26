package game.skill.farming.hops

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Represents any hops patch.
 * Uses varp [Farming.HOPS_PATCH_VARP].
 *
 * @author hydrozoa
 */
class HopsPatch(location: HopsPatchLocation) : FarmingPatch(location) {

    override val cycleMinutes = 10
    override val crops: List<Crop> = HopsSeed.values().toList()
    override val patchName = "hops patch"
    override val harvestAnimation = Farming.PICK_FRUIT_ANIMATION
    override val canBeWatered = true

    /**
     * Hops and barley need 4 seeds, and jute needs 3 (wiki).
     */
    override fun seedsRequired(crop: Crop): Int = (crop as? HopsSeed)?.seeds ?: seedsRequired

    /**
     * Drawn the same way as allotments: weeds are 0 (full weeds) to 3 (raked), and a crop is its
     * [HopsSeed.configIndexOffset] plus its stage, plus 64 when watered, 128 when diseased, or 192 when dead. See
     * [encodeWaterable].
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? HopsSeed ?: return MAX_WEEDS
        return encodeWaterable(seed.configIndexOffset)
    }
}
