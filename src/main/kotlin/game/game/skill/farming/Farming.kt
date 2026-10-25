package game.skill.farming

import api.attr.*
import com.google.gson.JsonObject
import game.skill.farming.patch.*
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
     * The varp changed when using compost on a compost bin.
     */
    const val COMPOST_BIN_VARP = 511

    /**
     * The varp changed when using supercompost on a compost bin.
     */
    const val SUPERCOMPOST_BIN_VARP = 512

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
            TreePatchLocation.values()

    /**
     * The location of every farming patch, keyed by object id.
     */
    private val locationsByObject = LOCATIONS.associateBy(PatchLocation::objectId)

    /**
     * The farming patches loaded for a player's session, and the varp values last sent to them.
     */
    class LoadedPatches(val patches: Map<PatchLocation, FarmingPatch>) {

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
     * An attribute holding the saved state of every farming patch, one object per patch keyed by
     * [PatchLocation.key]. See [FarmingPatch.save].
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
            loaded = LoadedPatches(patches)
            plr.loadedPatches = loaded
        }
        return loaded
    }

    /**
     * Returns all of the player's farming patches, keyed by location.
     */
    fun patches(plr: Player): Map<PatchLocation, FarmingPatch> = loaded(plr).patches

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
     */
    fun update(plr: Player) {
        val now = System.currentTimeMillis()
        patches(plr).values.forEach { GrowthEngine.advance(it, now) }
        commit(plr)
    }

    /**
     * Saves the player's patches and sends any varps that changed. Call this after changing a patch.
     */
    fun commit(plr: Player) {
        save(plr)
        sendPatches(plr)
    }

    /**
     * Saves the state of the player's patches to [farmingPatchData].
     */
    fun save(plr: Player) {
        val data = JsonObject()
        patches(plr).forEach { (location, patch) -> data.add(location.key, patch.save()) }
        plr.farmingPatchData = data
    }

    /**
     * Combines the states of the player's patches per varp and sends them, either all of them if [force] is true, or
     * only the ones that changed since they were last sent.
     */
    fun sendPatches(plr: Player, force: Boolean = false) {
        val loaded = loaded(plr)
        val values = HashMap<Int, Int>()
        loaded.patches.values.forEach { patch ->
            values.merge(patch.location.varpId, patch.getVarpValue()) { old, new -> old or new }
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
