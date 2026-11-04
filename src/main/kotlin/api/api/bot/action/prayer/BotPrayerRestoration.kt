package api.bot.action.prayer

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.script.DynamicBotScript
import api.predef.*
import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.EntityState
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Offers an ordinary altar visit when a bot has spent a useful share of its prayer points.
 *
 * Only loaded objects matching the native recharge handler are considered. Nearby altars use the existing
 * web-walker and interaction reach; a visit never creates prayer points or experience. Wilderness boundaries
 * are not crossed just to recharge. A failed visit has a ten-minute runtime cooldown before another is offered.
 * The temporary visit has no rewards or partial progress to persist across logout.
 *
 * @author lare96
 */
class BotPrayerRestoration(private val bot: Bot, private val handler: BotActionHandler) {
    companion object {
        /**
         * Last attempted visit, including failed approaches, shared across temporary activity selections.
         */
        private var Bot.lastAltarVisit by Attr.long()

        /**
         * Mirrors restorePrayer.kts, including the separate Ancient altar and Yanille trapped altar exclusions.
         */
        internal fun isRechargeAltar(altar: GameObject): Boolean = altar.id != 6552 && altar.id != 412 &&
            altar.def().name?.contains("altar", ignoreCase = true) == true && "Pray-at" in altar.def().actions

        /**
         * Static altar placements are public map knowledge; live occupancy does not influence selection.
         */
        private val altars by lazy { world.objects.filter(::isRechargeAltar) }
    }

    /**
     * Selects a bounded support visit within the surrounding map area, preserving other general activities otherwise.
     */
    fun preparation(): BotScript? {
        if (!needsPrayer() || !ActionSafety.NON_COMBAT.allows(bot) || bot.overlays.hasWindow() ||
            System.currentTimeMillis() - bot.lastAltarVisit < 10 * 60_000L) return null
        val wilderness = levelAt(bot.position) > 0
        val nearby = altars.filter { it.state == EntityState.ACTIVE &&
            it.position.isWithinDistance(bot.position, 128) && (levelAt(it.position) > 0) == wilderness }
            .sortedBy { it.position.computeLongestDistance(bot.position) }.take(3)
        val altar = if (bot.personality.isDextrous || bot.personality.isIntelligent) nearby.firstOrNull()
            else nearby.randomOrNull()
        if (altar == null) return null
        return object : DynamicBotScript(bot) {
            override suspend fun run(): Boolean {
                if (needsPrayer()) restoreAt(altar)
                return true
            }
        }
    }

    /**
     * A small loss waits for another activity; low prayer justifies a nearby native recharge.
     */
    private fun needsPrayer(): Boolean = bot.prayer.staticLevel - bot.prayer.level >=
        maxOf(1, bot.prayer.staticLevel / 4)

    /**
     * Rechecks safety after navigation and confirms the native prayer change before considering the visit successful.
     */
    private suspend fun restoreAt(altar: GameObject): Boolean {
        bot.lastAltarVisit = System.currentTimeMillis()
        return withTimeoutOrNull(90.seconds) {
            if (!handler.webWalkTo(altar.position, radius = 2, fallback = false)) return@withTimeoutOrNull false
            repeat(3) {
                if (!ActionSafety.NON_COMBAT.allows(bot) || !isRechargeAltar(altar) ||
                    altar.state != EntityState.ACTIVE || bot.overlays.hasWindow()) return@withTimeoutOrNull false
                if (bot.prayer.level >= bot.prayer.staticLevel) return@withTimeoutOrNull true
                if (handler.interactions.interact(1, altar) { ActionSafety.NON_COMBAT.allows(bot) } &&
                    waitFor(3.seconds) { bot.prayer.level >= bot.prayer.staticLevel }) return@withTimeoutOrNull true
            }
            false
        } == true
    }
}
