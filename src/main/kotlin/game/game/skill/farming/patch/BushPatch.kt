package game.skill.farming.patch

import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.seed.*

/**
 * Represents any bush patch.
 * Uses varp [Farming.BUSH_PATCH_VARP].
 *
 * A fully grown bush must have its health checked, after which it bears [MAX_BERRIES] berries. They are picked one at a
 * time, and grow back one at a time every [cycleMinutes]. A fully grown bush whose health has been checked can be dug
 * up with a spade.
 *
 * @author hydrozoa
 */
class BushPatch(location: BushPatchLocation) : FarmingPatch(location), HealthCheckPatch, PickablePatch {

    companion object {

        /**
         * The most berries a bush can bear.
         */
        const val MAX_BERRIES = 4
    }

    override val cycleMinutes = 20
    override val crops: List<Crop> = BushSeed.values().toList()
    override val patchName = "bush patch"
    override val harvestAnimation = Farming.PICK_FRUIT_ANIMATION
    override val plantXpOnPlanting = true
    override val checkedName = "bush"

    /**
     * If the fully grown bush has had its health checked.
     */
    var healthChecked = false

    /**
     * How many berries are on the bush.
     */
    var berries = 0

    /**
     * The time the berries were last brought up to date, in epoch milliseconds.
     */
    var berriesUpdated = 0L

    override fun canCheckHealth(): Boolean = harvestReady() && !healthChecked

    /**
     * Checks the health of the fully grown bush, which then bears [MAX_BERRIES] berries.
     */
    override fun checkHealth() {
        healthChecked = true
        berries = MAX_BERRIES
    }

    override fun canPick(): Boolean = harvestReady() && healthChecked && berries > 0

    override fun pick() {
        berries--
    }

    /**
     * @return True if the bush can be dug up: fully grown with its health checked, whatever berries are left.
     */
    fun canDigUp(): Boolean = harvestReady() && healthChecked

    /**
     * Grows back one berry for every multiple of [cycleMinutes] passed since the berries were last brought up to date,
     * up to [MAX_BERRIES].
     */
    override fun advanceTimers(now: Long): Boolean {
        val last = berriesUpdated
        berriesUpdated = now
        if (last <= 0L || !harvestReady() || !healthChecked || berries >= MAX_BERRIES) {
            return false
        }
        val grown = GrowthEngine.boundaries(last, now, cycleMinutes)
        if (grown <= 0) {
            return false
        }
        berries = minOf(MAX_BERRIES.toLong(), berries + grown).toInt()
        return true
    }

    /**
     * Weeds are 0 (full weeds) to 3 (raked). A bush is its [BushSeed.configIndexOffset] plus its stage minus one, plus
     * its berries once its health has been checked, and [BushSeed.checkHealthValue] while it waits for that. A diseased
     * bush is its value plus 64 and a dead bush its value plus 128, except poison ivy, whose diseased growing stages are
     * 210 to 216 and dead ones 217 to 224.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        val seed = crop as? BushSeed ?: return MAX_WEEDS
        val value = seed.configIndexOffset + stage - 1
        val poisonIvy = seed == BushSeed.POISON_IVY
        return when {
            dead -> if (poisonIvy) 217 + stage - 2 else value + 128
            diseased -> if (poisonIvy) 210 + stage - 2 else value + 64
            !harvestReady() -> value
            !healthChecked -> seed.checkHealthValue
            else -> value + berries
        }
    }

    override fun onReset() {
        healthChecked = false
        berries = 0
        berriesUpdated = 0L
    }

    override fun saveExtra(obj: JsonObject) {
        obj.addProperty("health_checked", healthChecked)
        obj.addProperty("berries", berries)
        obj.addProperty("berries_updated", berriesUpdated)
    }

    override fun loadExtra(obj: JsonObject) {
        healthChecked = obj.get("health_checked")?.asBoolean ?: false
        berries = obj.get("berries")?.asInt ?: 0
        berriesUpdated = obj.get("berries_updated")?.asLong ?: 0L
    }
}
