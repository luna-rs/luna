package api.bot.webwalk.walk

import api.attr.Attr
import api.bot.Suspendable.waitFor
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Performs one bounded recovery after a trip exhausts its walking attempts. Used only on the game thread.
 *
 * @author lare96
 */
internal object WebWalkRecovery {

    /**
     * Five minutes at the normal 600 ms tick rate, shared by all recovery destinations of a bot.
     */
    private const val COOLDOWN_TICKS = 500

    /**
     * The last recovery attempt, including rejected or cancelled teleports, to prevent repeated recovery loops.
     */
    private val Bot.webWalkRecoveryDelay by Attr.timeSource()

    /**
     * Attempts a recovery teleport, preserving the original failure and trip metadata if it cannot finish.
     * The action is owned here so timeout or coroutine cancellation can stop it without touching other actions.
     */
    suspend fun recover(bot: Bot, destination: Position, radius: Int, result: WebWalkResult): WebWalkResult {
        coroutineContext.ensureActive()
        if (result.success || result.failure !in setOf(WebWalkFailure.NO_ROUTE, WebWalkFailure.EXECUTION)) {
            return result
        }
        if (bot.state != EntityState.ACTIVE || !bot.isAlive || bot.isLocked || bot.combat.inCombat() ||
            bot.actions.getAll<Action<*>>(ActionType.STRONG).isNotEmpty() ||
            bot.navigator.currentPending?.isDone == false) {
            bot.log("WebWalk: recovery skipped because the bot is busy or unavailable. ${result.message}")
            return result
        }
        if (WebWalker.isWithin(bot.position, destination, radius)) {
            return result.copy(success = true, message = "Arrived before recovery was needed.", failure = null)
        }
        if (!bot.webWalkRecoveryDelay.ready(COOLDOWN_TICKS)) {
            bot.log("WebWalk: recovery is on cooldown. ${result.message}")
            return result
        }
        bot.log("WebWalk: fallback teleport from ${bot.position} to $destination after exhausted attempts. ${result.message}")
        var landed = false
        val action = object : TeleportAction(bot, destination = destination,
                                            style = TeleportStyle.SPELLBOOK_TO_STYLE[bot.spellbook] ?: TeleportStyle.REGULAR) {
            override fun onLand() {
                landed = true
            }
        }
        try {
            bot.actions.submit(action)
            if (!action.isFinished) {
                waitFor(LegContext.ACTION_TIMEOUT) { action.isFinished }
            }
            if (action.isFinished && landed && bot.isAlive && !bot.isLocked &&
                WebWalker.isWithin(bot.position, destination, radius)) {
                return result.copy(success = true, failure = null,
                                   message = "Recovered by fallback teleport to $destination. Original failure: ${result.message}")
            }
            bot.log("WebWalk: fallback teleport did not complete at $destination. ${result.message}")
            return result
        } finally {
            if (!action.isFinished) action.interrupt()
        }
    }
}
