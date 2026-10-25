package engine.bot.coordinator

import api.bot.action.PkLoadoutPreparation
import api.bot.script.BotScript.ActionSafety
import api.predef.rand
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.WildernessScavengeBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Dispatches temporary new-account scavengers and optional supply-starved PK detours through one runner.
 * Introductory participation is assigned once at genuine account creation. Detours retain the saved PK
 * identity and are rate limited before dispatch, even when travel or banking subsequently fails.
 *
 * @author lare96
 */
object ScavengingCoordinator : BotCoordinator {
    override fun accept(bot: Bot) {
        if (bot.preferences.scavenging.isIntroductory && bot.scriptStack.size() == 0) {
            bot.scriptStack.push(WildernessScavengeBotScript(bot, introductory = true, duration = 30.minutes))
        }
    }

    /**
     * Offers a bounded fallback only for missing owned supplies, never a safety, clan or XP-policy rejection.
     */
    internal fun detour(bot: Bot, preparation: PkLoadoutPreparation): WildernessScavengeBotScript? {
        if (bot.state != EntityState.ACTIVE || bot.inWilderness() || bot.preferences.isSkiller ||
            bot.preferences.pk == null || !bot.preferences.canChooseActivity(BotActivity.PKING) ||
            !ActionSafety.NON_COMBAT.allows(bot) || !preparation.missingSupplies()) return null
        if (!bot.preferences.scavenging.tryDetour(System.currentTimeMillis(), rand(30, 90).minutes.inWholeMilliseconds)) return null
        if (!rand(0.25 + 0.5 * bot.personality.confidence.coerceIn(0.0, 1.0))) return null
        return WildernessScavengeBotScript(bot, introductory = false, duration = rand(15, 45).minutes)
    }
}
