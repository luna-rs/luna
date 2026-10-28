package game.content.randomEvents

import api.predef.*
import game.content.randomEvents.VisitorRandomEvent.Kind
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

Kind.entries.forEach { kind -> npc1(kind.npcId) { randomEvents.talk(plr, targetNpc) } }

cmd("random_event", RIGHTS_ADMIN) {
    val kind = when (args.firstOrNull()?.lowercase()) {
        "genie" -> Kind.GENIE
        "old_man" -> Kind.MYSTERIOUS_OLD_MAN
        else -> null
    }
    if (kind == null) {
        plr.sendMessage("Usage: ::random_event genie|old_man")
    } else if (!randomEvents.startVisitor(plr, kind)) {
        plr.sendMessage("A random event cannot start here right now.")
    }
}
