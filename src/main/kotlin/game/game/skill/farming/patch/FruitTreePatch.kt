package game.skill.farming.patch

import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents any fruit tree patch.
 * Uses varp [Farming.FRUIT_TREE_PATCH_VARP].
 *
 * Once a fully grown fruit tree has had its health checked it bears [MAX_FRUIT] fruit, which are picked one at a time.
 * Picked fruit grows back one at a time, every [FRUIT_CYCLE_MINUTES]. A tree with no fruit left can be chopped down,
 * leaving a stump that stays until it is dug up.
 *
 * @author hydrozoa
 */
class FruitTreePatch(location: FruitTreePatchLocation) : AbstractTreePatch(location) {

    companion object {

        /**
         * The most fruit a fruit tree can bear (wiki).
         */
        const val MAX_FRUIT = 6

        /**
         * How many minutes it takes for one picked fruit to grow back.
         */
        const val FRUIT_CYCLE_MINUTES = 40
    }

    override val cycleMinutes = 160
    override val crops: List<Crop> = FruitTreeSapling.values().toList()
    override val patchName = "fruit tree patch"
    override val harvestAnimation = Farming.PICK_FRUIT_ANIMATION

    /**
     * How much fruit is on the tree.
     */
    var fruit = 0

    /**
     * The time the fruit was last brought up to date, in epoch milliseconds.
     */
    var fruitUpdated = 0L

    /**
     * A fruit tree is only at risk of disease for four growth cycles (wiki): every stage from the first growing stage,
     * except the last growing stage before fully grown.
     */
    override fun canBeDiseased(stage: Int): Boolean {
        val stages = crop?.stages ?: return false
        return stage in 2 until stages - 1
    }

    /**
     * Checks the health of the fully grown tree, which then bears [MAX_FRUIT] fruit.
     */
    override fun checkHealth() {
        super.checkHealth()
        fruit = MAX_FRUIT
    }

    /**
     * @return True if there is fruit on the tree to pick.
     */
    fun canPick(): Boolean = isBearing() && fruit > 0

    override fun canChop(): Boolean = isBearing() && fruit == 0

    /**
     * Picks one fruit from the tree.
     */
    fun pick() {
        fruit--
    }

    /**
     * Chops down the tree, leaving a stump until it is dug up.
     */
    fun fell() {
        stump = true
    }

    /**
     * Grows back one fruit for every multiple of [FRUIT_CYCLE_MINUTES] passed since the fruit was last brought up to
     * date, up to [MAX_FRUIT].
     */
    override fun advanceTimers(now: Long): Boolean {
        val last = fruitUpdated
        fruitUpdated = now
        if (last <= 0L || !isBearing() || fruit >= MAX_FRUIT) {
            return false
        }
        val grown = GrowthEngine.boundaries(last, now, FRUIT_CYCLE_MINUTES)
        if (grown <= 0) {
            return false
        }
        fruit = minOf(MAX_FRUIT.toLong(), fruit + grown).toInt()
        return true
    }

    /**
     * @return True if the tree is standing, fully grown and has had its health checked, so it bears fruit.
     */
    private fun isBearing(): Boolean = harvestReady() && healthChecked && !stump

    /**
     * Weeds are 0 (full weeds) to 3 (raked). Every fruit tree is drawn from a block of values starting at its
     * [FruitTreeSapling.configIndexOffset] `b`: `b` is the seedling, then five growing stages, `b+6` is fully grown with
     * no fruit and `b+7` to `b+12` are one to six fruit. `b+13` to `b+17` are the diseased growing stages and `b+18`
     * diseased fully grown, `b+19` to `b+23` the dead growing stages and `b+24` dead fully grown, `b+25` is the stump and
     * `b+26` the tree waiting to have its health checked.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val sapling = crop as? FruitTreeSapling ?: return MAX_WEEDS
        val base = sapling.configIndexOffset
        val grown = stage >= sapling.stages
        return when {
            dead -> if (grown) base + 24 else base + 18 + stage - 1
            diseased -> if (grown) base + 18 else base + 12 + stage - 1
            !grown -> base + stage - 1
            stump -> base + 25
            !healthChecked -> base + 26
            else -> base + 6 + fruit
        }
    }

    override fun onReset() {
        super.onReset()
        fruit = 0
        fruitUpdated = 0L
    }

    override fun saveExtra(obj: JsonObject) {
        super.saveExtra(obj)
        obj.addProperty("fruit", fruit)
        obj.addProperty("fruit_updated", fruitUpdated)
    }

    override fun loadExtra(obj: JsonObject) {
        super.loadExtra(obj)
        fruit = obj.get("fruit")?.asInt ?: 0
        fruitUpdated = obj.get("fruit_updated")?.asLong ?: 0L
    }
}
