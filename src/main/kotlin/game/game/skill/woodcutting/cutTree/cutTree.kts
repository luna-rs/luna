package game.skill.woodcutting.cutTree

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Determines which axe the player has and cuts the [treeObj] if they have one.
 */
fun cutTree(plr: Player, tree: Tree, treeObj: GameObject) {
    val currentAxe = Axe.computeAxeType(plr)
    if (currentAxe == null) {
        Axe.sendNoAxeMessages(plr)
        return
    }
    plr.submitAction(CutTreeAction(plr, currentAxe, tree, treeObj))
}

// Object interactions for cutting trees.
TreeStump.TREE_ID_MAP.entries.forEach { (id, stump) ->
    object1(id) { cutTree(plr, stump.tree, gameObject) }
}