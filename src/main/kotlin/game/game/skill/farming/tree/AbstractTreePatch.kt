package game.skill.farming.tree

import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.fruitTree.*
import game.skill.farming.patch.*

/**
 * Represents any patch a tree grows in, such as a [TreePatch] or a [FruitTreePatch]. Holds what every tree shares: it
 * is planted from a sapling with a spade, and once fully grown it must have its health checked before it can be used.
 * Once chopped down it leaves a stump, which is dug up with a spade to empty the patch.
 *
 * How the tree is chopped down, and what happens to its stump, is up to the subclass.
 *
 * @author hydrozoa
 */
abstract class AbstractTreePatch(location: PatchLocation) : FarmingPatch(location) {

    companion object {

        /**
         * The chance from 0 to 1 that pruning a diseased tree removes all of the diseased leaves (wiki, given for fruit
         * trees).
         */
        const val PRUNE_CHANCE = 0.75
    }

    override val plantTool = Farming.SPADE
    override val plantLeftover = Farming.PLANT_POT
    override val plantAnimation = Farming.DIG_ANIMATION
    override val plantXpOnPlanting = true

    /**
     * If the fully grown tree has had its health checked.
     */
    var healthChecked = false

    /**
     * If the tree has been chopped down, leaving a stump.
     */
    var stump = false

    /**
     * @return True if the fully grown tree is waiting to have its health checked.
     */
    fun canCheckHealth(): Boolean = harvestReady() && !healthChecked

    /**
     * @return True if the tree can be chopped down.
     */
    abstract fun canChop(): Boolean

    /**
     * Checks the health of the fully grown tree.
     */
    open fun checkHealth() {
        healthChecked = true
    }

    override fun onReset() {
        healthChecked = false
        stump = false
    }

    override fun saveExtra(obj: JsonObject) {
        obj.addProperty("health_checked", healthChecked)
        obj.addProperty("stump", stump)
    }

    override fun loadExtra(obj: JsonObject) {
        healthChecked = obj.get("health_checked")?.asBoolean ?: false
        stump = obj.get("stump")?.asBoolean ?: false
    }
}
