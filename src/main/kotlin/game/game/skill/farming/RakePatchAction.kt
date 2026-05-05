package game.skill.farming

import api.predef.ext.*
import game.player.*
import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.action.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Action for raking a patch.
 * todo add other types of patches
 *
 * @author hydrozoa
 */
class RakePatchAction(plr: Player, private val patchObject: GameObject) : Action<Player>(plr, ActionType.WEAK, true, 4) {

    override fun onSubmit() {
        var patch: FarmingPatch? = null
        if (HerbPatchLocation.values().map { it.objectId }.contains(patchObject.id)) {
            patch = mob.herbPatches[HerbPatchLocation.lookup(patchObject.id)]
        } /*else if (Farming.ALLOTMENT_PATCHES.contains(patchObject.id)) {
            patch = mob.allotmentPatch
        }*/ // todo implement allotment patches

        if (patch == null) {
            return
        }

        if (patch.needsRaking()) {
            mob.sendMessage("Raking the patch...")
        }
    }

    override fun run(): Boolean {
        var patch: FarmingPatch? = null
        if (HerbPatchLocation.values().map { it.objectId }.contains(patchObject.id)) {
            patch = mob.herbPatches[HerbPatchLocation.lookup(patchObject.id)]
        } /*else if (Farming.ALLOTMENT_PATCHES.contains(patchObject.id)) {
            patch = mob.allotmentPatch
        }*/ // todo implement allotment patches

        if (patch == null) {
            mob.sendMessage("Unknown patch")
            return true
        }

        if (!patch.needsRaking()) {
            mob.sendMessage("There's nothing left to rake.")
            return true
        }

        mob.animation(Animations.SUPERHEAT) // dummy just for visual feedback, remove once correct anim is found
        // todo send correct animation
        // todo send rake gfx

        var completedRaking = patch.rake() ?: true
        Farming.sendHerbState(mob)
        mob.inventory.add(Item.byName("Weeds"))

        return completedRaking
    }
}