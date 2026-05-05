package game.skill.farming.patch

import game.skill.farming.*
import game.skill.farming.seed.*
import io.luna.game.model.item.*

/**
 * Represents any allotment patch.
 * Uses varp id 504 and 505
 *
 * todo finish
 *
 * @author hydrozoa
 */
class AllotmentPatch(val location: AllotmentPatchLocation) : FarmingPatch() {

    private var plantType: AllotmentSeed? = null

    override fun getVarpValue(): Int {
        var varpValue: Int = 3
        if (needsRaking()) {
            varpValue = 3 - weeds
        } else if (plantType == null) {
            varpValue = 3
        }

        // todo

        return varpValue
    }

    override fun getVarpId(): Int {
        return location.config
    }

    override fun hasPlant(): Boolean {
        return plantType != null
    }

    override fun harvestReady(): Boolean {
        return plantType != null && growthStage >= 10
    }

    override fun produce(): Item? {
        return Item.byName("Weeds")
    }

    override fun reset(includeWeeds: Boolean) {
        super.reset(includeWeeds)
        plantType = null
    }
}