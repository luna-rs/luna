package world.player.skill.mining.mineOre

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import world.player.skill.mining.Ore
import world.player.skill.mining.Pickaxe

/**
 * Starts the mining action.
 */
fun mineRock(plr: Player, ore: Ore, obj: GameObject) {
    val pick = Pickaxe.computePickType(plr)
    if (pick != null) {
        plr.submitAction(MineOreActionItem(plr, pick, ore, obj))
    } else {
        plr.sendMessage("You need a pickaxe which you have the required level to use.")
    }
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object1(oreId) { mineRock(plr, ore, gameObject) }
}
Ore.EMPTY_ROCKS.forEach {
    object1(it) { plr.sendMessage("There is no ore left in the rock.") }
}
