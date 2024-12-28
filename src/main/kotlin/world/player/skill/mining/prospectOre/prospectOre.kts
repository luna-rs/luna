package world.player.skill.mining.prospectOre

import api.attr.Attr
import api.predef.*
import api.predef.ext.*
import io.luna.game.action.ThrottledAction
import io.luna.game.model.mob.Player
import world.player.Sounds
import world.player.skill.mining.Ore

/**
 * The mining ore prospectinmg timesource.
 */
val Player.lastProspect by Attr.timeSource()

/**
 * Prospect the ore, tells the player what it is.
 */
fun prospect(plr: Player,  ore: Ore?) {
    val delay = rand(2, 4);
    plr.submitAction(object : ThrottledAction<Player>(plr, plr.lastProspect, delay + 1) {
        override fun execute() {
            plr.playSound(Sounds.PROSPECT_ORE)
            plr.sendMessage("You examine the rock for ores...");
            plr.walking.isLocked = true
            world.scheduleOnce(rand(2, 4)) {
                when (ore) { // sound 431
                    null -> plr.sendMessage("There is no ore left in the rock.")
                    else -> plr.sendMessage("This rock contains ${ore.typeName.toLowerCase()}.")
                }
                plr.walking.isLocked = false
            }
        }
    })
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object2(oreId) { prospect(plr, ore) }
}

Ore.EMPTY_ROCKS.forEach {
    object2(it) { prospect(plr, null) }
}

