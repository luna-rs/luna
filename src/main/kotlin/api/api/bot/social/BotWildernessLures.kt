package api.bot.social

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.PkLoadoutPreparation
import api.bot.action.PkRiskBudget
import api.bot.script.BotScript
import api.bot.webwalk.plan.BankAccess
import api.combat.death.PlayerDeathItems
import api.predef.ext.*
import api.predef.rand
import api.predef.world
import engine.bot.speech.BotReactions.lastScammedBy
import engine.bot.speech.BotScamSpeechPool
import engine.bot.speech.BotScamSpeechPool.ScamSpeech
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/**
 * Bot-only Wilderness invitations with native following and delivered travel conversations. The lurer may fight
 * personally or recruit a prepared, idle PK patrol. Victims retain their carried kit and all native combat reflexes.
 * No travel step withdraws valuables, suppresses escape, changes a saved PK identity or transfers death items.
 *
 * @author lare96
 */
object BotWildernessLures {
    private var Bot.nextLure by Attr.long()
    private var Bot.lureFight: Fight? by Attr.nullableObj(Fight::class)

    /**
     * Predatory combat must fit the permanent purpose. Recreational SKILL bots and cape-clan outings do not become
     * solo hunters through a scam; kind or uncertain GENERIC bots retain their protection/defence-only behavior.
     */
    internal fun canAmbush(bot: Bot): Boolean = !bot.preferences.isSkiller && when (bot.preferences.pk?.purpose()) {
        Purpose.VALUE -> true
        Purpose.GENERIC -> !bot.personality.isKind && !bot.personality.isUncertain
        else -> false
    }

    /**
     * Compares prospective native death loss with the victim's carried value. Low intelligence permits full risk;
     * smarter victims refuse an excessive exposure rather than being banked or stripped by the lurer.
     */
    internal fun acceptsRisk(intelligence: Double, carried: java.math.BigInteger, loss: java.math.BigInteger): Boolean {
        if (loss.signum() <= 0) return false
        if (intelligence <= 0.0) return true
        if (carried.signum() <= 0) return false
        val share = (BotTradeScams.riskFraction(intelligence) * 10_000).toLong()
        return loss * java.math.BigInteger.valueOf(10_000) <= carried * java.math.BigInteger.valueOf(share)
    }

    /**
     * Takes a fresh read-only native retention quote. Bank contents never count as victim risk or travel supplies.
     */
    private fun willingToRisk(victim: Bot): Boolean {
        if (!PlayerDeathItems.losesItems(victim, Luna.settings().game().betaMode())) return false
        val items = victim.inventory.filterNotNull() + victim.equipment.filterNotNull()
        fun value(stock: List<io.luna.game.model.item.Item>) = stock.fold(java.math.BigInteger.ZERO) { sum, item ->
            if (!item.itemDef.isTradeable) sum else sum + java.math.BigInteger.valueOf(
                if (item.id == 995) 1 else world.economy.getPrice(item.unnotedItemDef.id()).coerceAtLeast(0)) *
                java.math.BigInteger.valueOf(item.amount.toLong())
        }
        val loss = PkRiskBudget.assess(victim, losesItems = true).deathLoss
        return acceptsRisk(victim.personality.intelligence, value(items), loss)
    }

    /**
     * Selects a compatible solo trap from existing PK anchors or holds an already-prepared patrol in place.
     * Recruitment is limited to available bots; humans cannot be supplied to this API.
     */
    fun approach(actor: Bot, victim: Bot, useAccomplice: Boolean): Boolean {
        val now = System.currentTimeMillis()
        if (!actor.preferences.isScammer || actor === victim || now < actor.nextLure || now < victim.nextLure ||
            !BotConversations.available(actor) || !BotConversations.available(victim) ||
            victim.preferences.getFeelingsToward(actor.username) < 0.4) return false
        val patrol = if (useAccomplice) world.players.filterIsInstance<Bot>().asSequence()
            .filter { it !== actor && it !== victim && canAmbush(it) && it.state == EntityState.ACTIVE &&
                !it.combat.inCombat() && it.combat.target == null && it.walking.isEmpty && !it.navigator.isActive &&
                !BotSocialInteractions.engaged(it) && !it.overlays.hasWindow() && levelAt(it.position) > 0 }
            .mapNotNull { bot -> (bot.scriptStack.current() as? PkBotScript)?.takeIf {
                it.isRunning() && !it.isRestocking() && it.wantsToAttack(victim) &&
                    PkLoadoutPreparation(bot, bot.actionHandler).ready() &&
                    levelAt(bot.position) >= abs(bot.combatLevel - victim.combatLevel) + 1
            }?.let { bot to it } }.firstOrNull() else null
        if (useAccomplice && patrol == null) return false
        val killer = patrol?.first ?: actor
        if (!canAmbush(killer) || !PkLoadoutPreparation(killer, killer.actionHandler).ready()) return false
        val policy = patrol?.second ?: PkBotScript(killer, 10.minutes)
        val difference = abs(killer.combatLevel - victim.combatLevel) + 1
        val destination = patrol?.first?.position ?: (PkBotScript.PkArea.LOW_LEVEL.anchors +
            PkBotScript.PkArea.CHAOS_TEMPLE_LVL_10.anchors + PkBotScript.PkArea.GREEN_DRAGONS.anchors)
            .filter { levelAt(it) >= difference && policy.allowsHuntingAt(it) }
            .minByOrNull { levelAt(it) * 1000 + abs(it.x - actor.position.x) + abs(it.y - actor.position.y) }
            ?: return false
        val lines = BotScamSpeechPool.exchange(ScamSpeech.LURE_PITCH) ?: return false
        val pitch = BotConversations.start(actor, victim, lines) ?: return false
        val session = Journey(actor, victim, killer, policy, Position(destination.x, destination.y, destination.z), pitch)
        if (!BotSocialInteractions.claim(session.bots, session)) {
            pitch.close()
            return false
        }
        actor.nextLure = now + 5 * 60_000
        victim.nextLure = now + 15 * 60_000
        return true
    }

    /**
     * Called by the native death hook after the actual lost-item selection. The source must be the assigned ambusher,
     * and a delayed death action may finish briefly after its combat script observed zero health.
     */
    fun onDeath(victim: Player, source: Any?, lostItems: Boolean) {
        val bot = victim as? Bot ?: return
        val fight = bot.lureFight ?: return
        bot.lureFight = null
        if (source !== fight.killer || !lostItems || fight.recorded || !fight.recent() || levelAt(victim.position) <= 0) return
        fight.recorded = true
        bot.lastScammedBy = fight.lurer
        bot.preferences.adjustFeelingsToward(fight.lurer, -0.5)
        if (fight.killer.username != fight.lurer) bot.preferences.adjustFeelingsToward(fight.killer.username, -0.5)
    }

    /**
     * One transient ambush, separate from invitation ownership so a victim can defend or flee through normal reflexes.
     *
     * @author lare96
     */
    private class Fight(val lurer: String, val victim: Bot, val killer: Bot, val policy: PkBotScript) {
        private val deadline = System.currentTimeMillis() + 120_000
        private var finishedAt: Long? = null
        var recorded = false
        /**
         * Allows native death processing to settle after the combat script observed zero health.
         */
        fun recent(): Boolean = System.currentTimeMillis() <= minOf(deadline, finishedAt?.plus(10_000) ?: deadline)
        /**
         * Revalidates the particular victim through the original PK purpose and the native player-combat checks.
         */
        fun allows(): Boolean = finishedAt == null && recent() && canAmbush(killer) &&
            policy.wantsToAttack(victim) && policy.allowsPursuit(victim) &&
            killer.actionHandler.combat.canAttackWildernessPlayer(victim)
        /**
         * Clears an abandoned live target; dead victims keep the short-lived tag until their death hook settles.
         */
        fun finished() {
            finishedAt = System.currentTimeMillis()
            if (victim.health > 0 && victim.lureFight === this) victim.lureFight = null
        }
    }

    /**
     * An invitation, optional border reassurance, native follow journey and legal ambush. Participants continue to
     * execute through their original scripts; an idle accomplice holds its current position instead of patrolling.
     *
     * @author lare96
     */
    private class Journey(val actor: Bot, val victim: Bot, val killer: Bot, val policy: PkBotScript,
                          val destination: Position, var speech: BotConversations.Conversation) : BotSocialInteractions.Session {
        val bots = listOf(actor, victim, killer).distinct()
        private val scripts = bots.map { it.scriptStack.current() }
        private val deadline = System.currentTimeMillis() + 10 * 60_000
        private var travelling = false
        private var following = false
        private var walking = false
        private var ended = false
        private var arrived = false
        private var nextSpeech = 0L
        private val originalRun = actor.walking.isRunning

        override fun valid(): Boolean = !ended && System.currentTimeMillis() < deadline &&
            actor.isWithinDistance(victim, 16) && !actor.ignores.contains(victim.usernameHash) &&
            !victim.ignores.contains(actor.usernameHash) && bots.indices.all { index ->
                val bot = bots[index]
                bot.state == EntityState.ACTIVE && bot.scriptStack.current() === scripts[index] &&
                    scripts[index]?.isRunning() == true && BotScript.ActionSafety.NON_COMBAT.allows(bot) &&
                    bot.reflex.activeReflex == null && bot.combat.target == null
            }

        override suspend fun participate(bot: Bot) {
            speech.tick()
            if (!walking) {
                if (!speech.ended) return
                if (!speech.completed || !willingToRisk(victim) || !rand(BotTradeScams.consentChance(
                        victim.personality.intelligence, victim.preferences.getFeelingsToward(actor.username)))) {
                    close()
                    BotScamSpeechPool.exchange(ScamSpeech.REFUSAL)?.let { BotConversations.start(actor, victim, it) }
                    return
                }
                walking = true
                actor.walking.isRunning = false
            }
            if (!valid()) { close(); return }
            if (bot === victim) {
                if (!willingToRisk(victim)) { close(); return }
                if (!following) {
                    following = victim.actionHandler.interactions.interact(3, actor)
                    if (!following || !valid()) { close(); return }
                }
                if (arrived && actor.isWithinDistance(victim, 2)) ambush()
            } else if (bot === actor && following && !travelling) {
                travelling = true
                val reached = withTimeoutOrNull(120_000) {
                    actor.actionHandler.webWalkTo(destination, bank = BankAccess.NONE)
                } == true
                if (!valid() || !reached || !actor.isWithinDistance(destination, 2)) { close(); return }
                arrived = true
            }
            if (walking && speech.ended && System.currentTimeMillis() >= nextSpeech && actor.isWithinDistance(victim, 8)) {
                nextSpeech = System.currentTimeMillis() + 20_000
                BotScamSpeechPool.exchange(ScamSpeech.LURE_TRAVEL)?.let {
                    BotConversations.start(actor, victim, it, allowMovement = true, interaction = this)?.let { next -> speech = next }
                }
            }
        }

        /**
         * Requotes victim risk and ambusher legality at the destination. No attack is issued by the social protocol;
         * the existing combat script owns switching, reaction delay, native attacks, survival and visible looting.
         */
        private fun ambush() {
            if (!willingToRisk(victim) || !canAmbush(killer) || !policy.wantsToAttack(victim) ||
                !policy.allowsPursuit(victim) || !PkLoadoutPreparation(killer, killer.actionHandler).ready() ||
                !killer.actionHandler.combat.canAttackWildernessPlayer(victim)) { close(); return }
            val fight = Fight(actor.username, victim, killer, policy)
            close()
            victim.lureFight = fight
            // Hold the victim's idle/trader loop only until native combat takes over, without disabling its reflexes.
            val original = victim.scriptStack.current()
            val waiting = object : BotSocialInteractions.Session {
                override fun valid() = fight.recent() && victim.isAlive && victim.scriptStack.current() === original
                override suspend fun participate(bot: Bot) {}
                override fun close() { BotSocialInteractions.release(listOf(victim), this) }
            }
            if (!BotSocialInteractions.claim(listOf(victim), waiting)) { victim.lureFight = null; return }
            killer.scriptStack.pushHead(CombatBotScript(killer, victim, pkSession = policy,
                targetAllowed = { fight.allows() }, onCombatFinished = { fight.finished(); waiting.close() }, lootOnVictory = true))
        }

        override fun close() {
            if (ended) return
            ended = true
            speech.close()
            if (actor.scriptStack.current() === scripts[0]) {
                if (travelling) { actor.navigator.cancel(); actor.walking.clear() }
                actor.walking.isRunning = originalRun
            }
            if (following && victim.navigator.currentTarget === actor) {
                victim.navigator.cancel()
                victim.walking.clear()
            }
            BotSocialInteractions.release(bots, this)
        }
    }
}
