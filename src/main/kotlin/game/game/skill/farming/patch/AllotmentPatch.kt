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
        } else if (plantType != null) {
            varpValue = plantType!!.configIndexOffset + growthStage

            if (isDiseased) {
                varpValue += 128
            } else if (isDead) {
                varpValue += 192
            }
        }

        varpValue = varpValue shl location.shifts
        return varpValue
    }

    fun plant(seed: AllotmentSeed): Boolean {
        if (weeds > 0) {
            return false
        }
        plantType = seed
        growthStage = 1
        return true
    }

    override fun getVarpId(): Int {
        return location.config
    }

    override fun hasPlant(): Boolean {
        return plantType != null
    }

    override fun produce(): Item? {
        return plantType?.crop
    }

    override fun reset(includeWeeds: Boolean) {
        super.reset(includeWeeds)
        plantType = null
    }

    override fun maxGrowth(): Int? {
        return plantType?.growthStages
    }
}