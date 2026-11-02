package engine.item.developerItem

import api.bot.isRealBot
import api.bot.script.BotScript
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.bot.Bot

/**
 * Runs existing Bot scripts on the same connected developer character, through its session lifecycle.
 * Autonomous bots retain their existing script-stack controls and are not player-injection sessions.
 *
 * @author lare96
 */
object PlayerToBotInjector {
    fun controller(player: Player): Bot? {
        if (player.rights != PlayerRights.DEVELOPER) return null
        if (player.isRealBot()) {
            player.sendMessage("Use the bot's script controls for autonomous bots.")
            return null
        }
        if (player !is Bot || !player.isConnectedPlayer) {
            player.sendMessage("Reconnect to initialize developer script capability.")
            return null
        }
        return player
    }

    fun start(player: Player, factory: (Bot) -> BotScript): Boolean {
        val bot = controller(player) ?: return false
        return try {
            bot.startAutomation(factory(bot))
            player.sendMessage("Automation started. Click to take control or use ::autostop.")
            true
        } catch (error: IllegalStateException) {
            player.sendMessage(error.message ?: "Automation could not start.")
            false
        }
    }

    fun stop(player: Player) {
        val bot = controller(player) ?: return
        bot.stopAutomation()
        player.sendMessage("Automation stopped.")
    }
}
