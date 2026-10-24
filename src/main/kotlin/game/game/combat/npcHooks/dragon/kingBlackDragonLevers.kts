package game.combat.npcHooks.dragon

import api.predef.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Position
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

// Lost City dragonkinginlever / dragonkingoutlever destinations. These object transports work in deep Wilderness.
mapOf(1816 to Position(2271, 4680), 1817 to Position(3067, 10254)).forEach { (id, destination) ->
    object1(id) {
        plr.submitAction(object : LockedAction(plr) {
            private var stage = 0

            override fun onLock() {
                mob.combat.target = null
                mob.animation(Animation(2140))
                mob.sendMessage("You pull the lever...")
            }

            override fun run(): Boolean {
                when (stage++) {
                    0 -> {
                        mob.animation(Animation(714))
                        mob.graphic(Graphic(308, 100, 0))
                    }
                    2 -> {
                        mob.move(destination)
                        mob.animation(Animation(715))
                        mob.sendMessage(if (id == 1816) "...And teleport into the Dragon's Lair."
                            else "...And teleport out of the Dragon's Lair.")
                    }
                    3 -> return true
                }
                return false
            }
        })
    }
}
