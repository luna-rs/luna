package game.skill.farming.tree

import api.predef.*
import game.player.Sound
import game.skill.farming.*
import game.skill.farming.action.*
import game.skill.farming.fruitTree.*
import game.skill.farming.patch.*
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Handles the first option of the fully grown tree in the tree or fruit tree patch [objectId]: checking its health,
 * which gives the tree's [Crop.checkHealthXp], then picking its fruit while it has any, then chopping it down.
 */
fun treeOption(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) as? AbstractTreePatch ?: return
    val crop = patch.crop ?: return
    when {
        patch.canCheckHealth() -> {
            patch.checkHealth()
            plr.farming.addExperience(crop.checkHealthXp)
            plr.sendMessage("You examine the tree for signs of disease and find that it is in perfect health.")
            Farming.commit(plr)
        }

        patch is FruitTreePatch && patch.canPick() -> plr.submitAction(PickFruitAction(plr, patch))
        patch.canChop() -> {
            val axe = Axe.computeAxeType(plr)
            if (axe == null) {
                Axe.sendNoAxeMessages(plr)
                return
            }
            when (patch) {
                is TreePatch -> plr.submitAction(ChopPatchTreeAction(plr, axe, patch, crop as TreeSapling))
                is FruitTreePatch -> chopFruitTree(plr, axe, patch)
            }
        }
    }
}

/**
 * Chops down the fruit tree in [patch] with [axe]. It needs no Woodcutting level, gives no logs or experience, and
 * falls on the first swing, leaving a stump until it is dug up.
 */
fun chopFruitTree(plr: Player, axe: Axe, patch: FruitTreePatch) {
    plr.animation(axe.animation)
    plr.playSound(Sound.TREE_FALL)
    patch.fell()
    Farming.commit(plr)
}

/**
 * Digs up the stump in the tree or fruit tree [patch], which empties the patch. A tree stump also gives the tree's
 * roots.
 */
fun digStump(plr: Player, patch: AbstractTreePatch) {
    val roots = (patch.crop as? TreeSapling)?.roots
    when {
        patch.harvestReady() && !patch.stump -> plr.sendMessage("You need to chop this tree down first.")
        !patch.stump -> return
        roots != null && !plr.inventory.hasSpaceFor(Item(roots)) ->
            plr.sendMessage("You don't have enough inventory space.")

        else -> {
            plr.animation(Farming.DIG_ANIMATION)
            roots?.let { plr.inventory.add(Item(it)) }
            plr.sendMessage("You dig up the tree stump.")
            patch.clear()
            Farming.commit(plr)
        }
    }
}
