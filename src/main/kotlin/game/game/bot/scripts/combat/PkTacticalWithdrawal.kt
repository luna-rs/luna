package game.bot.scripts.combat

import api.bot.action.BotCombatActionHandler
import api.bot.action.estimateCombatThreat
import api.bot.action.eating.CarriedFood
import engine.controllers.MultiCombatAreaListener
import engine.controllers.WildernessLocatableController.levelAt
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.MobReference
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionType
import kotlinx.coroutines.yield
import kotlin.math.abs

/**
 * Creates a small gap against melee while preserving a projectile attack or making time to eat after thawing.
 * The owning script retains the opponent and decides whether to resume once movement ends.
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
        private const val CLOSE_RANGE = 2
        private const val MAX_STEPS = 2
        private const val RECHECK_TICKS = 3L
        private const val SPACE_COOLDOWN_TICKS = 10L
    }

    private val recovery = PkCombatRecovery(bot)
    private var frozenAgainst: MobReference? = null
    private var nextCheck = 0L

    /**
     * Forgets an observation made before the script stopped or paused.
     */
    fun clearObservation() {
        frozenAgainst = null
    }

    /**
     * Uses up to two ordinary steps during the attack delay. Pure melee falls back only to recover after thawing.
     *
     * @return True when a withdrawal step was queued and the caller needs to reassess the fight.
     */
    suspend fun createSpace(target: Player): Boolean {
        if (bot.status.isImmobilized()) {
            if (frozenAgainst?.resolve() !== target && isCloseFight(target)) {
                frozenAgainst = MobReference.capture(target)
            }
            return false
        }
        val thawed = frozenAgainst?.resolve() === target
        clearObservation()
        val now = bot.world.currentTick
        if (now < nextCheck || bot.combat.attackDelay <= 0 || !isCloseFight(target)) return false
        val enemyReach = PkCombatRecovery.attackReach(target)
        if (enemyReach.type != InteractionType.SIZE) return false

        val ourReach = PkCombatRecovery.attackReach(bot)
        val projectile = ourReach.type == InteractionType.LINE_OF_SIGHT &&
            estimateCombatThreat(bot, target, capability = false, checkSupplies = true).maximumHit > 0
        val pressured = bot.healthPercent <= 75 && target.combat.target === bot &&
            CarriedFood.of(bot).any { it.food.heal > 0 } &&
            PkCombatRecovery.shouldEat(bot.health, bot.totalHealth, recovery.pressureFrom(target), spaced = false)
        if (!(projectile && target.status.isImmobilized()) && !(pressured && (projectile || thawed))) return false

        val combat = bot.combat
        val hold = combat.beginTacticalWithdrawal() ?: return false
        val fromMulti = MultiCombatAreaListener.inside(bot.position)
        nextCheck = now + RECHECK_TICKS
        var queued = false
        var pendingStep: Position? = null
        try {
            for (step in 0 until MAX_STEPS) {
                if (queued && (combat.isExplicitlyDisabled || bot.status.isImmobilized() ||
                        combat.target != null || !canContinue(target) || combat.attackDelay <= 0 ||
                        PkCombatRecovery.attackReach(target).type != InteractionType.SIZE)) break
                val origin = bot.position
                if (!combatPolicy.stepAwayFrom(target, retraceHistory = false) {
                        validStep(it, target, fromMulti) &&
                            !it.isWithinDistance(target.position, enemyReach.distance) &&
                            (!projectile || bot.world.collisionManager.reached(it, target, ourReach))
                    }) break
                pendingStep = bot.walking.singleStep
                if (!queued) {
                    queued = true
                    nextCheck = now + SPACE_COOLDOWN_TICKS
                    combat.target = null
                    bot.interact(null)
                }
                yield()
                if (bot.position == origin) break
                pendingStep = null
                if (!bot.position.isWithinDistance(target.position, CLOSE_RANGE)) break
            }
        } finally {
            bot.walking.cancelSingleStep(pendingStep)
            combat.finishTacticalWithdrawal(hold)
        }
        return queued
    }

    private fun validStep(tile: Position, target: Player, fromMulti: Boolean): Boolean {
        val level = minOf(levelAt(tile), levelAt(target.position))
        return level > 0 && abs(bot.combatLevel - target.combatLevel) <= level &&
            tile.isViewable(target.position) && MultiCombatAreaListener.inside(tile) == fromMulti && allowsTile(tile)
    }

    private fun isCloseFight(target: Player): Boolean =
        bot.combat.target === target && bot.position.isWithinDistance(target.position, CLOSE_RANGE) &&
            canContinue(target) && combatPolicy.canAttackWildernessPlayer(target)

    private fun canContinue(target: Player): Boolean =
        ownsFight() && bot.isAlive && bot.healthPercent >= PkCombatRecovery.SURVIVAL_HEALTH_PERCENT &&
            bot.combat.inCombat() && bot.combat.lastCombatWith === target &&
            target.state == EntityState.ACTIVE && target.isAlive && target.isViewableFrom(bot) &&
            levelAt(bot.position) > 0 && levelAt(target.position) > 0 && allowsFight(target)
}
