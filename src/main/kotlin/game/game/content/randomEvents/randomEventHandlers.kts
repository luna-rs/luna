package game.content.randomEvents

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PlayerTickEvent

// Register persistent attribute keys while loading scripts, before any player files are read.
val randomEvents = RandomEvents

on(LoginEvent::class) { randomEvents.login(plr) }
on(LogoutEvent::class) { randomEvents.logout(plr) }
on(PlayerTickEvent::class) {
    randomEvents.restore(plr)
    randomEvents.tick(plr)
}

npc1(409) { RandomEvents.talk(plr, targetNpc) }

cmd("random_event", RIGHTS_ADMIN) {
    if (args.firstOrNull()?.equals("genie", true) != true) {
        plr.sendMessage("Usage: ::random_event genie")
    } else if (!RandomEvents.startGenie(plr)) {
        plr.sendMessage("A random event cannot start here right now.")
    }
}
