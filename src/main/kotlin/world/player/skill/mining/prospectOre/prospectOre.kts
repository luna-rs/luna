package world.player.skill.mining.prospectOre

import api.attr.Attr
import api.predef.*
import io.luna.game.model.mob.Player
import world.player.skill.mining.Ore

/**
 * Prospect the ore, tells the player what it is.
 */
fun prospect(plr: Player, ore: Ore?) {
    plr.submitAction(ProspectOreAction(plr, ore))
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object2(oreId) { prospect(plr, ore) }
}

Ore.EMPTY_ROCKS.forEach {
    object2(it) { prospect(plr, null) }
}

