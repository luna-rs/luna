package game.skill.farming

import api.attr.*
import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.model.mob.*
import io.luna.game.model.mob.varp.*

/**
 * Singleton that describes player attributes for farming and utility functions.
 * todo find a way to persist player attributes where it is loaded as correct types
 *
 * @author lare96
 * @author hydrozoa
 */
object Farming {

    /**
     * The varp holding the state of the Falador allotment patches.
     */
    const val ALLOTMENT_VARP_1 = 504

    /**
     * The varp holding the state of the Ardougne allotment patches.
     */
    const val ALLOTMENT_VARP_2 = 505

    /**
     * The varp changed when using compost on a compost bin.
     */
    const val COMPOST_BIN_VARP = 511

    /**
     * The varp changed when using supercompost on a compost bin.
     */
    const val SUPERCOMPOST_BIN_VARP = 512

    /**
     * The varp holding the state of all herb patches.
     */
    const val HERB_PATCH_VARP = 515

    /**
     * The varp holding the tools stored with tool leprechauns. See [StorableTool].
     */
    const val TOOLS_VARP = 615

    /**
     * An attribute representing the tools stored with tool leprechauns, packed the same way as the `farming_tools`
     * varp. See [StorableTool].
     */
    var Player.storedTools by Attr.int().persist("farming_tools")

    /**
     * An attribute representing all herb patches.
     */
    val Player.herbPatches by Attr.map<HerbPatchLocation, HerbPatch> {
        val map = HashMap<HerbPatchLocation, HerbPatch>()
        HerbPatchLocation.values().forEach { location ->
            map[location] = HerbPatch(location)
        }
        map
    }

    /**
     * An attribute representing all allotment patches.
     */
    val Player.allotmentPatches by Attr.map<AllotmentPatchLocation, AllotmentPatch> {
        val map = HashMap<AllotmentPatchLocation, AllotmentPatch>()
        AllotmentPatchLocation.values().forEach { location ->
            map[location] = AllotmentPatch(location)
        }
        map
    }

    /**
     * Finds a players farming patch based on the object id. Useful for handling interactions with patches.
     * @return FarmingPatch corresponding to the object id for a given player.
     */
    fun findPatch(objectId: Int, plr: Player): FarmingPatch? {
        var patch: FarmingPatch? = null
        if (HerbPatchLocation.lookup(objectId) != null) {
            patch = plr.herbPatches[HerbPatchLocation.lookup(objectId)]
        } else if (AllotmentPatchLocation.lookup(objectId) != null) {
            patch = plr.allotmentPatches[AllotmentPatchLocation.lookup(objectId)]
        }
        return patch
    }

    /**
     * Combines all herb patch states to a single int and sends it to the player.
     */
    fun sendHerbState(plr: Player) {
        var herbState: Int = 0
        plr.herbPatches.values.forEach { patch ->
            herbState += patch.getVarpValue()
        }
        plr.sendVarp(Varp(HERB_PATCH_VARP, herbState))
    }

    /**
     * Combines all allotment patch states and sends them to the player.
     */
    fun sendAllotmentState(plr: Player) {
        val allotmentStates: MutableMap<Int, Int> = mutableMapOf()
        plr.allotmentPatches.values.forEach { patch ->
            if (allotmentStates[patch.location.config] == null) {
                allotmentStates[patch.location.config] = 0
            }
            allotmentStates[patch.location.config] = allotmentStates[patch.location.config]!! + patch.getVarpValue()
        }
        allotmentStates.forEach { (configId, value) ->
            System.out.println("Sent "+configId+" = "+value);
            plr.sendVarp(Varp(configId, value))
        }
    }

    /**
     * Sends the tools stored with tool leprechauns to the player.
     */
    fun sendStoredTools(plr: Player) {
        plr.sendVarp(Varp(TOOLS_VARP, plr.storedTools))
    }
}