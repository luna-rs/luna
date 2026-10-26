package api.bot.action.prayer

import api.bot.action.eating.MonsterMemory
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * How a bot spends its prayer points during one fight against monsters.
 *
 * Prayer points only come back at an altar or from a potion, so a player treats them as a budget. A habit is rolled
 * from the bot's personality when a fight starts:
 * - **When protection pays off.** Bosses always, and otherwise only monsters that can take a big share of the bot's
 * hitpoints in one round of hits. Confident bots need a bigger share before they bother.
 * - **Which protection.** Bots that know their monsters pray against the way the monster usually attacks, from the
 * start of the fight. Others copy the last hit they took, a moment after it lands. Unintelligent bots now and then
 * misread the fight and pick the wrong protection, until the next hit sets them straight.
 * - **Boosts.** Only against tough monsters. Careless bots sometimes switch on a defence prayer they don't need too.
 * - **Saving points.** Intelligent bots keep some points back for an emergency, stop boosting well before that, and
 * switch everything off between kills. Others pray until their points run out and often leave prayers on.
 *
 * @property intelligence How much the bot plans its prayer instead of reacting, from `0.0` to `1.0`.
 * @property dexterity How quickly the bot reacts, from `0.0` to `1.0`.
 * @property threatShare The share of its hitpoints one round of hits must be able to take before protection pays off.
 * @property toughShare How high a monster's combat level must be, as a share of the bot's own, to be tough.
 * @property knowsStyles Whether the bot prays against the way its monster usually attacks, rather than the last hit.
 * @property tidy Whether the bot switches off prayers it no longer needs during the fight.
 * @property wasteful Whether the bot also switches on a defence prayer when boosting.
 * @author lare96
 */
class PrayerHabit(val intelligence: Double,
                  val dexterity: Double,
                  val threatShare: Double,
                  val toughShare: Double,
                  val knowsStyles: Boolean,
                  val tidy: Boolean,
                  val wasteful: Boolean) {

    companion object {

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
        fun roll(intelligence: Double, confidence: Double, dexterity: Double, scared: Boolean): PrayerHabit {
            fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
            val smarts = normalized(intelligence)
            val fightConfidence = normalized(confidence) * if (scared) 0.75 else 1.0
            return PrayerHabit(intelligence = smarts,
                               dexterity = normalized(dexterity),
                               threatShare = (0.15 + 0.15 * fightConfidence) * Random.nextDouble(0.85, 1.15),
                               toughShare = (0.8 + 0.4 * fightConfidence) * Random.nextDouble(0.9, 1.1),
                               knowsStyles = Random.nextDouble() < smarts,
                               tidy = Random.nextDouble() < smarts,
                               wasteful = Random.nextDouble() < 0.5 * (1.0 - smarts))
        }
    }

    /**
     * How much of a monster's real maximum hit the bot knows about before it has been hit hard by one.
     */
    val knowledge = MonsterMemory.knowledge(intelligence)

    /**
     * The chance the bot picks the wrong protection each time it reads which one it needs. Rare even for the least
     * intelligent bots, and practically never for intelligent ones.
     */
    val wrongProtectionChance = 0.12 * (1.0 - intelligence) * (1.0 - intelligence)

    /**
     * The chance the bot switches its prayers off once a kill is done, rather than leaving them on.
     */
    val switchOffChance = 0.1 + 0.9 * intelligence

    /**
     * The most ticks the bot takes, beyond the first, to react.
     */
    private val reactionSpread = 2 + (5 * (1.0 - dexterity)).roundToInt()

    /**
     * The prayer points the bot keeps back for an emergency, below which it stops protecting itself.
     *
     * @param prayerLevel The bot's Prayer level.
     */
    fun reserve(prayerLevel: Int): Int = (0.3 * intelligence * prayerLevel).roundToInt()

    /**
     * The prayer points below which the bot stops boosting, to keep points for protection.
     *
     * @param prayerLevel The bot's Prayer level.
     */
    fun boostFloor(prayerLevel: Int): Int = reserve(prayerLevel) + (0.25 * intelligence * prayerLevel).roundToInt()

    /**
     * Rolls how many ticks the bot takes to react, at least one.
     *
     * @param inDanger Whether the bot is close to dying. Nobody dawdles then.
     */
    fun reactionTicks(inDanger: Boolean): Int {
        val ticks = 1 + Random.nextInt(reactionSpread + 1)
        return if (inDanger) minOf(ticks, 2) else ticks
    }

    /**
     * Rolls how many ticks after a kill the bot gets round to switching its prayers off. Unintelligent bots take
     * longer to think of it.
     */
    fun switchOffTicks(): Int = reactionTicks(false) + Random.nextInt(1 + (10 * (1.0 - intelligence)).roundToInt())
}
