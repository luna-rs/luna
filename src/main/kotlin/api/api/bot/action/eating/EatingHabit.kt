package api.bot.action.eating

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How a bot looks after its hitpoints during one fight against monsters.
 *
 * A habit is rolled from the bot's personality when a fight starts, so the same bot doesn't eat at exactly the same
 * hitpoints twice, and two bots with the same personality don't eat alike:
 * - **When to eat.** Intelligent bots plan from how hard their attackers hit, keeping a cushion of several maximum hits.
 * Less intelligent bots go by gut feeling, a share of their hitpoints that ignores the monster. Confidence shrinks both.
 * - **How quickly they react.** Dextrous bots eat a tick or two after the hit that takes them low. Clumsy bots take
 * longer, and sometimes don't notice a hit at all until they glance at their hitpoints later.
 * - **How well they read their hitpoints.** Without a hitpoints orb, players judged their hitpoints from hitsplats and
 * the health bar, so clumsy and unintelligent bots misjudge theirs by a few points.
 * - **Food.** Intelligent bots pick food that fits the hitpoints they're missing, top up between fights, and leave with
 * food to spare. Others eat whatever comes first and fight until the food runs out.
 *
 * The numbers come from simulating thousands of fights at the training camps: intelligent or careful bots almost
 * never die, while unintelligent, overconfident and clumsy ones occasionally do, mostly against monsters that hit hard.
 *
 * @property intelligence How much the bot plans its eating instead of going by feel, from `0.0` to `1.0`.
 * @property confidence How much risk the bot accepts in this fight, from `0.0` to `1.0`.
 * @property dexterity How quickly and precisely the bot reacts, from `0.0` to `1.0`.
 * @property cushionHits How many of its attackers' maximum hits a planning bot keeps between itself and death.
 * @property gutShare The share of its hitpoints at which a bot going by feel starts to worry.
 * @author lare96
 */
class EatingHabit(val intelligence: Double,
                  val confidence: Double,
                  val dexterity: Double,
                  val cushionHits: Double,
                  val gutShare: Double) {

    companion object {

        /**
         * The lowest share of its hitpoints any bot plans to fall to before eating.
         */
        const val LOWEST_EAT_SHARE = 0.15

        /**
         * The highest share of its hitpoints any bot eats at, so even a nervous bot doesn't eat after every hit.
         */
        const val HIGHEST_EAT_SHARE = 0.80

        /**
         * How long a bot takes to react to the hit that puts its life in danger, at most, in ticks.
         */
        const val DANGER_REACTION_TICKS = 2

        /**
         * Rolls a habit for one fight.
         *
         * Out-of-range traits are clamped, and non-finite traits are treated as average.
         *
         * @param intelligence The bot's intelligence.
         * @param confidence The bot's confidence.
         * @param dexterity The bot's dexterity.
         * @param scared Whether the bot is scared going into this fight, which costs it a quarter of its confidence.
         */
        fun roll(intelligence: Double, confidence: Double, dexterity: Double, scared: Boolean): EatingHabit {
            fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
            val fightConfidence = normalized(confidence) * if (scared) 0.75 else 1.0
            val caution = 1.0 - fightConfidence
            return EatingHabit(intelligence = normalized(intelligence),
                               confidence = fightConfidence,
                               dexterity = normalized(dexterity),
                               cushionHits = (2.0 + 1.2 * caution) * Random.nextDouble(0.85, 1.15),
                               gutShare = (0.25 + 0.35 * caution) * Random.nextDouble(0.85, 1.15))
        }
    }

    /**
     * How much of a monster's real maximum hit the bot knows about before it has been hit hard by one.
     */
    val knowledge = MonsterMemory.knowledge(intelligence)

    /**
     * The chance the bot fails to notice a hit, until it next glances at its hitpoints or is hit again.
     */
    val zoneOutChance = 0.30 * (1.0 - dexterity) * (1.0 - 0.5 * intelligence)

    /**
     * The chance the bot skips topping up between fights, trusting it can eat once the next fight starts.
     */
    val skipTopUpChance = 0.15 + 0.5 * confidence * (1.0 - intelligence)

    /**
     * The chance the bot takes an extra bite after a scare, even once it's no longer low.
     */
    val panicBiteChance = 0.5 * (1.0 - confidence)

    /**
     * The most ticks the bot takes, beyond the first, to react to a hit.
     */
    private val reactionSpread = 2 + (5 * (1.0 - dexterity)).roundToInt()

    /**
     * The hitpoints at or below which the bot eats.
     *
     * @param maximumHp The bot's hitpoints level.
     * @param danger The most the bot expects its attackers to hit for, together.
     */
    fun eatAt(maximumHp: Int, danger: Int): Int {
        val planned = cushionHits * danger + 1
        val felt = gutShare * maximumHp
        val eatAt = intelligence * planned + (1.0 - intelligence) * felt
        return eatAt.coerceIn(LOWEST_EAT_SHARE * maximumHp, HIGHEST_EAT_SHARE * maximumHp).roundToInt().coerceAtLeast(1)
    }

    /**
     * The hitpoints the bot likes to have before starting another fight.
     *
     * @param maximumHp The bot's hitpoints level.
     * @param danger The most the bot expects its attackers to hit for, together.
     */
    fun comfortableAt(maximumHp: Int, danger: Int): Int =
        (eatAt(maximumHp, danger) + (1.0 + 1.5 * (1.0 - confidence)) * danger).roundToInt().coerceAtMost(maximumHp)

    /**
     * How many pieces of food the bot keeps for getting away safely. Once that's all it has left, it eats the next
     * time it needs to and then leaves to restock. Unintelligent bots keep nothing back.
     *
     * @param danger The most the bot expects its attackers to hit for, together.
     * @param heal How much the bot's food heals.
     */
    fun foodToKeep(danger: Int, heal: Int): Int =
        if (intelligence < 0.5 || heal <= 0) 0 else ceil(intelligence * 2 * danger / heal).toInt()

    /**
     * Rolls how many ticks the bot takes to react to a hit, at least one.
     *
     * @param inDanger Whether the hit left the bot within one hit of dying. Nobody dawdles then.
     */
    fun reactionTicks(inDanger: Boolean): Int {
        val ticks = 1 + Random.nextInt(reactionSpread + 1)
        return if (inDanger) minOf(ticks, DANGER_REACTION_TICKS) else ticks
    }

    /**
     * Rolls how many ticks pass before a bot that missed a hit glances at its hitpoints again.
     */
    fun glanceTicks(): Int = Random.nextInt(4, 9 + (16 * (1.0 - dexterity)).roundToInt())

    /**
     * Rolls how far off the bot's sense of its own hitpoints is, in either direction.
     *
     * @param maximumHp The bot's hitpoints level.
     */
    fun misjudgement(maximumHp: Int): Int {
        val error = (0.08 * maximumHp * (1.0 - 0.5 * dexterity - 0.5 * intelligence)).roundToInt()
        return if (error <= 0) 0 else Random.nextInt(-error, error + 1)
    }

    /**
     * Rolls whether the bot picks its food carefully this time, rather than eating whatever comes first.
     */
    fun picksFoodCarefully(): Boolean = Random.nextDouble() < intelligence
}
