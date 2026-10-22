package api.bot.action

import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Session-local evidence for protection and visible fleeing without inventing an aggressor in an existing fight.
 * Records visible fresh attack starts and visible pursuits over consecutive bot cycles. A chase requires
 * ordinary walking/running movement towards a fleeing player, rather than a shared destination or mutual combat.
 * Evidence expires when either participant disappears or the attacker stops targeting the observed victim.
 *
 * @author lare96
 */
internal class PkCombatObservations(private val bot: Bot) {
    /**
     * Consecutive local samples and aggressor/victim pairs, bounded by the observer's current visible scene.
     */
    private var previousCycle: Long? = null
    private var previous = emptyMap<Player, Position>()
    private val aggressors = mutableMapOf<Player, Player>()
    private val fleeing = mutableMapOf<Player, Player>()

    /**
     * Called only for a fresh native PvP attack, before combat timers are reset.
     * Both participants must be visible to the observer at the start, not merely when damage lands later.
     */
    fun attackStarted(attacker: Player, victim: Player) {
        // A witnessed fleeing victim fighting back does not become the aggressor of the same encounter.
        if (aggressors[victim] === attacker && threatening(victim, attacker)) return
        if (attacker !== victim && visible(attacker) && visible(victim)) aggressors[attacker] = victim
    }

    /**
     * Samples visible movement once per bot cycle. Missing cycles or disappearing players break continuity.
     * The native walking queue moves at most two tiles per cycle; larger jumps cannot establish a chase.
     */
    fun observe(players: Collection<Player>) {
        val cycle = bot.cycles
        if (previousCycle == cycle) return
        fleeing.clear()
        aggressors.entries.removeIf { !threatening(it.key, it.value) }
        val current = players.filter { visible(it) }.associateWith { it.position }
        if (previousCycle?.plus(1) == cycle) {
            for ((attacker, now) in current) {
                val victim = attacker.combat.target as? Player ?: continue
                val victimNow = current[victim] ?: continue
                if (victim.combat.target === attacker) continue
                val before = previous[attacker] ?: continue
                val victimBefore = previous[victim] ?: continue
                if (before.z != now.z || victimBefore.z != victimNow.z) continue
                val attackerSteps = now.computeLongestDistance(before)
                val victimSteps = victimNow.computeLongestDistance(victimBefore)
                if (attackerSteps !in 1..2 || victimSteps !in 1..2) continue
                val oldGap = before.computeLongestDistance(victimBefore)
                if (now.computeLongestDistance(victimBefore) < oldGap &&
                    before.computeLongestDistance(victimNow) > oldGap) {
                    aggressors.putIfAbsent(attacker, victim)
                    fleeing[victim] = attacker
                }
            }
        }
        previous = current
        previousCycle = cycle
    }

    /**
     * Allows intervention only while the observed aggressor is still threatening the visible victim.
     * Native Wilderness, controller and single/multi-combat checks remain the caller's responsibility.
     */
    fun canProtectAgainst(attacker: Player): Boolean = aggressors[attacker]?.let { threatening(attacker, it) } == true

    /**
     * Reports current-cycle fleeing only, rather than labelling every witnessed attack victim as fleeing.
     * A stationary victim, broken observation continuity or discontinued pursuit removes this preference.
     */
    fun isFleeing(player: Player): Boolean = fleeing[player]?.let { threatening(it, player) } == true

    /**
     * Drops world references when the owning session finishes; observations are never persisted across login.
     */
    fun clear() {
        previousCycle = null
        previous = emptyMap()
        aggressors.clear()
        fleeing.clear()
    }

    private fun threatening(attacker: Player, victim: Player): Boolean = visible(attacker) && visible(victim) &&
        attacker.combat.target === victim

    private fun visible(player: Player): Boolean = player !== bot && player.state == EntityState.ACTIVE &&
        player.isAlive && player.position.z == bot.position.z && player.isViewableFrom(bot)
}
