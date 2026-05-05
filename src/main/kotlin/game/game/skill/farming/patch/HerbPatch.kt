package game.skill.farming.patch

import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents any herb patch.
 *
 * @hydrozoa
 */
class HerbPatch(val location: HerbPatchLocation) : FarmingPatch() {

    var plantType: HerbSeeds? = null
    var growthStage = 0 // from 0 to 5

    override fun getVarpValue(): Int {
        var varpValue: Int = 3
        if (needsRaking()) {
            varpValue = 3 - weeds
        } else if (plantType == null) {
            varpValue = 3
        } else if (plantType != null)  {
            varpValue = 3 + growthStage
        }
        varpValue = varpValue shl location.shifts
        return varpValue
    }

    override fun hasPlant(): Boolean {
        return plantType != null
    }

    fun plant(seed: HerbSeeds): Boolean {
        if (weeds > 0) {
            return false
        }
        plantType = seed
        growthStage = 1
        return true
    }

    fun reset() {
        plantType = null
        growthStage = 0
        weeds = 3
    }
}