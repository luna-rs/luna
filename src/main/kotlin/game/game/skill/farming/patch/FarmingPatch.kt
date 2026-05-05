package game.skill.farming.patch;

import game.skill.farming.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.*

/**
 * Represents any farming patch.
 * @author hydrozoa
 */
abstract class FarmingPatch {

    var isDead: Boolean = false
    var isDiseased: Boolean = false

    /**
     * How many weeds are on the patch. Can be 0 to 3, where 3 is full of weeds.
     */
    var weeds: Int = 3

    /**
     * How many crops can be harvested.
     */
    var produceAvailable = 0

    /**
     * Growth stage of current plant. If no plant is planted this will be 0.
     */
    var growthStage = 0

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
     * The id of the config that changes this patch.
     */
    abstract fun getVarpId(): Int

    /**
     * @return True if the player has planted something here
     */
    abstract fun hasPlant(): Boolean

    /**
     * @return True if this patch is ready to be harvested.
     */
    fun harvestReady(): Boolean {
        return growthStage == maxGrowth() && hasPlant()
    }

    /**
     * @return Item that the player will harvest from this patch.
     */
    abstract fun produce(): Item?

    /**
     * Resets the patch. If weeds are included it will reset to fully grown weeds,
     * if not it will reset to no weeds or plants.
     */
    open fun reset(includeWeeds: Boolean) {
        growthStage = 0
        if (includeWeeds) {
            weeds = 3
        }
    }

    abstract fun maxGrowth(): Int?

}
