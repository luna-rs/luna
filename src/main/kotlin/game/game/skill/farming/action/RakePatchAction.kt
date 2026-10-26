package game.skill.farming.action

import api.predef.*
import game.skill.farming.*
import game.skill.farming.patch.*
import io.luna.game.action.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Action for raking a patch. Every cycle removes one weed, which goes into the inventory, and gives experience.
 *
 * @author hydrozoa
 */
class RakePatchAction(plr: Player, private val patchObject: GameObject) : Action<Player>(plr, ActionType.WEAK, true, 4) {

    /**
     * The patch being raked.
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
        if (found.needsRaking()) {
            mob.sendMessage("Raking the patch...")
        }
    }

    override fun run(): Boolean {
        val patch = patch ?: return true

        if (!patch.needsRaking()) {
            mob.sendMessage("There's nothing left to rake.")
            return true
        }

        if (!mob.inventory.contains(Farming.RAKE)) {
            mob.sendMessage("You need a rake to clear the weeds.")
            return true
        }

        if (!mob.inventory.hasSpaceFor(Item(Farming.WEEDS))) {
            mob.sendMessage("You don't have enough inventory space.")
            return true
        }

        mob.animation(Farming.RAKE_ANIMATION)
        mob.inventory.add(Item(Farming.WEEDS))
        mob.farming.addExperience(Farming.RAKE_XP)

        val completedRaking = patch.rake()
        Farming.commit(mob)
        return completedRaking
    }
}
