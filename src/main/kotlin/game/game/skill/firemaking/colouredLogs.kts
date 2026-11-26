package game.skill.firemaking

import api.predef.*
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

fun light(plr: Player, log: ColouredLog, groundLog: GroundItem? = null) {
    plr.submitAction(LightLogAction(plr, Log.NORMAL, groundLog == null, log.id, ColouredLog.EXP, log.fireId, groundLog))
}

for (log in ColouredLog.entries) {
    // Use a firelighter on normal logs.
    useItem(log.firelighter).onItem(Log.NORMAL.id) {
        if (plr.inventory.contains(log.firelighter) && plr.inventory.remove(Log.NORMAL.id)) {
            plr.inventory.remove(log.firelighter)
            plr.inventory.add(log.id)
        }
    }

    // Use tinderbox on log in inventory.
    useItem(Firemaking.TINDERBOX).onItem(log.id) { light(plr, log) }

    // Use tinderbox on log on floor.
    useItem(Firemaking.TINDERBOX).onGroundItem(log.id, InteractionPolicy.EQUAL_POSITION_BIF) {
        light(plr, log, groundItem)
    }

    // Use "Light" option with log on ground.
    groundItem2(log.id) { light(plr, log, groundItem) }
}
