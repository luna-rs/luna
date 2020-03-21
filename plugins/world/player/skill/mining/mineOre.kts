package world.player.skill.mining

import api.predef.*
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.Player

/**
 * Starts the mining action.
 */
fun mineRock(plr: Player, ore: Ore, obj: GameObject) {
    val pick = Pickaxe.computePickType(plr)
    if (pick != null) {
        plr.submitAction(MineOreAction(plr, pick, ore, obj))
    } else {
        plr.sendMessage("You need a pickaxe to mine ores.")
    }
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object1(oreId) { mineRock(plr, ore, gameObject) }
}
