package game.skill.mining.mineOre

import api.predef.*
import game.player.Sound
import game.skill.mining.Ore
import game.skill.mining.Pickaxe
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Starts the mining action.
 */
fun mineRock(plr: Player, ore: Ore, obj: GameObject) {
    val pick = Pickaxe.computePickType(plr)
    if (pick != null) {
        plr.submitAction(MineOreAction(plr, pick, ore, obj))
    } else {
        Pickaxe.sendNoPickaxeMessage(plr)
    }
}

// Add listeners for all ores.
Ore.ROCK_MAP.entries.forEach { (oreId, ore) ->
    object1(oreId) { mineRock(plr, ore, gameObject) }
}

// A rock whose ore has been mined. The emptied limestone rock has its own line.
Ore.EMPTY_ROCKS.filter { it != -1 }.forEach {
    object1(it) {
        plr.sendMessage(if (gameObject.id == 4030) "There are no minerals currently available in this rock."
                        else "There is no ore currently available in this rock.")
        plr.playSound(Sound.PROSPECT)
    }
}
