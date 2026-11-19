package game.bot.scripts.combat

import api.bot.action.BotCombatActionHandler
import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobReference
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.yield

/**
 * Gives an established PK fight one short chance to create distance after a close-range freeze.
 * The owning script keeps the opponent and decides whether to resume once the movement ends.
 *
 * @author lare96
 */
internal class PkTacticalWithdrawal(
    private val bot: Bot,
    private val combatPolicy: BotCombatActionHandler,
    private val ownsFight: () -> Boolean,
    private val allowsFight: (Player) -> Boolean,
    private val allowsTile: (Position) -> Boolean
) {
    companion object {
        const val SURVIVAL_HEALTH_PERCENT = 30
        private const val CLOSE_RANGE = 2
        private const val MAX_STEPS = 2
    }

    private var frozenAgainst: MobReference? = null
    private var attempted = false

    /**
     * Forgets an observation made before the script stopped or paused.
     */
    fun clearObservation() {
        frozenAgainst = null
    }

    /**
     * Observes an engaged freeze and, on thaw, queues up to two ordinary steps on successive turns.
     *
     * @return True when a withdrawal step was queued and the caller needs to reassess the fight.
     */
    suspend fun afterThaw(target: Player): Boolean {
        if (attempted) return false
        if (bot.status.isImmobilized()) {
            if (frozenAgainst?.resolve() !== target && isCloseFight(target)) {
                frozenAgainst = MobReference.capture(target)
            }
            return false
        }
        val wasFrozenAgainst = frozenAgainst
        clearObservation()
        if (wasFrozenAgainst?.resolve() !== target || !isCloseFight(target)) return false

        val combat = bot.combat
        val hold = combat.beginTacticalWithdrawal() ?: return false
        var pendingStep: Position? = null
        try {
            for (step in 0 until MAX_STEPS) {
                if (step > 0 && (combat.isExplicitlyDisabled || bot.status.isImmobilized() ||
                        combat.target != null || !canContinue(target))) break
                val origin = bot.position
                if (!combatPolicy.stepAwayFrom(target, retraceHistory = false) {
                        levelAt(it) > 0 && it.isViewable(target.position) && allowsTile(it)
                    }) break
                pendingStep = bot.walking.singleStep
                if (!attempted) {
                    attempted = true
                    combat.target = null
                    bot.interact(null)
                }
                yield()
                if (bot.position == origin) break
                pendingStep = null
            }
        } finally {
            bot.walking.cancelSingleStep(pendingStep)
            combat.finishTacticalWithdrawal(hold)
        }
        return attempted
    }

    private fun isCloseFight(target: Player): Boolean =
        bot.combat.target === target && bot.position.isWithinDistance(target.position, CLOSE_RANGE) &&
            canContinue(target) && combatPolicy.canAttackWildernessPlayer(target)

    private fun canContinue(target: Player): Boolean =
        ownsFight() && bot.isAlive && bot.healthPercent >= SURVIVAL_HEALTH_PERCENT &&
            bot.combat.inCombat() && bot.combat.lastCombatWith === target &&
            target.state == EntityState.ACTIVE && target.isAlive && target.isViewableFrom(bot) &&
            levelAt(bot.position) > 0 && levelAt(target.position) > 0 && allowsFight(target)
}
