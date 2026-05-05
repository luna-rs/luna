package game.skill.farming.patch

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
class AllotmentPatch(val southEastern: Boolean) : FarmingPatch() {

    private var plantType: AllotmentSeeds? = null

    override fun getVarpValue(): Int {
        var varpValue: Int = 3
        if (needsRaking()) {
            varpValue = 3 - weeds
        } else if (plantType == null) {
            varpValue = 3
        }

        if (southEastern) {
            varpValue = varpValue shl 8
        }

        return varpValue
    }

    override fun hasPlant(): Boolean {
        return plantType != null
    }

    override fun harvestReady(): Boolean {
        return false
    }

    override fun produce(): Item? {
        return Item.byName("Weeds")
    }

    override fun reset(includeWeeds: Boolean) {
        TODO("Not yet implemented")
    }
}