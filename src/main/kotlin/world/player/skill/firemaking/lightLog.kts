package world.player.skill.firemaking

import api.predef.*

for (log in Log.VALUES) {
    // Use tinderbox on log in inventory.
    useItem(Firemaking.TINDERBOX).onItem(log.id) {
        plr.submitAction(LightLogAction(plr, log, true))
    }

    // Use tinderbox on log on floor.
    useItem(Firemaking.TINDERBOX).onGroundItem(log.id) {
        plr.submitAction(LightLogAction(plr, log, false))
    }

    // Use "Light" option with log on ground.
    groundItem2(log.id) {
        plr.submitAction(LightLogAction(plr, log, false))
    }
}