package world.player.combat

import api.controller.Controllers
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import world.player.combat.Combat.inMultiArea
import world.player.wilderness.WildernessAreaController
import world.player.wilderness.WildernessAreaController.wildernessLevel

object Combat {

    fun Mob.inMultiArea(): Boolean {
        return if(this is Npc) MultiCombatAreaController.inside(position) else
            asPlr().controllers.contains(Controllers.WILDERNESS)
    }
    fun Mob.inWilderness(): Boolean {
        return if(this is Npc) WildernessAreaController.inside(position) else
            asPlr().wildernessLevel > 0

    }
}