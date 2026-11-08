package game.bot.scripts.combat

import api.attr.Attr
import api.bot.isRealBot
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import engine.spawn.PersistentGroundItem
import game.obj.resource.ZamorakWine
import game.obj.resource.ZamorakWineSources
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Lets an inexperienced dumb bot try a nearby wine once during ordinary NPC combat work.
 * The native pickup applies the penalty and records the permanent lesson; navigation never teaches it.
 * Global resource spawns stay separate from the ordinary ground-loot feed.
 *
 * @author lare96
 */
object WineTheftLesson {

    /**
     * Runtime ownership survives script cancellation until its bounded retry deadline. A late duplicate packet
     * is refused by the native learned guard; only a real native theft penalty persists learning.
     *
     * @author lare96
     */
    private class Attempt(val expiresAt: Long, var submitted: Boolean = false)

    private var Bot.wineLessonAttempt by Attr.nullableObj(Attempt::class)
    private var Bot.wineLessonRetryTick by Attr.long()

    /**
     * Attempts a single ordinary pickup within eight seconds, then returns control to combat or escape.
     * Failed approaches may retry later. Pending clicks are reserved until the retry deadline, and a late duplicate
     * cannot apply another penalty after the native callback records learning.
     * The caller retains its live activity, banking and opponent checks throughout the approach.
     */
    suspend fun attempt(bot: Bot, allowed: () -> Boolean): Boolean {
        fun eligible() = allowed() && bot.isRealBot() && bot.personality.isDumb && !ZamorakWine.learnedTheft(bot) &&
            bot.state == EntityState.ACTIVE && ActionSafety.COMBAT.allows(bot) &&
            bot.combat.target !is Player && (!bot.combat.inCombat() || bot.combat.lastCombatWith !is Player)

        if (!eligible()) return false
        val now = bot.world.currentTick
        if (bot.wineLessonAttempt?.expiresAt?.let { now >= it } == true) bot.wineLessonAttempt = null
        if (bot.wineLessonAttempt != null || now < bot.wineLessonRetryTick) return false
        val zones = ZamorakWineSources.zones.filter { bot.position in it.area }
        if (zones.isEmpty()) return false
        val wine = bot.world.locator.findItems(bot, Position.VIEWING_DISTANCE, true) {
            it.id == 245 && it is PersistentGroundItem && it.state == EntityState.ACTIVE &&
                zones.any { zone -> it.position in zone.area } && it.isVisibleTo(bot) &&
                bot.inventory.hasSpaceFor(it.toItem())
        }.firstOrNull() ?: return false

        val attempt = Attempt(now + 100)
        bot.wineLessonAttempt = attempt
        bot.wineLessonRetryTick = attempt.expiresAt
        fun permitted() = eligible() && bot.wineLessonAttempt === attempt &&
            wine.state == EntityState.ACTIVE && wine.isVisibleTo(bot) &&
            zones.any { bot.position in it.area } && bot.inventory.hasSpaceFor(wine.toItem())

        try {
            withTimeoutOrNull(8.seconds) {
                bot.actionHandler.interactions.interactBeforeSubmit(1, wine, ::permitted) {
                    if (!permitted()) false else {
                        attempt.submitted = true
                        true
                    }
                }
                if (attempt.submitted) waitFor(3.seconds) {
                    ZamorakWine.learnedTheft(bot) || wine.state != EntityState.ACTIVE || !eligible()
                }
            }
        } finally {
            if (!attempt.submitted && bot.wineLessonAttempt === attempt) bot.wineLessonAttempt = null
        }
        return true
    }
}
