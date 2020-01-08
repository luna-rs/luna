package world.player.skill.firemaking

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player

fun delayTime(plr: Player, log: Log): Int {
    var baseTime = rand(LightLogAction.BASE_LIGHT_RATE / 2, LightLogAction.BASE_LIGHT_RATE)
    var levelFactor = plr.firemaking.level - log.level
    if (levelFactor >= 2) {
        levelFactor /= 2
        baseTime -= levelFactor
    }
    if (baseTime <= 1) {
        return rand(1, 3)
    }
    return rand(2, baseTime)
}

/*

-> Using tinderbox with a log on the ground
-> Right clicking log on ground and using "Light" option
 */

on(ItemOnItemEvent::class)
    .filter { matches(LightLogAction.TINDERBOX_ID) }
    .then {
        val log = lookup(Log.ID_TO_LOG)
        if (log != null) {
            plr.submitAction(LightLogAction(plr, delayTime(plr, log), log))
        }
    }