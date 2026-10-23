package game.content.flourMill

import api.predef.*
import game.content.flourMill.FlourMill.FLOUR_BIN
import game.content.flourMill.FlourMill.GRAIN
import game.content.flourMill.FlourMill.POT
import game.content.flourMill.FlourMill.hopperFull
import io.luna.game.event.impl.LoginEvent

for (hopper in FlourMill.HOPPERS) {
    useItem(GRAIN).onObject(hopper) { FlourMill.fillHopper(plr) }
}

for (controls in FlourMill.CONTROLS) {
    object1(controls) { FlourMill.operateControls(plr) }
}

object1(FLOUR_BIN) { FlourMill.emptyBin(plr) }
useItem(POT).onObject(FLOUR_BIN) { FlourMill.emptyBin(plr) }

// Read the saved hopper at login, so a session that never uses a mill still saves it.
on(LoginEvent::class) { plr.hopperFull }
