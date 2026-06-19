package game.skill.farming

import api.attr.*
import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.model.def.VarpDefinition
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
        plr.sendVarp(Varp(VarpDefinition.VarpType.HERB_PATCH.id, herbState))
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
}