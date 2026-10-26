package game.skill.farming.action

import api.predef.*
import game.skill.farming.*
import game.skill.farming.patch.*
import io.luna.game.action.*
import io.luna.game.model.mob.*

/**
 * Action that picks from a [PickablePatch], such as the fruit from a fruit tree or the spines from a cactus. Every cycle
 * picks one and gives its harvest experience, until there is nothing left to pick or the inventory is full.
 *
 * @author hydrozoa
 */
class PickFruitAction<T>(plr: Player, private val patch: T) :
    Action<Player>(plr, ActionType.WEAK, true, 4) where T : FarmingPatch, T : PickablePatch {

    override fun run(): Boolean {
        val crop = patch.crop

        if (crop == null || !patch.canPick()) {
            return true
        }

        if (!mob.inventory.hasSpaceFor(crop.produce)) {
            mob.sendMessage("You don't have enough inventory space.")
            return true
        }

        mob.animation(patch.harvestAnimation)
        mob.inventory.add(crop.produce)
        mob.farming.addExperience(crop.harvestXp)
        mob.sendMessage("You pick ${articleItemName(crop.produce.id).lowercase()}.")

        patch.pick()
        Farming.commit(mob)
        return !patch.canPick()
    }
}
