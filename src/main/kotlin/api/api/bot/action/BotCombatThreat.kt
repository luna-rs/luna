package api.bot.action

import game.player.item.consume.food.Food
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot

/**
 * Estimates the danger of a matchup without rolling attacks or changing combat state.
 *
 * Expected damage per tick combines accuracy, maximum hit and attack speed. The score is the expected incoming
 * damage while defeating the opponent divided by the bot's available hitpoints, or the larger two-hit burst ratio.
 * Bosses and uncertain attack profiles each add a 15% safety allowance; neither is an automatic threat.
 *
 * Live decisions use current skills, worn equipment, selected attacks, health and carried food. Food contributes at
 * most one full health bar, less the damage expected during its attack delays; it cannot hide burst danger. Efficient
 * food is credited first and food whose delay costs more than it heals can be ignored, so carrying food never hurts.
 * Opponents' private food and rune inventories, bank stock, pathing, special-attack rolls and future gear switches are
 * not inspected. Native combat eligibility and personality choices remain with the callers.
 *
 * Recovery and achievement decisions compare full health and permanent skill levels without consumables or active
 * prayers. They still use currently worn equipment: items on a corpse are unavailable until recovered. This keeps a
 * defeated opponent or an exhausted food supply from making an otherwise difficult kill appear trivial.
 *
 * The calculation is bounded by carried inventory size and three attack families, with no world searches or cached
 * stat snapshots. Live classification has a dead band and five-tick recovery period to avoid attack/flee oscillation.
 *
 * @author lare96
 */
class BotCombatThreat(private val bot: Bot) {

    /**
     * Orders food by healing per outgoing attack-delay tick, without sorting inventory on each assessment.
     *
     * @author lare96
     */
    private companion object {
        val FOOD_BY_EFFICIENCY = Food.entries.sortedByDescending {
            it.heal.toDouble() / if (it == Food.KARAMBWAN) 2 else 3
        }
    }

    /**
     * The decision being made and its minimum danger score. Recovery is more cautious than choosing to fight;
     * achievements require a matchup that is at least even without the benefit of carried food.
     *
     * @author lare96
     */
    enum class Context(val threshold: Double) {
        COMBAT(0.90),
        RECOVERY(0.80),
        ACHIEVEMENT(1.00)
    }

    /**
     * An instantaneous estimate before live-decision hysteresis. A score of one predicts exhausted endurance before
     * defeating the opponent, or exposure to two maximum hits. It is a safety heuristic, not a win probability.
     *
     * @author lare96
     */
    data class Assessment(val score: Double,
                          val threatened: Boolean,
                          val incomingDamagePerTick: Double,
                          val outgoingDamagePerTick: Double,
                          val maximumIncomingHit: Int,
                          val availableHealing: Double,
                          val uncertain: Boolean)

    /**
     * Carried healing and the endurance it adds after allowing for incoming damage during eating delays.
     *
     * @author lare96
     */
    private data class FoodReserve(val healing: Double, val effectiveHealing: Double)

    private var lastOpponent: Mob? = null
    private var lastLife = -1
    private var lastTick = -1L
    private var safeSince = -1L
    private var threatened = false

    /**
     * Reassesses all relevant live inputs. This method is side-effect free and does not retain the result.
     *
     * @param opponent The other combatant, including a defeated opponent for achievement assessment.
     * @param context The purpose of the decision.
     * @return The bounded danger score and its contributing damage estimates.
     */
    @JvmOverloads
    fun assess(opponent: Mob, context: Context = Context.COMBAT): Assessment {
        val capability = context != Context.COMBAT
        val incoming = estimateCombatThreat(opponent, bot, capability)
        val outgoing = estimateCombatThreat(bot, opponent, capability, checkSupplies = true)
        val maximumHealth = bot.totalHealth.coerceAtLeast(1).toDouble()
        val health = if (capability) maximumHealth else bot.health.coerceAtLeast(1).toDouble()
        val opponentHealth = if (capability) opponent.totalHealth.coerceAtLeast(1).toDouble() else
            opponent.health.coerceAtLeast(0).toDouble()
        val food = if (capability) FoodReserve(0.0, 0.0) else
            carriedFood(maximumHealth.toLong(), incoming.damagePerTick)
        val uncertain = incoming.uncertain || outgoing.uncertain
        val allowance = 1.0 + (if (opponent is Npc && opponent.isBoss) 0.15 else 0.0) +
                (if (uncertain) 0.15 else 0.0)
        val ticksToDefeat = opponentHealth / outgoing.damagePerTick.coerceAtLeast(0.01)
        val sustained = incoming.damagePerTick * ticksToDefeat / (health + food.effectiveHealing)
        val burst = 2.0 * incoming.maximumHit / health
        val score = if (opponentHealth == 0.0) 0.0 else
            (maxOf(sustained, burst) * allowance).coerceIn(0.0, 100.0)
        return Assessment(score, score >= context.threshold, incoming.damagePerTick, outgoing.damagePerTick,
                          incoming.maximumHit, food.healing, uncertain)
    }

    /**
     * Classifies current danger while retaining a live warning through small score changes. Worsening danger takes
     * effect immediately. A live warning clears after observations spanning five ticks below 80% of the entry threshold.
     * Switching opponents, an NPC respawning, or twenty ticks without an assessment starts a fresh decision.
     * Recovery and achievement calls do not read or change this live state.
     *
     * @param opponent The combatant to evaluate.
     * @param context The purpose of the decision.
     * @return Whether the caller should treat the matchup as threatening.
     */
    @JvmOverloads
    fun isThreat(opponent: Mob, context: Context = Context.COMBAT): Boolean {
        val assessment = assess(opponent, context)
        if (context != Context.COMBAT) return assessment.threatened
        val tick = bot.world.currentTick
        val life = (opponent as? Npc)?.spawnCount ?: -1
        if (lastOpponent !== opponent || life != lastLife || tick < lastTick || tick - lastTick >= 20) {
            threatened = assessment.threatened
            safeSince = -1L
        } else if (assessment.threatened) {
            threatened = true
            safeSince = -1L
        } else if (threatened && assessment.score < Context.COMBAT.threshold * 0.8) {
            if (safeSince < 0) safeSince = tick
            if (tick - safeSince >= 5) threatened = false
        } else {
            safeSince = -1L
        }
        lastOpponent = opponent
        lastLife = life
        lastTick = tick
        return threatened
    }

    /**
     * Credits only food the bot already carries, including remaining cake and pie portions. Long intermediates
     * prevent stackable food quantities from overflowing, and the cap bounds both healing and eating time.
     */
    private fun carriedFood(cap: Long, incomingDamagePerTick: Double): FoodReserve {
        val available = LongArray(Food.entries.size)
        for (item in bot.inventory) {
            if (item == null || item.amount <= 0) continue
            val food = Food.ID_TO_FOOD[item.id] ?: continue
            if (food.heal <= 0) continue
            var portion: Int? = item.id
            var portions = 0
            while (portion != null && portions++ < 4) {
                available[food.ordinal] += food.heal.toLong() * item.amount
                portion = food.getNextId(portion)
            }
        }
        var healing = 0L
        var effectiveHealing = 0.0
        for (food in FOOD_BY_EFFICIENCY) {
            if (food.heal <= 0) continue
            val credited = minOf(cap - healing, available[food.ordinal])
            healing += credited
            val attackDelay = if (food == Food.KARAMBWAN) 2 else 3
            val ticks = credited.toDouble() / food.heal * attackDelay
            effectiveHealing += maxOf(0.0, credited - incomingDamagePerTick * ticks)
            if (healing >= cap) break
        }
        return FoodReserve(healing.toDouble(), effectiveHealing)
    }
}
