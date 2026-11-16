package engine.bot.coordinator

import api.bot.action.PkLoadoutPreparation
import api.bot.action.BotCombatActionHandler.Companion.isPkParticipant
import api.bot.isRealBot
import api.bot.script.BotScript.ActionSafety
import api.predef.rand
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.WildernessScavengeBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Dispatches supply-starved PK detours with at most one scavenger for every ten active Wilderness PKers.
 * Preparing and restored scavengers share the same slots. Detours retain the saved PK identity and are
 * rate limited before dispatch, even when travel or banking subsequently fails.
 *
 * @author lare96
 */
object ScavengingCoordinator : BotCoordinator {
    private var populationWorld: World? = null
    private var populationTick = Long.MIN_VALUE
    private var limit = 0
    private val admitted = arrayListOf<Bot>()

    /**
     * Samples the world once per game tick. A PK personality elsewhere in the world is not active PKing.
     * Admission adds to this tick's shared list immediately, before the next bot can request a slot.
     */
    private fun refresh(bot: Bot) {
        val world = bot.world
        if (populationWorld === world && populationTick == world.currentTick) return
        populationWorld = world
        populationTick = world.currentTick
        admitted.clear()
        var pking = 0
        for (player in world.players) {
            if (player.state != EntityState.ACTIVE || !player.isAlive) continue
            if (player.isRealBot()) {
                val scavenging = player.scriptStack.scripts.any {
                    it is WildernessScavengeBotScript && !it.isFinished() && !it.isTerminated()
                }
                if (scavenging) {
                    admitted += player
                    continue
                }
                if (!player.scriptStack.containsActive(PkBotScript::class.java)) continue
            }
            if (player.inWilderness() && isPkParticipant(player)) pking++
        }
        limit = pking / 10
    }

    /**
     * Reserves a shared slot, or revalidates an existing visit when the active PK population falls.
     * Sessions above the current limit leave through their normal escape and banking path.
     */
    fun permits(bot: Bot): Boolean {
        refresh(bot)
        val index = admitted.indexOf(bot)
        if (index >= 0) return index < limit
        if (admitted.size >= limit) return false
        admitted += bot
        return true
    }

    fun canDispatch(bot: Bot): Boolean {
        if (!bot.preferences.scavenging.isIntroductory || bot.scriptStack.size() != 0) return false
        refresh(bot)
        return admitted.size < limit
    }

    override fun accept(bot: Bot) {
        if (canDispatch(bot) && permits(bot)) {
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
        if (!permits(bot)) return null
        return WildernessScavengeBotScript(bot, introductory = false, duration = rand(15, 45).minutes)
    }
}
