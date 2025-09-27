package world.player.combat

import io.luna.game.model.mob.Mob
import world.player.wilderness.WildernessAreaController

object Combat {

    fun Mob.inMultiArea(): Boolean {
        return MultiCombatAreaController.inside(position)
    }
    fun Mob.inWilderness(): Boolean {
        return WildernessAreaController.inside(position)
    }
    fun Mob.inCombatArea(): Boolean {
        return inWilderness()
    }
}