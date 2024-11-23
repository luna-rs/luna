package world.player.skill.woodcutting.cutTree

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Determines which axe the player has and cuts the [treeObj] if they have one.
 */
fun cutTree(plr: Player, tree: Tree, treeObj: GameObject) {
    val currentAxe = Axe.computeAxeType(plr)
    if (currentAxe == null) {
        plr.sendMessage("You do not have an axe which you have the required level to use.")
        return
    }
    plr.submitAction(CutTreeActionItem(plr, currentAxe, tree, treeObj))
}

// Object interactions for cutting trees.
TreeStump.TREE_ID_MAP.entries.forEach { (id, stump) ->
    object1(id) { cutTree(plr, stump.tree, gameObject) }
}