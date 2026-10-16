package game.content.abyss

import api.predef.*
import game.content.abyss.Abyss.abyssLayout
import game.content.abyss.Abyss.showLayout
import game.content.abyss.ObstacleAttempt.Companion.obstacleFor
import game.skill.mining.Pickaxe
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) {
    if (plr.abyssLayout != 0) {
        showLayout(plr, plr.abyssLayout)
    }
}

for (passage in AbyssPassage.entries) {
    object1(passage.id) {
        when (obstacleFor(plr, gameObject)) {
            AbyssObstacle.ROCK -> {
                val pickaxe = Pickaxe.computePickType(plr)
                if (pickaxe == null) {
                    plr.sendMessage("You need a pickaxe for which you have the required Mining level to mine " +
                                    "this rock.")
                } else {
                    plr.submitAction(MineRock(plr, passage, gameObject, pickaxe))
                }
            }
            AbyssObstacle.TENDRILS -> plr.submitAction(ChopTendrils(plr, passage, gameObject))
            AbyssObstacle.BOIL -> plr.submitAction(BurnBoil(plr, passage, gameObject))
            AbyssObstacle.EYES -> plr.submitAction(DistractEyes(plr, passage, gameObject))
            AbyssObstacle.GAP -> plr.submitAction(SqueezeThroughGap(plr, passage, gameObject))
            AbyssObstacle.PASSAGE -> {
                plr.move(passage.inner)
                plr.abyssLayout = 0
                showLayout(plr, 0)
            }
            AbyssObstacle.BLOCKAGE, null -> Unit
        }
    }
}
