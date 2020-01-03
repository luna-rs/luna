package world.player.skill.woodcutting.cutTree

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.Player

/**
 * Determines which axe the player has and cuts the [treeObj] if they have one.
 */
fun cutTree(plr: Player, tree: Tree, treeObj: GameObject) {
    val currentAxe = Axe.computeAxeType(plr)
    if (currentAxe == null) {
        plr.sendMessage("You do not have an axe which you have the required level to use.")
        return
    }
    plr.submitAction(CutTreeAction(plr, currentAxe, tree, treeObj))
}

on(ObjectFirstClickEvent::class) {
    val treeStump = TreeStump.TREE_ID_MAP[id]
    if (treeStump != null) {
        cutTree(plr, treeStump.tree, gameObject)
    }
}