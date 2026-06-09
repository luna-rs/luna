package game.bot.scripts

import api.bot.Suspendable.naturalDelay
import api.bot.script.ReflexBotScript
import api.bot.zone.SubZone
import api.bot.zone.Zone
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await

/**
 * A [ReflexBotScript] that sends a bot back toward the location where it died.
 *
 * This script records the bot's death position, zone, and subzone when the bot reaches zero health. It then waits for
 * the normal death process to finish before attempting to route the bot back to the general death area.
 *
 * The goal is not to directly loot items. This script only performs the recovery travel step. Once the bot is near the
 * death location again, the [LootItemReflexScript] can take over.
 *
 * @param bot The bot running this death-recovery reflex.
 */
class DeathReflexScript(bot: Bot) : ReflexBotScript(bot) {

    /**
     * The location where the bot died.
     *
     * The stored values include the exact position, the active zone, and the active subzone at the time death was
     * detected. The zone/subzone are used first because they may contain custom travel logic for caves, dungeons,
     * ladders, gates, or other special transitions.
     */
    private var deathLocation: Triple<Position, Zone?, SubZone?>? = null

    /**
     * When the bot reaches zero health, this captures the current position and zone context before the death process
     * moves or respawns the bot.
     *
     * @return `true` if the bot is currently dying and the recovery location was captured.
     */
    override fun shouldReact(): Boolean {
        if (bot.health < 1) {
            deathLocation = Triple(bot.position, bot.zone, bot.subZone)
            return true
        }
        return false
    }

    /**
     * Runs the death-recovery behaviour.
     *
     * The script first waits until the bot has finished dying and respawning. It then travels back through the captured
     * subzone or zone when available, followed by a direct best-effort navigation to the original death position.
     *
     * @return `true` once the recovery travel attempt has completed, or if no valid death location was available.
     */
    override suspend fun run(): Boolean {
        // Wait until death process has finished.
        if (bot.health < 1) {
            bot.log("Waiting for death process to finish.")
            bot.naturalDelay()
            return false
        }

        // A null death location indicates an issue with the script or activation flow.
        if (deathLocation == null) {
            bot.log("No valid death location could be found.")
            return true
        }

        // Try our best to navigate back to where we died.
        val (position, zone, subZone) = deathLocation!!
        if (subZone != null) {
            bot.log("Starting movement back to death location in $subZone.")
            handler.travelTo(subZone)
        } else if (zone != null) {
            bot.log("Starting movement back to death location in $zone.")
            handler.travelTo(zone)
        }

        bot.log("Finalizing move to death location at $position.")
        bot.navigator.navigate(position, true).await()
        bot.log("Best attempt to move to the death location completed. The script will now exit.")

        // End the script afterward. Loot recovery logic can take over once the bot is nearby.
        deathLocation = null
        return true
    }
}