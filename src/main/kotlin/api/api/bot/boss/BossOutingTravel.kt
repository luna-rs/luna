package api.bot.boss

import api.bot.Suspendable.naturalMicroDelay
import api.bot.script.BotScript
import api.bot.webwalk.plan.BankAccess
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CompletableFuture
import io.luna.game.model.mob.movement.NavigationResult

/**
 * Meets after native preparation, follows predecessors over ordinary ground, and performs each native crossing.
 * Navigation ownership is cancelled only through this coroutine or its own continuous follow request.
 *
 * @author lare96
 */
internal object BossOutingTravel {
    /**
     * A prepared member waits for the host's real bot quorum; reaching the encounter ends its membership.
     */
    suspend fun travel(bot: Bot, token: BossOutings.Member, permitted: () -> Boolean,
                       nativeTravel: suspend () -> Boolean): Boolean = coroutineScope {
        val target = token.target
        var following: CompletableFuture<NavigationResult>? = null
        fun stopFollowing() {
            following?.let {
                if (bot.navigator.currentPending === it) {
                    bot.navigator.cancel()
                    bot.walking.clear()
                }
            }
            following = null
        }
        try {
            if (token.phase == BossOutings.Phase.RECRUITING && !token.session.bank.area.contains(bot.position) &&
                !bot.actionHandler.webWalkTo(token.session.bank, radius = 4,
                    bank = BankAccess.NONE, fallback = false)) return@coroutineScope false
            if (BossOutings.member(bot) !== token || !permitted() || !BossOutings.markPrepared(bot))
                return@coroutineScope false
            while (BossOutings.member(bot) === token && permitted()) {
                if (!BotScript.ActionSafety.NON_COMBAT.allows(bot)) {
                    bot.naturalMicroDelay()
                    continue
                }
                if (token.phase == BossOutings.Phase.RECRUITING) {
                    if (bot === token.session.host) BossOutingSpeechPool.advertise(bot, target,
                        (token.session.requiredCompanions + 1 - token.session.members.size).coerceAtLeast(0))
                    BossOutings.markPrepared(bot)
                    bot.naturalMicroDelay()
                    continue
                }
                if (target.zone.area.contains(bot.position)) {
                    stopFollowing()
                    BossOutings.arrived(bot)
                    return@coroutineScope true
                }
                val previous = BossOutings.predecessor(bot)
                val crossing = previous != null && (target.zone.area.contains(previous.position) ||
                    previous.position.z != bot.position.z ||
                    previous.position.computeLongestDistance(bot.position) > BossOutings.FORMATION_DISTANCE)
                if (previous != null && !crossing) {
                    if (following == null || following!!.isDone || bot.navigator.currentTarget !== previous) {
                        stopFollowing()
                        following = bot.navigator.follow(previous)
                    }
                    bot.naturalMicroDelay()
                    continue
                }
                stopFollowing()
                if (previous == null && !BossOutings.canTravel(bot)) {
                    bot.naturalMicroDelay()
                    continue
                }
                val journey = async { nativeTravel() }
                while (!journey.isCompleted && BossOutings.member(bot) === token && permitted()) {
                    if (target.zone.area.contains(bot.position)) break
                    if (previous == null && !BossOutings.canTravel(bot)) break
                    if (previous != null && !target.zone.area.contains(previous.position) &&
                        previous.position.isWithinDistance(bot.position, BossOutings.FORMATION_DISTANCE)) break
                    bot.naturalMicroDelay()
                }
                if (target.zone.area.contains(bot.position)) {
                    journey.cancelAndJoin()
                    BossOutings.arrived(bot)
                    return@coroutineScope true
                }
                if (!journey.isCompleted) journey.cancelAndJoin()
                else if (!journey.await()) return@coroutineScope false
            }
            false
        } finally {
            stopFollowing()
        }
    }
}
