package game.skill.firemaking.lightSource

import api.predef.*
import io.luna.game.event.impl.LoginEvent

LampOilStill.initialize()

useItem(LampOilStill.SWAMP_TAR).onObject(LampOilStill.STILL) { LampOilStill.addTar(plr) }

for (empty in LampOilStill.FILLED.keys) {
    useItem(empty).onObject(LampOilStill.STILL) { LampOilStill.fill(plr, usedItemIndex) }
}

on(LoginEvent::class) { LampOilStill.show(plr) }
