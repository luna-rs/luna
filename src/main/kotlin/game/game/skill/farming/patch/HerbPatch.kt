package game.skill.farming.patch

import game.skill.farming.*
import game.skill.farming.seed.*
import io.luna.game.model.item.*

/**
 * Represents any herb patch.
 * Growths stage can go from 0 to 5 for all herbs.
 *
 * @hydrozoa
 */
class HerbPatch(val location: HerbPatchLocation) : FarmingPatch() {

    var plantType: HerbSeed? = null

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

    override fun getVarpId(): Int {
        return 515
    }

    override fun hasPlant(): Boolean {
        return plantType != null
    }

    fun plant(seed: HerbSeed): Boolean {
        if (weeds > 0) {
            return false
        }
        plantType = seed
        growthStage = 1
        return true
    }

    override fun harvestReady(): Boolean {
        return growthStage == 5
    }

    override fun produce(): Item? {
        return plantType?.crop
    }

    override fun reset(includeWeeds: Boolean) {
        super.reset(includeWeeds)
        plantType = null
    }
}