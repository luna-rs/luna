package game.skill.farming.action

import api.predef.ext.*
import game.player.*
import game.skill.farming.*
import game.skill.farming.Farming.herbPatches
import game.skill.farming.patch.*
import io.luna.game.action.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*

/**
 * Action that harvests the produce from a farming patch.
 * @author hydrozoa
 */
class HarvestAction(plr: Player, private val patchObject: GameObject) : Action<Player>(plr, ActionType.WEAK, true, 4) {

    override fun onSubmit() {
        var patch: FarmingPatch? = Farming.findPatch(patchObject.id, mob)

        if (patch == null) {
            mob.sendMessage("Unknown patch id="+patchObject.id)
            complete()
            return
        }

        if (patch.harvestReady() && patch.produceAvailable > 0) {
            mob.sendMessage("You begin harvesting...")
        }
    }

    override fun run(): Boolean {
        var patch: FarmingPatch? = Farming.findPatch(patchObject.id, mob)

        if (patch == null) {
            mob.sendMessage("Unknown patch id="+patchObject.id)
            return true
        }

        if (!patch.harvestReady()) {
            mob.sendMessage("Not ready for harvest")
            return true
        }

        if (patch.produceAvailable <= 0) {
            mob.sendMessage("Nothing to harvest")
            return true
        }

        // todo change to correct animation
        mob.animation(Animations.SUPERHEAT) // dummy just for visual feedback, remove once correct anim is found

        var harvestResult = patch.harvest(mob)
        if (harvestResult) {
            Farming.sendHerbState(mob)
            return true
        }
        return false
    }
}