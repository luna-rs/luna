package game.skill.farming.special

import com.google.gson.JsonObject
import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Represents the cactus patch.
 * Uses varp [Farming.SPECIAL_PATCH_VARP].
 *
 * A fully grown cactus must have its health checked, after which it bears [MAX_SPINES] spines. They are picked one at a
 * time, and grow back one at a time every [cycleMinutes], like fruit on a fruit tree.
 *
 * @author hydrozoa
 */
class CactusPatch(location: SpecialPatchLocation) : FarmingPatch(location), HealthCheckPatch, PickablePatch {

    companion object {

        /**
         * The most spines a cactus can bear.
         */
        const val MAX_SPINES = 3
    }

    override val cycleMinutes = 80
    override val crops: List<Crop> = listOf(SpecialSeed.CACTUS)
    override val patchName = "cactus patch"
    override val harvestAnimation = Farming.PICK_FRUIT_ANIMATION
    override val checkedName = "cactus"

    /**
     * If the fully grown cactus has had its health checked.
     */
    var healthChecked = false

    /**
     * How many spines are on the cactus.
     */
    var spines = 0

    /**
     * The time the spines were last brought up to date, in epoch milliseconds.
     */
    var spinesUpdated = 0L

    /**
     * @return True if the fully grown cactus is waiting to have its health checked.
     */
    override fun canCheckHealth(): Boolean = harvestReady() && !healthChecked

    /**
     * Checks the health of the fully grown cactus, which then bears [MAX_SPINES] spines.
     */
    override fun checkHealth() {
        healthChecked = true
        spines = MAX_SPINES
    }

    override fun canPick(): Boolean = harvestReady() && healthChecked && spines > 0

    override fun pick() {
        spines--
    }

    /**
     * Grows back one spine for every multiple of [cycleMinutes] passed since the spines were last brought up to date, up
     * to [MAX_SPINES].
     */
    override fun advanceTimers(now: Long): Boolean {
        val last = spinesUpdated
        spinesUpdated = now
        if (last <= 0L || !harvestReady() || !healthChecked || spines >= MAX_SPINES) {
            return false
        }
        val grown = GrowthEngine.boundaries(last, now, cycleMinutes)
        if (grown <= 0) {
            return false
        }
        spines = minOf(MAX_SPINES.toLong(), spines + grown).toInt()
        return true
    }

    /**
     * Weeds are 0 (full weeds) to 3 (raked). The seedling is 8 and the growing stages 9 to 14, the cactus waiting to have
     * its health checked is 31, and a checked cactus is 15 plus its spines. The diseased growing stages are 19 to 24 and
     * the dead ones 25 to 30.
     */
    override fun encode(): Int {
        if (needsRaking()) {
            return MAX_WEEDS - weeds
        }
        if (crop == null) {
            return MAX_WEEDS
        }
        return when {
            dead -> 25 + stage - 2
            diseased -> 19 + stage - 2
            !harvestReady() -> 8 + stage - 1
            !healthChecked -> 31
            else -> 15 + spines
        }
    }

    override fun onReset() {
        healthChecked = false
        spines = 0
        spinesUpdated = 0L
    }

    override fun saveExtra(obj: JsonObject) {
        obj.addProperty("health_checked", healthChecked)
        obj.addProperty("spines", spines)
        obj.addProperty("spines_updated", spinesUpdated)
    }

    override fun loadExtra(obj: JsonObject) {
        healthChecked = obj.get("health_checked")?.asBoolean ?: false
        spines = obj.get("spines")?.asInt ?: 0
        spinesUpdated = obj.get("spines_updated")?.asLong ?: 0L
    }
}
