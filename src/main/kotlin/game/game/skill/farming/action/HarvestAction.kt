package game.skill.farming.action

import api.predef.*
import game.skill.farming.*
import game.skill.farming.patch.*
import io.luna.game.action.*
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*

/**
 * Action that harvests the produce from a farming patch. Every cycle harvests one crop and gives its harvest
 * experience, then rolls to save a harvest life. Once the lives run out the patch is fully harvested, which gives the
 * crop's planting experience.
 *
 * @author hydrozoa
 */
class HarvestAction(plr: Player, private val patchObject: GameObject) : Action<Player>(plr, ActionType.WEAK, true, 4) {

    /**
     * The patch being harvested.
     */
    private var patch: FarmingPatch? = null

    override fun onSubmit() {
        val found = Farming.findPatch(patchObject.id, mob)

        if (found == null) {
            mob.sendMessage("Unknown patch id=" + patchObject.id)
            complete()
            return
        }

        patch = found
        if (found.harvestReady()) {
            if (!hasTool(found)) {
                complete()
                return
            }
            mob.sendMessage("You begin to harvest the ${found.patchName}.")
        }
    }

    override fun run(): Boolean {
        val patch = patch ?: return true
        val crop = patch.crop

        if (crop == null || !patch.harvestReady()) {
            mob.sendMessage("This patch is not ready to be harvested.")
            return true
        }

        if (!hasTool(patch)) {
            return true
        }

        if (!mob.inventory.hasSpaceFor(crop.produce)) {
            mob.sendMessage("You don't have enough inventory space.")
            return true
        }

        mob.animation(patch.harvestAnimation)
        mob.inventory.add(crop.produce)
        mob.farming.addExperience(crop.harvestXp)

        val emptied = patch.harvest(rand(patch.chanceToSave(mob.farming.level)))
        if (emptied) {
            mob.farming.addExperience(crop.plantXp)
            mob.sendMessage("The ${patch.patchName} is now empty.")
        }
        Farming.commit(mob)
        return emptied
    }

    /**
     * Checks if the player has the tool needed to harvest [patch], telling them which one they need if not.
     */
    private fun hasTool(patch: FarmingPatch): Boolean {
        val tool = patch.harvestTool
        if (tool != null && !mob.inventory.contains(tool)) {
            mob.sendMessage("You need a ${itemName(tool).lowercase()} to harvest this patch.")
            return false
        }
        return true
    }
}
