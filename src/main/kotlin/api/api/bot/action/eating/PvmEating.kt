package api.bot.action.eating

import api.attr.Attr
import api.bot.action.PvmFight
import api.bot.action.eating.MonsterMemory.Companion.monsterMemory
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.random.Random

/**
 * Eats for a bot while it fights monsters, the way a player keeping an eye on their hitpoints would.
 *
 * Each fight gets its own [EatingHabit]. The bot notices damage a few ticks after it lands, or misses it until it next
 * glances at its hitpoints, and reads its hitpoints with some error. Once it believes it has fallen to its eating
 * point, it eats ([CarriedFood.choose] picks what), and keeps eating as quickly as the food allows until it's above that
 * point again. How hard it expects to be hit is learned through [MonsterMemory]. Between fights, it may top up to the
 * hitpoints it likes to start a fight with.
 *
 * While a script keeps calling [check], usually through [api.bot.action.PvmTactics], it is attending to the bot's
 * hitpoints, and [game.bot.scripts.EatFoodReflexScript] leaves eating to it. If the script stops checking because it was paused or
 * is busy elsewhere, attention lapses after [ATTENTION_TICKS] and the reflex looks after the bot again.
 *
 * @param bot The bot to eat for.
 * @author lare96
 */
class PvmEating(private val bot: Bot) {

    companion object {

        /**
         * How long a bot keeps attending to its hitpoints after its script last checked them, in ticks. Longer than
         * the time between checks while fighting, shorter than any trip away from the fight.
         */
        const val ATTENTION_TICKS = 8

        /**
         * The tick until which a script is attending to the bot's hitpoints.
         */
        private var Bot.attendingUntil by Attr.long { -1L }

        /**
         * Whether a script using [PvmEating] is currently looking after [bot]'s eating.
         */
        fun isAttending(bot: Bot): Boolean = bot.world.currentTick <= bot.attendingUntil
    }

    /**
     * How the bot eats in its current or most recent fight, or `null` before its first fight.
     */
    private var habit: EatingHabit? = null

    /**
     * The most the bot expects its attackers to hit for, together, in its current or most recent fight.
     */
    private var danger = 1

    /**
     * The monster the current fight is against.
     */
    private var opponent: Npc? = null

    /**
     * The life of [opponent] this fight is against; a respawned NPC is a new fight.
     */
    private var opponentLife = -1

    /**
     * The hitpoints the bot last saw, or `-1` before it has looked.
     */
    private var seenHp = -1

    /**
     * How far off the bot's sense of its hitpoints is.
     */
    private var misjudged = 0

    /**
     * The tick the bot last looked at its hitpoints.
     */
    private var lookedAt = -1L

    /**
     * The tick from which the bot acts on the damage it has taken.
     */
    private var noticeAt = -1L

    /**
     * The earliest tick the bot can take its next bite.
     */
    private var nextBiteAt = -1L

    /**
     * Whether a scare has the bot taking one more bite than it needs.
     */
    private var panicBite = false

    /**
     * Whether the bot has decided if it tops up during the current lull between fights.
     */
    private var lullDecided = false

    /**
     * Whether the bot is topping up during the current lull between fights.
     */
    private var toppingUp = false

    /**
     * Whether the bot, low on hitpoints with only the food it keeps back, is eating up before it leaves to restock.
     */
    private var eatingBeforeRestock = false

    /**
     * Looks after the bot's hitpoints once: notices damage it has taken, and eats if it's time to.
     *
     * @param fight The monsters the bot is fighting, or `null` if it isn't fighting.
     * @return `false` if the bot needs to leave and restock food.
     */
    suspend fun check(fight: PvmFight?): Boolean {
        val now = bot.world.currentTick
        bot.attendingUntil = now + ATTENTION_TICKS
        val hp = bot.health
        if (hp <= 0) {
            return true
        }
        val maximumHp = bot.totalHealth
        val fighting = updateFight(fight)
        observe(now, hp, maximumHp)
        val fed = if (fighting) duringFight(now, hp, maximumHp) else betweenFights(now, hp, maximumHp)
        if (!fed) {
            eatingBeforeRestock = false
        }
        return fed
    }

    /**
     * Stops attending to the bot's hitpoints, handing them back to [game.bot.scripts.EatFoodReflexScript] right away.
     */
    fun stopAttending() {
        bot.attendingUntil = -1L
    }

    /**
     * How much food to take from the bank: the pieces the bot keeps back for getting away, as of its last fight, plus
     * [fighting] pieces to eat. With only the reserve, it would leave to restock as soon as it got back.
     *
     * @param heal How much the food heals.
     * @param fighting How many pieces to eat while fighting.
     */
    fun foodToCarry(heal: Int, fighting: Int): Int = (habit?.foodToKeep(danger, heal) ?: 0) + fighting

    /**
     * Whether the bot, as of its last fight, is below the hitpoints it likes to start a fight with. With no food it
     * would leave to restock as soon as it got back, as [betweenFights] does.
     */
    fun needsFood(): Boolean {
        val habit = habit ?: return false
        return bot.health + misjudged < habit.comfortableAt(bot.totalHealth, danger)
    }

    /**
     * Starts a new fight with a fresh habit when the bot's opponent changes, and works out how hard it expects to be
     * hit.
     *
     * @return `true` if the bot is fighting a monster.
     */
    private fun updateFight(fight: PvmFight?): Boolean {
        if (fight == null) {
            return false
        }
        val primary = fight.primary
        if (primary !== opponent || primary.spawnCount != opponentLife) {
            opponent = primary
            opponentLife = primary.spawnCount
            habit = EatingHabit.roll(bot.personality.intelligence, bot.personality.confidence,
                                     bot.personality.dexterity, bot.emotions.isFeeling(EmotionType.SCARED))
            panicBite = false
            lullDecided = false
        }
        val knowledge = habit!!.knowledge
        danger = (fight.attackers + primary).sumOf { bot.monsterMemory.expectedMaxHit(it, knowledge) }.coerceAtLeast(1)
        return true
    }

    /**
     * Notices changes to the bot's hitpoints. Damage is acted on after the bot's reaction time, counted from when the
     * hit landed, unless it zones out and only notices when it next glances at its hitpoints.
     */
    private fun observe(now: Long, hp: Int, maximumHp: Int) {
        val habit = habit
        if (seenHp != -1 && hp < seenHp && habit != null) {
            val hitAt = bot.monsterMemory.lastHitTick.takeIf { it >= lookedAt } ?: now
            misjudged = habit.misjudgement(maximumHp)
            val inDanger = hp <= danger || hp + misjudged <= danger
            val reactionAt = hitAt + when {
                inDanger -> habit.reactionTicks(true)
                Random.nextDouble() < habit.zoneOutChance -> habit.glanceTicks()
                else -> habit.reactionTicks(false)
            }
            // Another hit can hurry a pending reaction, but cannot make the bot forget it.
            noticeAt = if (noticeAt < 0) reactionAt else minOf(noticeAt, reactionAt)
        } else if (hp > seenHp) {
            // Having just eaten or healed, the bot has a closer look.
            misjudged /= 2
        }
        seenHp = hp
        lookedAt = now
    }

    /**
     * Eats during a fight once the bot notices it has fallen to its eating point.
     *
     * @return `false` if the bot needs to leave and restock food.
     */
    private suspend fun duringFight(now: Long, hp: Int, maximumHp: Int): Boolean {
        val habit = habit!!
        val believedHp = hp + misjudged
        val eatAt = habit.eatAt(maximumHp, danger)
        if (hp >= maximumHp) {
            noticeAt = -1L
            panicBite = false
            return true
        }
        if (now < noticeAt) {
            return true
        }
        if (believedHp > eatAt && !panicBite) {
            noticeAt = -1L
            return true
        }
        val foods = CarriedFood.of(bot)
        if (foods.isEmpty()) {
            bot.log("Out of food at $hp/$maximumHp hitpoints, leaving to restock.")
            return false
        }
        if (now < nextBiteAt) {
            return true
        }
        val inDanger = believedHp <= danger
        val food = CarriedFood.choose(foods, maximumHp - believedHp, inDanger, habit.picksFoodCarefully())!!
        val keep = habit.foodToKeep(danger, foods.maxOf { it.food.heal })
        bot.log("Eating ${food.food.formattedName} at $hp/$maximumHp hitpoints " +
                    "(eats at about $eatAt, expects hits up to $danger).")
        if (!eat(food)) {
            return true
        }
        panicBite = !panicBite && inDanger && Random.nextDouble() < habit.panicBiteChance
        if (foods.size <= keep) {
            bot.log("Down to the food kept for getting away safely, leaving to restock.")
            return false
        }
        return true
    }

    /**
     * Between fights, decides once whether to top up to the hitpoints the bot likes to start a fight with, and does so
     * at a relaxed pace. When the bot is low on hitpoints and down to the food it keeps back, it always tops up, then
     * leaves to restock.
     *
     * @return `false` if the bot needs to leave and restock food.
     */
    private suspend fun betweenFights(now: Long, hp: Int, maximumHp: Int): Boolean {
        val habit = habit ?: return true
        val believedHp = hp + misjudged
        val comfortable = habit.comfortableAt(maximumHp, danger)
        val foods = CarriedFood.of(bot)
        val keep = habit.foodToKeep(danger, foods.maxOfOrNull { it.food.heal } ?: 0)
        if (foods.size > keep) {
            eatingBeforeRestock = false
        }
        if (believedHp >= comfortable && !eatingBeforeRestock) {
            return true
        }
        if (foods.size <= keep && !eatingBeforeRestock) {
            if (foods.isEmpty()) {
                bot.log("Low on hitpoints and food between fights, leaving to restock.")
                return false
            }
            // The food kept back is for getting away mid-fight. Nothing is attacking, so the bot eats it rather than
            // walking to the bank and back on low hitpoints.
            eatingBeforeRestock = true
            bot.log("Low on hitpoints and food between fights, eating up before leaving to restock.")
        }
        if (!eatingBeforeRestock) {
            if (!lullDecided) {
                lullDecided = true
                toppingUp = Random.nextDouble() >= habit.skipTopUpChance
            }
            if (!toppingUp) {
                return true
            }
        }
        if (now < nextBiteAt) {
            return true
        }
        val food = CarriedFood.choose(foods, maximumHp - believedHp, false, habit.picksFoodCarefully())
        if (food == null || believedHp + food.food.heal / 2 >= comfortable) {
            if (eatingBeforeRestock) {
                bot.log("Low on hitpoints and food between fights, leaving to restock.")
                return false
            }
            toppingUp = false
            return true
        }
        bot.log("Topping up with ${food.food.formattedName} at $hp/$maximumHp hitpoints between fights.")
        if (eat(food)) {
            // Out of combat there's no rush between bites.
            nextBiteAt += Random.nextInt(1, 4)
        }
        return true
    }

    /**
     * Eats [food] with one click, as a player would, and schedules the earliest next bite.
     *
     * @return `true` if the bot ate it.
     */
    private suspend fun eat(food: CarriedFood): Boolean {
        if (!bot.actionHandler.inventory.eat(food.index, food.id)) {
            return false
        }
        noticeAt = -1L
        nextBiteAt = bot.world.currentTick + food.food.delay + Random.nextInt(0, 2)
        return true
    }
}
