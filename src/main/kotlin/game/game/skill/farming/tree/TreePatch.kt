package game.skill.farming.tree

import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Represents any tree patch.
 * Uses varp [Farming.TREE_PATCH_VARP].
 *
 * A fully grown tree must have its health checked before it can be chopped down. When it falls it turns into a stump,
 * which grows back into a tree after its [TreeSapling.tree]'s regrowth time. Digging up the stump empties the patch.
 *
 * @author hydrozoa
 */
class TreePatch(location: TreePatchLocation) : AbstractTreePatch(location) {

    override val cycleMinutes = 40
    override val crops: List<Crop> = TreeSapling.values().toList()
    override val patchName = "tree patch"

    /**
     * Unused, trees are chopped down instead of harvested.
     */
    override val harvestAnimation = Farming.DIG_ANIMATION

    /**
     * The time the stump grows back into a tree, in epoch milliseconds.
     */
    var regrowAt = 0L

    override fun canChop(): Boolean = harvestReady() && healthChecked && !stump

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
        super.onReset()
        regrowAt = 0L
    }

    override fun saveExtra(obj: JsonObject) {
        super.saveExtra(obj)
        obj.addProperty("regrow_at", regrowAt)
    }

    override fun loadExtra(obj: JsonObject) {
        super.loadExtra(obj)
        regrowAt = obj.get("regrow_at")?.asLong ?: 0L
    }
}
