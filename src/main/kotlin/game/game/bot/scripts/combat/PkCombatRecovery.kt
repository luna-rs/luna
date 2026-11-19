package game.bot.scripts.combat

import api.bot.action.estimateCombatThreat
import engine.controllers.MultiCombatAreaListener
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * Estimates immediate PK pressure from visible opponents and ordinary attack timing.
 *
 * @author lare96
 */
internal class PkCombatRecovery(private val bot: Bot) {
    companion object {
        const val SURVIVAL_HEALTH_PERCENT = 30
        private const val SPACE_RECOVERY_HEALTH_PERCENT = 55
        private const val PRESSURE_TICKS = 3
        private const val RECENT_LAUNCH_TICKS = 4L

        /**
         * Describes current attack reach without choosing, preparing or consuming an attack.
         */
        fun attackReach(player: Player): InteractionPolicy {
            val combat = player.combat
            if (combat.magic.isCasting) return InteractionPolicy.STANDARD_LINE_OF_SIGHT
            val type = if (combat.weapon.isRanged) InteractionType.LINE_OF_SIGHT else InteractionType.SIZE
            return InteractionPolicy(type, combat.weapon.styleDef.range)
        }

        /**
         * Recovers ahead of credible burst damage, or uses safe spacing to heal while the opponent is out of reach.
         */
        fun shouldEat(health: Int, totalHealth: Int, incoming: Int, spaced: Boolean): Boolean {
            if (health <= 0 || health >= totalHealth) return false
            val percent = 100 * health / totalHealth
            if (percent < SURVIVAL_HEALTH_PERCENT) return true
            if (spaced && percent <= SPACE_RECOVERY_HEALTH_PERCENT) return true
            val buffer = (totalHealth / 6).coerceAtLeast(1)
            return incoming > 0 && percent <= 75 && health <= incoming + buffer
        }
    }

    /**
     * Counts the focus and up to two additional active attackers inside native multi-combat.
     */
    fun pressure(focus: Player): Int {
        val primary = pressureFrom(focus)
        if (!MultiCombatAreaListener.inside(bot.position)) return primary
        val others = bot.world.locator.findViewablePlayers(bot) {
            it !== focus && it.combat.target === bot && MultiCombatAreaListener.inside(it.position)
        }
        return primary + others.asSequence().map(::pressureFrom).sortedDescending().take(2).sum()
    }

    /**
     * Estimates an attack that can launch over the next few ticks. A recent landed hit helps when a manual
     * cast or special has already cleared its combat selection.
     */
    fun pressureFrom(opponent: Player): Int {
        if (opponent.state != EntityState.ACTIVE || !opponent.isAlive || !opponent.isViewableFrom(bot)) return 0
        val combat = opponent.combat
        if (combat.target !== bot || combat.isDisabled || combat.isEquipmentSwitching ||
            combat.attackDelay > PRESSURE_TICKS || !canReachSoon(opponent, attackReach(opponent))) return 0
        val received = bot.combat.lastAttackReceived
        val estimate = estimateCombatThreat(opponent, bot, capability = false).maximumHit
        val observed = bot.combat.lastDamageReceived
        val recentHit = if (received != null && received.attacker === opponent && received.launchTick >= 0 &&
            observed != null && observed.attacker === opponent &&
            bot.world.currentTick - received.launchTick in 0..RECENT_LAUNCH_TICKS) observed.rawAmount else 0
        return maxOf(estimate, recentHit)
    }

    private fun canReachSoon(opponent: Player, reach: InteractionPolicy): Boolean {
        val collision = bot.world.collisionManager
        if (collision.reached(opponent, bot, reach)) return true
        val origin = opponent.position
        return reach.type == InteractionType.SIZE && !opponent.status.isImmobilized() &&
            origin.isWithinDistance(bot.position, reach.distance + 1) && Direction.ALL_EXCEPT_NONE.any { direction ->
                collision.reached(origin.translate(1, direction), bot, reach) &&
                    collision.traversable(origin, opponent.type, direction)
            }
    }
}
