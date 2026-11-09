package api.bot.action

import api.attr.Attr
import api.bot.script.BotScript.ActionSafety
import api.combat.death.PlayerDeathItems
import engine.controllers.WildernessController
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.WitnessedPkEscapeBotScript
import game.bot.scripts.combat.CombatBotScript
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobReference
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.combat.state.CombatContext.COMBAT_TIMER_TICKS

/**
 * Coalesces visible Wilderness fights into one escape decision at the observing bot's safe update boundary.
 * Attack callbacks only record bounded, non-owning native identities. Valuation and script handoff happen later,
 * once per encounter, without changing aggression attribution, attack permission or personal combat responses.
 *
 * @author lare96
 */
internal class BotPvpAwareness(private val bot: Bot) {

    /**
     * Decided and pending encounters share eight slots. Continuing attacks refresh the same unordered pair;
     * a crowded scene drops new encounters rather than evicting an ongoing fight and deciding it again.
     */
    private val encounters = ArrayList<Encounter>(MAX_ENCOUNTERS)

    /**
     * At most one incident awaits a safe update. Native references cannot retain a removed player's session.
     */
    private var pending: Incident? = null

    /**
     * A decision to remain also delays the next valuation, bounding work when several nearby fights start.
     */
    private var nextDecisionTick = Long.MIN_VALUE

    /**
     * Records an eligible launched attack without valuation, suspension, navigation or another scene query.
     */
    fun observeAttack(attacker: Player, victim: Player) {
        if (!exposed(attacker, victim)) return
        val attackerRef = attacker.reference ?: return
        val victimRef = victim.reference ?: return
        val now = bot.world.currentTick
        encounters.removeIf { now - it.lastSeenTick > ENCOUNTER_SILENCE_TICKS }
        val existing = encounters.firstOrNull { it.matches(attackerRef, victimRef) }
        if (existing != null) {
            existing.lastSeenTick = now
            return
        }
        if (encounters.size >= MAX_ENCOUNTERS) return
        val encounter = Encounter(attackerRef, victimRef, now)
        encounters += encounter
        if (pending == null && now >= nextDecisionTick) {
            pending = Incident(attackerRef, victimRef, bot.position, now)
        }
    }

    /**
     * Consumes at most one cue after custom reflexes, preserving personal combat and activity-owned escapes.
     * A locked or strong-action bot can wait briefly for safety; stale cues never trigger delayed flight.
     */
    fun respond(): Boolean {
        val incident = pending ?: return false
        val now = bot.world.currentTick
        if (now - incident.tick !in 0..PENDING_TICKS || !autonomous() || !ordinaryWilderness(bot) ||
            bot.combat.inCombat()) {
            pending = null
            return false
        }
        val parent = bot.scriptStack.current()
        if (parent is CombatBotScript || parent is WitnessedPkEscapeBotScript ||
            bot.actionHandler.combat.isFleeingWilderness) {
            pending = null
            return false
        }
        if (!ActionSafety.COMBAT.allows(bot)) return false
        pending = null
        if (now < nextDecisionTick) return false
        val attacker = incident.attacker.resolve() as? Player ?: return false
        val victim = incident.victim.resolve() as? Player ?: return false
        if (!exposed(attacker, victim) ||
            (attacker.combat.target !== victim && victim.combat.target !== attacker)) return false
        nextDecisionTick = now + DECISION_COOLDOWN_TICKS
        if (!shouldEscape(bot.personality.confidence) {
                val share = PkRiskBudget.basisPoints(bot.personality.confidence, bot.personality.intelligence,
                    bot.preferences.activities[BotActivity.PKING] ?: 0.0)
                PkRiskBudget.assessor(bot, PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()), share)(
                    bot.inventory.filterNotNull(), bot.equipment.filterNotNull(), false)
            }) return false
        bot.scriptStack.pushHead(WitnessedPkEscapeBotScript(bot, incident.position, attacker.username, parent))
        return true
    }

    /**
     * Checks possible incoming harm, deliberately without skiller, attack-suppression or single-combat gates.
     */
    private fun exposed(attacker: Player, victim: Player): Boolean = autonomous() &&
        bot !== attacker && bot !== victim && canWitnessAttack(attacker, victim) && ordinaryWilderness(bot) &&
        attacker.position.z == bot.position.z && victim.position.z == bot.position.z &&
        attacker.isViewableFrom(bot) && victim.isViewableFrom(bot) &&
        kotlin.math.abs(attacker.combatLevel - bot.combatLevel) <= minOf(levelAt(attacker.position), levelAt(bot.position))

    /**
     * Connected human accounts only participate while their explicit automation owns the session.
     */
    private fun autonomous(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive &&
        !bot.client.isPendingLogout && (!bot.isConnectedPlayer || bot.isAutomationActive)

    /**
     * An unordered native identity pair, retaining neither player and never confusing a reused protocol slot.
     *
     * @author lare96
     */
    private class Encounter(val first: MobReference, val second: MobReference, var lastSeenTick: Long) {
        fun matches(attacker: MobReference, victim: MobReference): Boolean =
            (first == attacker && second == victim) || (first == victim && second == attacker)
    }

    /**
     * One short-lived visible attack cue and the observing bot's location before its activity is interrupted.
     *
     * @author lare96
     */
    private data class Incident(val attacker: MobReference, val victim: MobReference, val position: Position, val tick: Long)

    /**
     * Shared bounded delivery and deterministic policy, confined to native game-thread calls.
     *
     * @author lare96
     */
    companion object {
        private const val MAX_ENCOUNTERS = 8
        private const val MAX_OBSERVERS = 64
        private const val PENDING_TICKS = 10L
        private const val ENCOUNTER_SILENCE_TICKS = COMBAT_TIMER_TICKS + 2L
        private const val DECISION_COOLDOWN_TICKS = 50L

        /**
         * Each attacker's next crowded-scene slice starts after its previous batch. This transient attribute is
         * never persisted, and independent attackers cannot repeatedly select the same half of a crowded scene.
         * All access is on the game thread, as with the native attack hook.
         */
        private var Player.observerCursor by Attr.int()

        /**
         * Special controllers retain their own rules and deaths, even inside a geometric Wilderness boundary.
         */
        fun canWitnessAttack(attacker: Player, victim: Player): Boolean = attacker !== victim &&
            ordinaryWilderness(attacker) && ordinaryWilderness(victim) && attacker.position.z == victim.position.z

        /**
         * Reuses the native attack hook's local scene and caps new observer work at sixty-four candidates per launch.
         */
        fun notifyObservers(attacker: Player, players: Collection<Player>, notify: (Bot) -> Unit) {
            if (players.size <= MAX_OBSERVERS) {
                for (player in players) if (player is Bot) notify(player)
                return
            }
            val local = players.toList()
            val start = attacker.observerCursor % local.size
            repeat(MAX_OBSERVERS) { offset ->
                val player = local[(start + offset) % local.size]
                if (player is Bot) notify(player)
            }
            attacker.observerCursor = (start + MAX_OBSERVERS) % local.size
        }

        /**
         * Low confidence uses the existing .7 personal-threat cutoff. Otherwise native carried loss must exceed
         * an explicit personality allowance; the supplied assessment is skipped entirely for low-confidence bots.
         */
        internal fun shouldEscape(confidence: Double, assessment: () -> PkRiskBudget): Boolean {
            val normalized = if (confidence.isFinite()) confidence.coerceIn(0.0, 1.0) else 0.5
            if (normalized < 0.7) return true
            val risk = assessment()
            return risk.deathLoss > risk.maximumLoss
        }

        private fun ordinaryWilderness(player: Player): Boolean = player.state == EntityState.ACTIVE &&
            player.isAlive && levelAt(player.position) > 0 && player.controllers.primary is WildernessController
    }
}
