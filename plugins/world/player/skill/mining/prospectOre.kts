package world.player.skill.mining

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Prospect the ore, tells the player what it is.
 */
fun prospect(plr: Player, ore: Ore) {
    plr.lock()
    plr.sendMessage("You examine the rock for ores...")
    world.schedule(rand(2, 4)) {
        plr.unlock()
        plr.sendMessage("This rock contains ${ore.typeName.toLowerCase()}.")
    }
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object2(oreId) {
        prospect(plr, ore)
    }
}