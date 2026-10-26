package game.skill.farming.flower

import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.allotment.*
import game.skill.farming.patch.*

/**
 * Represents any flower patch.
 * Uses varp [Farming.FLOWER_PATCH_VARP].
 *
 * A fully grown flower, or a scarecrow standing in the patch, protects the allotments of the same farming area from
 * disease. See [protection] and [AllotmentSeed.flowerProtection].
 *
 * @author hydrozoa
 */
class FlowerPatch(location: FlowerPatchLocation) : FarmingPatch(location) {

    companion object {

        /**
         * The value drawn for a scarecrow in a weeded patch. Every weed around it lowers the value by one.
         */
        const val SCARECROW_VALUE = 36
    }

    override val cycleMinutes = 5
    override val crops: List<Crop> = FlowerSeed.values().toList()
    override val patchName = "flower patch"
    override val harvestAnimation = Farming.PICK_ANIMATION
    override val canBeWatered = true

    /**
     * If a scarecrow is standing in this patch.
     */
    var scarecrow = false

    override fun isOccupied(): Boolean = super.isOccupied() || scarecrow

    /**
     * Places a scarecrow in this patch.
     */
    fun placeScarecrow() {
        scarecrow = true
    }

    /**
     * Returns what in this patch protects the allotments of its farming area: the scarecrow, or the flower once it is
     * fully grown and alive. `null` if nothing does.
     */
    fun protection(): FlowerProtection? = when {
        scarecrow -> FlowerProtection.SCARECROW
        harvestReady() && !diseased -> (crop as? FlowerSeed)?.protection
        else -> null
    }

    /**
     * Weeds are 0 (full weeds) to 3 (raked), also around a scarecrow, which is [SCARECROW_VALUE] minus its weeds. A
     * flower is drawn the same way as allotments, see [encodeWaterable].
     */
    override fun encode(): Int {
        if (scarecrow) {
            return SCARECROW_VALUE - weeds
        }
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? FlowerSeed ?: return MAX_WEEDS
        return encodeWaterable(seed.configIndexOffset)
    }

    override fun onReset() {
        scarecrow = false
    }

    override fun saveExtra(obj: JsonObject) {
        obj.addProperty("scarecrow", scarecrow)
    }

    override fun loadExtra(obj: JsonObject) {
        scarecrow = obj.get("scarecrow")?.asBoolean ?: false
    }
}
