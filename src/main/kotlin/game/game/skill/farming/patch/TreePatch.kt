package game.skill.farming.patch

import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents any tree patch.
 * Uses varp [Farming.TREE_PATCH_VARP].
 *
 * A fully grown tree must have its health checked before it can be chopped down. When it falls it turns into a stump,
 * which grows back into a tree after its [TreeSapling.tree]'s regrowth time. Digging up the stump empties the patch.
 *
 * @author hydrozoa
 */
class TreePatch(location: TreePatchLocation) : FarmingPatch(location) {

    companion object {

        /**
         * The chance from 0 to 1 that pruning a diseased tree removes all of the diseased leaves (wiki, given for fruit
         * trees).
         */
        const val PRUNE_CHANCE = 0.75
    }

    override val cycleMinutes = 40
    override val crops: List<Crop> = TreeSapling.values().toList()
    override val patchName = "tree patch"

    /**
     * Unused, trees are chopped down instead of harvested.
     */
    override val harvestAnimation = Farming.DIG_ANIMATION
    override val plantTool = Farming.SPADE
    override val plantLeftover = Farming.PLANT_POT
    override val plantAnimation = Farming.DIG_ANIMATION
    override val plantXpOnPlanting = true

    /**
     * If the fully grown tree has had its health checked, which lets it be chopped down.
     */
    var healthChecked = false

    /**
     * If the tree has been chopped down, leaving a stump.
     */
    var stump = false

    /**
     * The time the stump grows back into a tree, in epoch milliseconds.
     */
    var regrowAt = 0L

    /**
     * @return True if the fully grown tree is waiting to have its health checked.
     */
    fun canCheckHealth(): Boolean = harvestReady() && !healthChecked

    /**
     * @return True if the tree can be chopped down.
     */
    fun canChop(): Boolean = harvestReady() && healthChecked && !stump

    /**
     * Checks the health of the fully grown tree, which lets it be chopped down.
     */
    fun checkHealth() {
        healthChecked = true
    }

    /**
     * Turns the tree into a stump at time [now], in epoch milliseconds, which grows back after [regrowTicks].
     */
    fun fell(now: Long, regrowTicks: Int) {
        stump = true
        regrowAt = now + regrowTicks.ticks.inWholeMilliseconds
    }

    override fun advanceTimers(now: Long): Boolean {
        if (stump && now >= regrowAt) {
            stump = false
            regrowAt = 0L
            return true
        }
        return false
    }

    /**
     * Weeds are 0 (full weeds) to 3 (raked). A tree is its [TreeSapling.configIndexOffset] plus its stage minus one, so
     * the fully grown stage is the tree waiting to have its health checked. The two values after it are the tree that
     * can be chopped down and its stump. A diseased tree is its value plus 64, and a dead tree its value plus 128.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val sapling = crop as? TreeSapling ?: return MAX_WEEDS
        val value = sapling.configIndexOffset + stage - 1
        return when {
            dead -> value + 128
            diseased -> value + 64
            stump -> value + 2
            healthChecked -> value + 1
            else -> value
        }
    }

    override fun onReset() {
        healthChecked = false
        stump = false
        regrowAt = 0L
    }

    override fun saveExtra(obj: JsonObject) {
        obj.addProperty("health_checked", healthChecked)
        obj.addProperty("stump", stump)
        obj.addProperty("regrow_at", regrowAt)
    }

    override fun loadExtra(obj: JsonObject) {
        healthChecked = obj.get("health_checked")?.asBoolean ?: false
        stump = obj.get("stump")?.asBoolean ?: false
        regrowAt = obj.get("regrow_at")?.asLong ?: 0L
    }
}
