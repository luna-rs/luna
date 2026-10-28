package engine.bot.coordinator

import api.bot.action.PkClanPolicy
import api.bot.action.PkLoadoutPreparation
import api.bot.script.BotScript.ActionSafety
import api.bot.script.BotScript
import api.predef.rand
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Dispatches saved PK identities only when an affordable compact kit can be prepared from owned supplies.
 * Clan cape replenishment is projected without creating an item here. An ordinary clan outing needs another
 * assigned clanmate online; a confidence-selected solo outing may depart alone. Banking and live checks belong
 * to the script, so coordinator eligibility never authorizes an attack by itself.
 *
 * @author lare96
 */
object PkingCoordinator : BotCoordinator {
    override fun accept(bot: Bot) {
        getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /**
     * Preserves permanent purpose/styles/build and declines duplicate, unsafe, unstocked or unaffordable sessions.
     */
    internal fun getScript(bot: Bot): BotScript? {
        if (bot.state != EntityState.ACTIVE || bot.preferences.isSkiller || bot.preferences.pk == null ||
            !bot.preferences.canChooseActivity(BotActivity.PKING) || !ActionSafety.NON_COMBAT.allows(bot) ||
            bot.scriptStack.size() > 0) return null
        val solo = PkClanPolicy.chooseSolo(bot)
        val clan = PkClanPolicy(bot)
        if (clan.clanOuting && !solo && clan.onlineMembers(activePkOnly = false).isEmpty()) return null
        val preparation = PkLoadoutPreparation(bot, bot.actionHandler)
        if (preparation.plan(allowMissingClanCape = true) == null) return ScavengingCoordinator.detour(bot, preparation)
        val duration = if (bot.preferences.likesActivity(BotActivity.PKING)) rand(45, 120).minutes else rand(15, 45).minutes
        return PkBotScript(bot, duration, solo)
    }
}
