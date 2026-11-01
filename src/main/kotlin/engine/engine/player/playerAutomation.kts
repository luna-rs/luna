package engine.player

import api.bot.script.DynamicBotScript
import api.bot.webwalk.walk.WebWalker
import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

cmd("autostop", RIGHTS_DEV) {
    val controller = plr as? Bot
    if (controller?.isConnectedPlayer == true) {
        controller.stopAutomation()
        plr.sendMessage("Automation stopped.")
    }
}

cmd("autowalk", RIGHTS_DEV) {
    val controller = plr as? Bot
    if (controller?.isConnectedPlayer != true) {
        plr.sendMessage("Reconnect to initialize developer script capability.")
        return@cmd
    }
    val x = args.getOrNull(0)?.toIntOrNull()
    val y = args.getOrNull(1)?.toIntOrNull()
    val z = args.getOrNull(2)?.toIntOrNull() ?: plr.position.z
    if (x == null || y == null || x !in 0..16383 || y !in 0..16383 || z !in 0..3 || args.size !in 2..3 ||
        (args.size == 3 && args[2].toIntOrNull() == null)) {
        plr.sendMessage("Usage: ::autowalk <x> <y> [plane]")
        return@cmd
    }
    val destination = Position(x, y, z)
    controller.startAutomation(object : DynamicBotScript(controller) {
        override suspend fun run(): Boolean {
            val result = WebWalker.webWalk(controller, destination)
            controller.sendMessage("Auto-walk: $result")
            return true
        }
    })
    plr.sendMessage("Walking to $destination. Click to take control, or use ::autostop.")
}
