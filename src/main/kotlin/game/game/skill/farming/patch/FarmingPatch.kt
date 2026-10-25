package game.skill.farming.patch

import api.predef.*
import com.google.gson.JsonObject
import game.skill.farming.*
import io.luna.game.model.mob.block.Animation
import kotlin.math.floor

/**
 * Represents any farming patch. Holds the state and the rules every patch shares, while a subclass overrides the hooks
 * for what differs: what it can hold, whether it can get diseased, how much it yields, and how it is drawn.
 *
 * The state is changed through the shared behaviour ([rake], [plant], [water], [applyCompost], [cure], [clear],
 * [harvest] and [tick]), which only changes the state. Requirements, items, animations and experience are handled by
 * the caller.
 *
 * @author hydrozoa
 */
abstract class FarmingPatch(val location: PatchLocation) {

    companion object {

        /**
         * The amount of weeds on a patch that is fully overgrown.
         */
        const val MAX_WEEDS = 3

        /**
         * The harvest lives a fully grown crop starts with, before compost.
         */
        const val BASE_HARVEST_LIVES = 3

        /**
         * How much watering lowers the disease chance for one growth cycle, after compost. An unchecked placeholder,
         * it is not from the wiki.
         */
        const val WATERED_DISEASE_REDUCTION = 0.5
    }

    /**
     * How many weeds are on the patch. Can be 0 to [MAX_WEEDS], where [MAX_WEEDS] is full of weeds.
     */
    var weeds = MAX_WEEDS

    /**
     * The crop planted in this patch, or `null` if it is empty.
     */
    var crop: Crop? = null

    /**
     * The growth stage of the planted crop, where the seed is stage 1. If no crop is planted this will be 0.
     */
    var stage = 0

    /**
     * If the crop has been watered this growth cycle.
     */
    var watered = false

    /**
     * If the crop is diseased. A diseased crop stops growing, and dies if it is not cured by the end of its next
     * growth cycle.
     */
    var diseased = false

    /**
     * If the crop is dead.
     */
    var dead = false

    /**
     * The compost this patch has been treated with, which lasts until the patch is next emptied.
     */
    var compost = CompostType.NONE

    /**
     * How many more harvests the fully grown crop can give before it runs out. See [chanceToSave].
     */
    var harvestLives = 0

    /**
     * The time this patch was last brought up to date, in epoch milliseconds. See [GrowthEngine].
     */
    var lastUpdated = 0L

    /**
     * How many minutes one growth cycle lasts.
     */
    abstract val cycleMinutes: Int

    /**
     * The crops that can be planted in this patch.
     */
    abstract val crops: List<Crop>

    /**
     * The name of this patch type in messages, for example "allotment".
     */
    abstract val patchName: String

    /**
     * The animation played for every crop harvested.
     */
    abstract val harvestAnimation: Animation

    /**
     * The tool required to harvest this patch, or `null` if none is needed.
     */
    open val harvestTool: Int? = null

    /**
     * How many seeds are needed to plant this patch.
     */
    open val seedsRequired = 1

    /**
     * How many seeds of [crop] are needed to plant this patch. By default [seedsRequired], for every crop.
     */
    open fun seedsRequired(crop: Crop): Int = seedsRequired

    /**
     * If crops in this patch can be watered.
     */
    open val canBeWatered = false

    /**
     * The tool required to plant allotments and herb patches.
     */
    open val plantTool = Farming.SEED_DIBBER

    /**
     * The item left over for every seed planted, or `null` if nothing is.
     */
    open val plantLeftover: Int? = null

    /**
     * The animation played when planting.
     */
    open val plantAnimation = Farming.SEED_DIBBING_ANIMATION

    /**
     * If the crop's [Crop.plantXp] is given when it is planted, instead of when the patch is fully harvested.
     */
    open val plantXpOnPlanting = false

    /**
     * Determines if the crop can become diseased at [stage]. By default every stage can, except the seed and fully
     * grown (wiki).
     */
    open fun canBeDiseased(stage: Int): Boolean {
        val stages = crop?.stages ?: return false
        return stage in 2 until stages
    }

    /**
     * The chance out of 128 that the crop becomes diseased this growth cycle. Compost and supercompost lower the
     * crop's [Crop.baseDiseaseChance] by 50% and 80% rounded down (wiki), and watering lowers it further.
     */
    open fun diseaseChance(): Int {
        val base = crop?.baseDiseaseChance ?: return 0
        var chance = base - floor(base * compost.diseaseReduction).toInt()
        if (watered) {
            chance -= floor(chance * WATERED_DISEASE_REDUCTION).toInt()
        }
        return chance
    }

    /**
     * Determines if the crop is protected from disease, for example by a flower patch or a gardener.
     */
    open fun isProtected(): Boolean = false

    /**
     * The harvest lives the crop starts with once it is fully grown.
     */
    open fun startingLives(): Int = BASE_HARVEST_LIVES + compost.extraLives

    /**
     * The chance from 0 to 1 that a harvest at Farming [level] does not use up a harvest life (wiki).
     */
    open fun chanceToSave(level: Int): Double {
        val planted = crop ?: return 0.0
        val roll = floor(planted.chanceToSaveLow * (99 - level) / 98.0 +
                             planted.chanceToSaveHigh * (level - 1) / 98.0 + 0.5)
        return (1 + roll) / 256.0
    }

    /**
     * Called when the last harvest life is used up. By default the patch is cleared.
     */
    open fun onFullyHarvested() {
        reset(false)
    }

    /**
     * Brings any timers of this patch other than its growth cycles up to date with [now], in epoch milliseconds. Called
     * every time the patch is brought up to date, see [GrowthEngine]. By default there are none.
     * @return True if the patch changed.
     */
    open fun advanceTimers(now: Long): Boolean = false

    /**
     * Called once after all of the player's [patches] are loaded, so a patch can keep a reference to another patch it
     * depends on. By default there is none.
     */
    open fun link(patches: Map<PatchLocation, FarmingPatch>) {
    }

    /**
     * Determines if something is in this patch that stops it from being planted. By default, only a planted crop.
     */
    open fun isOccupied(): Boolean = hasPlant()

    /**
     * Called when the patch is reset, to reset any state a subclass adds. See [reset].
     */
    protected open fun onReset() {
    }

    /**
     * Saves any state a subclass adds into [obj]. See [save].
     */
    protected open fun saveExtra(obj: JsonObject) {
    }

    /**
     * Loads any state a subclass adds from [obj], previously written by [saveExtra]. See [load].
     */
    protected open fun loadExtra(obj: JsonObject) {
    }

    /**
     * The value of this patch's byte within its varp, before it is shifted into place. This must only produce values
     * the patch object defines, anything else is drawn as full weeds.
     */
    abstract fun encode(): Int

    /**
     * Encodes the planted crop for a patch drawn the way allotments and hops patches are: the crop is its
     * [configIndexOffset] plus its stage, plus 64 when watered, 128 when diseased, or 192 when dead.
     */
    protected fun encodeWaterable(configIndexOffset: Int): Int {
        val value = configIndexOffset + stage
        return when {
            dead -> value + 192
            diseased -> value + 128
            watered -> value + 64
            else -> value
        }
    }

    /**
     * The value this patch contributes to its varp.
     */
    fun getVarpValue(): Int = encode() shl location.shifts

    /**
     * Returns the crop planted from [seedId], or `null` if it cannot be planted in this patch.
     */
    fun findCrop(seedId: Int): Crop? = crops.find { it.seedId == seedId }

    /**
     * If this patch needs raking.
     */
    fun needsRaking(): Boolean = weeds > 0

    /**
     * @return True if the player has planted something here
     */
    fun hasPlant(): Boolean = crop != null

    /**
     * @return True if the crop is alive, healthy, and not yet fully grown.
     */
    fun isGrowing(): Boolean {
        val stages = crop?.stages ?: return false
        return !diseased && !dead && stage < stages
    }

    /**
     * @return True if this patch is ready to be harvested.
     */
    fun harvestReady(): Boolean {
        val stages = crop?.stages ?: return false
        return !dead && stage >= stages
    }

    /**
     * Removes one weed from the patch.
     * @return True if the patch has no weeds left.
     */
    fun rake(): Boolean {
        if (weeds > 0) {
            weeds--
        }
        return weeds == 0
    }

    /**
     * Grows one weed on the patch, if it is empty.
     * @return True if a weed was grown.
     */
    fun growWeeds(): Boolean {
        if (crop == null && weeds < MAX_WEEDS) {
            weeds++
            return true
        }
        return false
    }

    /**
     * Plants [newCrop] in this patch at time [now], in epoch milliseconds.
     */
    fun plant(newCrop: Crop, now: Long) {
        crop = newCrop
        stage = 1
        watered = false
        diseased = false
        dead = false
        harvestLives = 0
        lastUpdated = now
    }

    /**
     * @return True if the crop can be watered right now.
     */
    fun canWater(): Boolean = canBeWatered && isGrowing() && !watered

    /**
     * Waters the crop for the rest of this growth cycle.
     */
    fun water() {
        watered = true
    }

    /**
     * @return True if compost can be applied right now. The patch must be weeded and either empty or growing.
     */
    fun canApplyCompost(): Boolean = weeds == 0 && compost == CompostType.NONE && (crop == null || isGrowing())

    /**
     * Treats this patch with [type], which lasts until the patch is next emptied.
     */
    fun applyCompost(type: CompostType) {
        compost = type
    }

    /**
     * Cures the diseased crop, letting it grow again.
     */
    fun cure() {
        diseased = false
    }

    /**
     * Clears away a dead crop, leaving the patch weeded and empty.
     */
    fun clear() {
        reset(false)
    }

    /**
     * Harvests one crop. If [saved], no harvest life is used up.
     * @return True if the patch was fully harvested.
     */
    fun harvest(saved: Boolean): Boolean {
        if (!saved) {
            harvestLives--
        }
        if (harvestLives <= 0) {
            onFullyHarvested()
            return true
        }
        return false
    }

    /**
     * Runs one growth cycle of the crop: a diseased crop dies, otherwise it may become diseased, otherwise it grows a
     * stage. Watering only lasts for the cycle it was done in.
     * @return True if the patch changed.
     */
    fun tick(): Boolean {
        val stages = crop?.stages ?: return false
        if (dead || stage >= stages) {
            return false
        }
        if (diseased) {
            diseased = false
            dead = true
            return true
        }
        if (canBeDiseased(stage) && !isProtected() && rand().nextInt(128) < diseaseChance()) {
            diseased = true
            watered = false
            return true
        }
        watered = false
        stage++
        if (stage >= stages) {
            harvestLives = startingLives()
        }
        return true
    }

    /**
     * Resets the patch. If weeds are included it will reset to fully grown weeds,
     * if not it will reset to no weeds or plants.
     */
    fun reset(includeWeeds: Boolean) {
        weeds = if (includeWeeds) MAX_WEEDS else 0
        crop = null
        stage = 0
        watered = false
        diseased = false
        dead = false
        compost = CompostType.NONE
        harvestLives = 0
        onReset()
    }

    /**
     * Saves the state of this patch.
     */
    fun save(): JsonObject {
        val obj = JsonObject()
        obj.addProperty("weeds", weeds)
        crop?.let { obj.addProperty("crop", it.name) }
        obj.addProperty("stage", stage)
        obj.addProperty("watered", watered)
        obj.addProperty("diseased", diseased)
        obj.addProperty("dead", dead)
        obj.addProperty("compost", compost.name)
        obj.addProperty("harvest_lives", harvestLives)
        obj.addProperty("last_updated", lastUpdated)
        saveExtra(obj)
        return obj
    }

    /**
     * Loads the state of this patch, previously written by [save].
     */
    fun load(obj: JsonObject) {
        weeds = obj.get("weeds")?.asInt ?: MAX_WEEDS
        val cropName = obj.get("crop")?.asString
        crop = crops.find { it.name == cropName }
        stage = if (crop == null) 0 else obj.get("stage")?.asInt ?: 0
        watered = obj.get("watered")?.asBoolean ?: false
        diseased = obj.get("diseased")?.asBoolean ?: false
        dead = obj.get("dead")?.asBoolean ?: false
        compost = CompostType.values().find { it.name == obj.get("compost")?.asString } ?: CompostType.NONE
        harvestLives = obj.get("harvest_lives")?.asInt ?: 0
        lastUpdated = obj.get("last_updated")?.asLong ?: 0L
        loadExtra(obj)
    }
}
