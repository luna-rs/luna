package game.skill.farming.patch;

import io.luna.game.model.item.*
import io.luna.game.model.mob.*


/**
 * Represents any farming patch.
 * @author hydrozoa
 */
abstract class FarmingPatch {

    private var isDead: Boolean = false
    private var isDiseased: Boolean = false
    var weeds: Int = 3
    var produceAvailable = 0

    /**
     * If this patchs needs raking.
     */
    fun needsRaking(): Boolean {
        return weeds > 0
    }

    /**
     * Does one cycle of raking the patch.
     * @return Returns {@code true} if completed raking, otherwise false
     */
    fun rake(plr: Player): Boolean {
        plr.inventory.add(Item.byName("Weeds"))
        weeds -= 1
        return weeds <= 0
    }

    /**
     * Does one cycle of harvesting the patch.
     * @return True if completed harvesting, otherwise false
     */
    fun harvest(plr: Player): Boolean {
        plr.inventory.add(produce())
        produceAvailable -= 1
        if (produceAvailable <= 0) {
            reset(false)
            return true
        }
        return false
    }

    /**
     * The value this patch contributes to the patch state, stored in a varp.
     */
    abstract fun getVarpValue(): Int

    /**
     * @return True if the player has planted something here
     */
    abstract fun hasPlant(): Boolean

    abstract fun harvestReady(): Boolean

    abstract fun produce(): Item?

    abstract fun reset(includeWeeds: Boolean)

}
