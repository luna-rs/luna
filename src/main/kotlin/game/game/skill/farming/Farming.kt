package game.skill.farming

import api.attr.*
import game.skill.farming.patch.*
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.*
import io.luna.game.model.mob.varp.*

/**
 * Singleton that describes player attributes for farming and utility functions.
 *
 * @author lare96
 * @author hydrozoa
 */
object Farming {

    /**
     * An attribute representing all herb patches.
     * todo find a way to persist this where it is loaded as correct types
     */
    val Player.herbPatches by Attr.map<HerbPatchLocation, HerbPatch> {
        val map = HashMap<HerbPatchLocation, HerbPatch>()
        HerbPatchLocation.values().forEach { location ->
            map[location] = HerbPatch(location)
        }
        map
    }

    /**
     * An attribute representing an allotment patch.
     * todo fix by making it a map like herb patches maybe?
     */
    val Player.allotmentPatch by Attr.obj<AllotmentPatch>{ AllotmentPatch(false) }.persist("allotment-patch")

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
}