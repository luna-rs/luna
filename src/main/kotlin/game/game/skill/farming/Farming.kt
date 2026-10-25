package game.skill.farming

import api.attr.*
import com.google.gson.JsonObject
import game.skill.farming.patch.*
import game.skill.farming.seed.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.varp.*

/**
 * Singleton that describes player attributes for farming and utility functions.
 *
 * @author lare96
 * @author hydrozoa
 */
object Farming {

    /**
     * The varp holding the state of the Falador and Catherby allotment patches.
     */
    const val ALLOTMENT_VARP_1 = 504

    /**
     * The varp holding the state of the Ardougne and Port Phasmatys allotment patches.
     */
    const val ALLOTMENT_VARP_2 = 505

    /**
     * The varp holding the state of all herb patches.
     */
    const val HERB_PATCH_VARP = 515

    /**
     * The varp holding the state of the Taverley, Falador, Varrock and Lumbridge tree patches.
     */
    const val TREE_PATCH_VARP = 502

    /**
     * The varp holding the state of the Tree Gnome Stronghold, Tree Gnome Village, Brimhaven and Catherby fruit tree
     * patches.
     */
    const val FRUIT_TREE_PATCH_VARP = 503

    /**
     * The varp holding the state of the Yanille, Entrana, Lumbridge and McGrubor's Wood hops patches.
     */
    const val HOPS_PATCH_VARP = 506

    /**
     * The varp holding the state of the Falador, Catherby, Ardougne and Port Phasmatys flower patches.
     */
    const val FLOWER_PATCH_VARP = 508

    /**
     * The varp holding the state of the Champions' Guild, Rimmington, Etceteria and south Ardougne bush patches.
     */
    const val BUSH_PATCH_VARP = 509

    /**
     * The varp holding the state of the Falador, Catherby, Port Phasmatys and Ardougne compost bins. See [CompostBin].
     */
    const val COMPOST_BIN_VARP = 511

    /**
     * The varp holding the state of the cactus, mushroom and belladonna patches, in six bits each.
     */
    const val SPECIAL_PATCH_VARP = 512

    /**
     * The varp holding the tools stored with tool leprechauns. See [StorableTool].
     */
    const val TOOLS_VARP = 615

    /**
     * The rake item id.
     */
    const val RAKE = 5341

    /**
     * The seed dibber item id.
     */
    const val SEED_DIBBER = 5343

    /**
     * The spade item id.
     */
    const val SPADE = 952

    /**
     * The scarecrow item id, placed in a flower patch.
     */
    const val SCARECROW = 6059

    /**
     * The secateurs item id.
     */
    const val SECATEURS = 5329

    /**
     * The magic secateurs item id.
     */
    const val MAGIC_SECATEURS = 7409

    /**
     * The empty plant pot item id, left over after planting a sapling.
     */
    const val PLANT_POT = 5350

    /**
     * The plant pot filled with soil item id, made by using an empty plant pot on a weeded farming patch.
     */
    const val FILLED_PLANT_POT = 5354

    /**
     * The plant pots filled with soil that seeds can be planted in. The cache has a second filled plant pot, which is
     * never made but is accepted too.
     */
    val FILLED_PLANT_POTS = listOf(FILLED_PLANT_POT, 5356)

    /**
     * The gardening trowel item id.
     */
    const val GARDENING_TROWEL = 5325

    /**
     * The empty watering can item id.
     */
    const val EMPTY_WATERING_CAN = 5331

    /**
     * The watering can item ids, from 1 dose of water to 8.
     */
    val WATERING_CANS = listOf(5333, 5334, 5335, 5336, 5337, 5338, 5339, 5340)

    /**
     * The plant cure item id.
     */
    const val PLANT_CURE = 6036

    /**
     * The vial item id, left over after using a plant cure.
     */
    const val VIAL = 229

    /**
     * The bucket item id, left over after using compost.
     */
    const val BUCKET = 1925

    /**
     * The weeds item id, given when raking.
     */
    const val WEEDS = 6055

    /**
     * The raking animation.
     */
    val RAKE_ANIMATION = Animation(2273)

    /**
     * The seed dibbing animation, played when planting.
     */
    val SEED_DIBBING_ANIMATION = Animation(2291)

    /**
     * The watering animation.
     */
    val WATERING_ANIMATION = Animation(2293)

    /**
     * The plant pot filling animation, played when filling a plant pot with soil.
     */
    val FILL_PLANT_POT_ANIMATION = Animation(2287)

    /**
     * The trowel digging animation, played when planting a seed in a plant pot.
     */
    val TROWEL_ANIMATION = Animation(2272)

    /**
     * The plant cure animation.
     */
    val PLANT_CURE_ANIMATION = Animation(2288)

    /**
     * The pouring animation, played when applying compost.
     */
    val COMPOST_ANIMATION = Animation(2283)

    /**
     * The pruning animation, played when pruning a diseased tree.
     */
    val PRUNE_ANIMATION = Animation(2274)

    /**
     * The spade digging animation, played when harvesting allotments and clearing dead crops.
     */
    val DIG_ANIMATION = Animation(830)

    /**
     * The low picking animation, played when harvesting herbs.
     */
    val PICK_ANIMATION = Animation(2282)

    /**
     * The middle picking animation, played when picking fruit from fruit trees.
     */
    val PICK_FRUIT_ANIMATION = Animation(2281)

    /**
     * The experience given for each weed raked. An unchecked placeholder, it is not from the wiki.
     */
    const val RAKE_XP = 4.0

    /**
     * The experience given for watering a crop. An unchecked placeholder, it is not from the wiki.
     */
    const val WATER_XP = 0.0

    /**
     * The experience given for curing a diseased crop. An unchecked placeholder, it is not from the wiki.
     */
    const val CURE_XP = 0.0

    /**
     * The location of every farming patch.
     */
    val LOCATIONS: List<PatchLocation> = AllotmentPatchLocation.values().toList() + HerbPatchLocation.values() +
            TreePatchLocation.values() + FruitTreePatchLocation.values() + HopsPatchLocation.values() +
            FlowerPatchLocation.values() + SpecialPatchLocation.values() + BushPatchLocation.values()

    /**
     * The location of every farming patch, keyed by object id.
     */
    private val locationsByObject = LOCATIONS.associateBy(PatchLocation::objectId)

    /**
     * The farming patches and compost bins loaded for a player's session, and the varp values last sent to them.
     */
    class LoadedPatches(val patches: Map<PatchLocation, FarmingPatch>, val bins: Map<CompostBinLocation, CompostBin>) {

        /**
         * The varp values last sent, keyed by varp id.
         */
        val sentVarps = HashMap<Int, Int>()
    }

    /**
     * An attribute representing the tools stored with tool leprechauns, packed the same way as the `farming_tools`
     * varp. See [StorableTool].
     */
    var Player.storedTools by Attr.int().persist("farming_tools")

    /**
     * An attribute holding the time the player's watered seedlings were last brought up to date, in epoch
     * milliseconds. See [growSeedlings].
     */
    var Player.seedlingsUpdated by Attr.long().persist("farming_seedlings_updated")

    /**
     * An attribute holding the saved state of every farming patch and compost bin, one object each keyed by
     * [PatchLocation.key] or [CompostBinLocation.key]. See [FarmingPatch.save] and [CompostBin.save].
     */
    var Player.farmingPatchData by Attr.obj { JsonObject() }.persist("farming_patches")

    /**
     * An attribute holding the farming patches loaded from [farmingPatchData] for this session.
     */
    private var Player.loadedPatches by Attr.nullableObj(LoadedPatches::class)

    /**
     * Returns the player's farming patches, loading them from [farmingPatchData] the first time.
     */
    private fun loaded(plr: Player): LoadedPatches {
        var loaded = plr.loadedPatches
        if (loaded == null) {
            val data = plr.farmingPatchData
            val patches = LinkedHashMap<PatchLocation, FarmingPatch>()
            for (location in LOCATIONS) {
                val patch = location.createPatch()
                val patchData = data.get(location.key)
                if (patchData != null && patchData.isJsonObject) {
                    patch.load(patchData.asJsonObject)
                }
                patches[location] = patch
            }
            patches.values.forEach { it.link(patches) }
            val bins = LinkedHashMap<CompostBinLocation, CompostBin>()
            for (location in CompostBinLocation.values()) {
                val bin = CompostBin(location)
                val binData = data.get(location.key)
                if (binData != null && binData.isJsonObject) {
                    bin.load(binData.asJsonObject)
                }
                bins[location] = bin
            }
            loaded = LoadedPatches(patches, bins)
            plr.loadedPatches = loaded
        }
        return loaded
    }

    /**
     * Returns all of the player's farming patches, keyed by location.
     */
    fun patches(plr: Player): Map<PatchLocation, FarmingPatch> = loaded(plr).patches

    /**
     * Returns all of the player's compost bins, keyed by location.
     */
    fun compostBins(plr: Player): Map<CompostBinLocation, CompostBin> = loaded(plr).bins

    /**
     * Finds a player's compost bin based on the object id, after bringing their farming state up to date.
     */
    fun findCompostBin(objectId: Int, plr: Player): CompostBin? {
        val location = CompostBinLocation.lookup(objectId) ?: return null
        update(plr)
        return compostBins(plr)[location]
    }

    /**
     * Finds a players farming patch based on the object id, after bringing their patches up to date. Useful for
     * handling interactions with patches.
     * @return FarmingPatch corresponding to the object id for a given player.
     */
    fun findPatch(objectId: Int, plr: Player): FarmingPatch? {
        val location = locationsByObject[objectId] ?: return null
        update(plr)
        return patches(plr)[location]
    }

    /**
     * Brings all of the player's patches up to date with the current time, then saves them and sends any varps that
     * changed. See [GrowthEngine].
     *
     * Flower patches are brought up to date first, so the allotments they protect use their latest state. After time
     * offline, a flower that finished growing during it may protect a few earlier allotment growth cycles. The player's
     * watered seedlings are grown too, see [growSeedlings].
     */
    fun update(plr: Player) {
        val now = System.currentTimeMillis()
        val (flowers, others) = patches(plr).values.partition { it is FlowerPatch }
        flowers.forEach { GrowthEngine.advance(it, now) }
        others.forEach { GrowthEngine.advance(it, now) }
        growSeedlings(plr, now)
        commit(plr)
    }

    /**
     * Grows every watered seedling in the player's inventory and bank into its sapling, if a multiple of
     * [GrowthEngine.WEED_CYCLE_MINUTES] has passed since they were last brought up to date at [now], in epoch
     * milliseconds. A seedling therefore grows within five minutes of being watered, on a growth tick (wiki), also
     * while the player is offline.
     */
    fun growSeedlings(plr: Player, now: Long) {
        val last = plr.seedlingsUpdated
        plr.seedlingsUpdated = now
        if (last <= 0L || GrowthEngine.boundaries(last, now, GrowthEngine.WEED_CYCLE_MINUTES) <= 0) {
            return
        }
        var grewInInventory = false
        for (seedling in TreeSeedling.values()) {
            if (plr.inventory.replaceAll(seedling.wateredSeedling, seedling.sapling) > 0) {
                grewInInventory = true
            }
            val banked = plr.bank.computeAmountForId(seedling.wateredSeedling)
            if (banked > 0) {
                plr.bank.remove(Item(seedling.wateredSeedling, banked))
                plr.bank.add(Item(seedling.sapling, banked))
            }
        }
        if (grewInInventory) {
            plr.sendMessage("Your seedling has grown into a sapling.")
        }
    }

    /**
     * Returns the watering can left after using one dose of water from [can].
     */
    fun lowerWateringCan(can: Int): Int {
        val dose = WATERING_CANS.indexOf(can)
        return if (dose == 0) EMPTY_WATERING_CAN else WATERING_CANS[dose - 1]
    }

    /**
     * Saves the player's patches and sends any varps that changed. Call this after changing a patch.
     */
    fun commit(plr: Player) {
        save(plr)
        sendPatches(plr)
    }

    /**
     * Saves the state of the player's patches and compost bins to [farmingPatchData].
     */
    fun save(plr: Player) {
        val data = JsonObject()
        patches(plr).forEach { (location, patch) -> data.add(location.key, patch.save()) }
        compostBins(plr).forEach { (location, bin) -> data.add(location.key, bin.save()) }
        plr.farmingPatchData = data
    }

    /**
     * Combines the states of the player's patches and compost bins per varp and sends them, either all of them if
     * [force] is true, or only the ones that changed since they were last sent.
     */
    fun sendPatches(plr: Player, force: Boolean = false) {
        val loaded = loaded(plr)
        val values = HashMap<Int, Int>()
        loaded.patches.values.forEach { patch ->
            values.merge(patch.location.varpId, patch.getVarpValue()) { old, new -> old or new }
        }
        loaded.bins.values.forEach { bin ->
            values.merge(bin.location.varpId, bin.getVarpValue()) { old, new -> old or new }
        }
        values.forEach { (varpId, value) ->
            if (force || loaded.sentVarps[varpId] != value) {
                plr.sendVarp(Varp(varpId, value))
                loaded.sentVarps[varpId] = value
            }
        }
    }

    /**
     * Sends the tools stored with tool leprechauns to the player.
     */
    fun sendStoredTools(plr: Player) {
        plr.sendVarp(Varp(TOOLS_VARP, plr.storedTools))
    }
}
