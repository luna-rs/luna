package game.player.command

import api.predef.RIGHTS_DEV
import api.predef.cmd
import api.predef.on
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.RegionLoadedEvent

cmd("invis", RIGHTS_DEV) {
    val hidden = when {
        args.isEmpty() -> !plr.isModelHidden
        args.size == 1 && args[0].equals("on", true) -> true
        args.size == 1 && args[0].equals("off", true) -> false
        else -> {
            plr.sendMessage("Usage: ::invis [on|off]")
            return@cmd
        }
    }
    plr.isModelHidden = hidden
    plr.sendMessage("Your character model is now ${if (hidden) "invisible" else "visible"}.")
}

cmd("visible", RIGHTS_DEV) {
    if (args.isNotEmpty()) {
        plr.sendMessage("Usage: ::visible")
        return@cmd
    }
    plr.isModelHidden = false
    plr.sendMessage("Your character model is now visible.")
}

cmd("bottour", RIGHTS_DEV) {
    when (args.firstOrNull()?.lowercase()) {
        "stop", "off" -> {
            if (args.size != 1) {
                plr.sendMessage("Usage: ::bottour stop")
            } else if (!BotTrailerTour.stop(plr)) {
                plr.sendMessage("You are not filming a bot tour.")
            }
        }
        "next" -> {
            if (args.size != 1) {
                plr.sendMessage("Usage: ::bottour next")
            } else if (!BotTrailerTour.next(plr)) {
                plr.sendMessage("You are not filming a bot tour.")
            }
        }
        else -> {
            val durationArgs = if (args.firstOrNull().equals("start", true)) args.drop(1) else args.toList()
            val seconds = if (durationArgs.isEmpty()) 12 else durationArgs.singleOrNull()?.toIntOrNull()
            if (seconds == null || seconds !in 6..60) {
                plr.sendMessage("Usage: ::bottour [seconds 6-60], ::bottour next, or ::bottour stop")
            } else {
                BotTrailerTour.start(plr, seconds)
            }
        }
    }
}

on(RegionLoadedEvent::class) {
    BotTrailerTour.regionLoaded(plr)
}

on(LogoutEvent::class) {
    BotTrailerTour.logout(plr)
}
