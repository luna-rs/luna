package game.content.draynorGuardTree

import api.predef.*
import game.skill.woodcutting.cutTree.Axe

object1(DraynorGuardTree.TREE) { DraynorGuardTree.chop(plr, gameObject) }
object2(DraynorGuardTree.TREE) { DraynorGuardTree.talk(plr, gameObject) }
for (axe in Axe.entries) useItem(axe.id).onObject(DraynorGuardTree.TREE) {
    DraynorGuardTree.chop(plr, gameObject)
}
