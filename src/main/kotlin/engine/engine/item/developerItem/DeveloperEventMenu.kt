package engine.item.developerItem

import api.predef.world
import engine.bot.event.BotEventActivity
import engine.bot.event.BotEventManager
import engine.bot.event.PartyRoomBotEvent
import engine.bot.event.PkBarrowsEvents
import engine.bot.event.SoloBotEvents
import engine.bot.event.TeamBotEvents
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Starts and manages temporary bot events from Luna's Toy.
 *
 * @author lare96
 */
internal object DeveloperEventMenu {
    private val events by lazy { BotEventManager(world) }
    private val activities by lazy {
        (PkBarrowsEvents.activities + TeamBotEvents.activities + SoloBotEvents.activities + PartyRoomBotEvent.activity)
            .sortedBy { it.name }
    }

    fun open(player: Player) {
        DeveloperItemHandler.menu(player, activities.map { activity ->
            val label = if (events.status(activity) != null) "${activity.name} (active)" else activity.name
            label to { viewer -> select(viewer, activity) }
        })
    }

    fun onLogout(bot: Bot) {
        events.onLogout(bot)
    }

    private fun select(player: Player, activity: BotEventActivity) {
        val status = events.status(activity)
        if (status == null) {
            configure(player, activity, activity.defaultBots, activity.defaultMinutes)
            return
        }
        val message = when {
            status.starting -> "${activity.name} is preparing its roster."
            status.finishing -> "${activity.name} is finishing with ${status.bots} bots online."
            else -> "${activity.name} has ${status.minutesRemaining} minutes remaining with ${status.bots} bots online."
        }
        player.sendMessage(message)
        val choices = mutableListOf<Pair<String, (Player) -> Unit>>()
        if (!status.finishing) choices += "Stop event" to { viewer ->
            events.stop(activity)
            select(viewer, activity)
        }
        choices += "Refresh" to { viewer -> select(viewer, activity) }
        choices += "Back to events" to ::open
        DeveloperItemHandler.menu(player, choices)
    }

    private fun configure(player: Player, activity: BotEventActivity, bots: Int, minutes: Int) {
        player.sendMessage("Configure ${activity.name}.")
        DeveloperItemHandler.menu(player, listOf(
            "Start event" to { viewer ->
                try {
                    val login = events.start(activity, bots, minutes)
                    select(viewer, activity)
                    login.whenComplete { started, error ->
                        if (DeveloperItemHandler.allowed(viewer)) {
                            if (error != null) viewer.sendMessage(error.cause?.message ?: error.message ?: "Could not start the event.")
                            else if (started) viewer.sendMessage("${activity.name} has started.")
                        }
                    }
                } catch (error: RuntimeException) {
                    viewer.sendMessage(error.message ?: "Could not start the event.")
                    select(viewer, activity)
                }
            },
            "Bot count ($bots)" to { viewer ->
                DeveloperItemHandler.number(viewer, "Bot count (1 to ${activity.maxBots})") { p, count ->
                    if (count !in 1..activity.maxBots) p.sendMessage("That bot count is out of range.")
                    configure(p, activity, count.takeIf { it in 1..activity.maxBots } ?: bots, minutes)
                }
            },
            "Duration ($minutes min)" to { viewer ->
                DeveloperItemHandler.number(viewer, "Duration in minutes") { p, duration ->
                    if (duration <= 0) p.sendMessage("Choose a duration of at least one minute.")
                    configure(p, activity, bots, duration.takeIf { it > 0 } ?: minutes)
                }
            },
            "Back to events" to ::open
        ))
    }
}
