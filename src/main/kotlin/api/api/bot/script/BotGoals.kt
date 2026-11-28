package api.bot.script

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.predef.*
import engine.bot.coordinator.skill.SkillingCoordinator
import game.skill.guide.SkillGuides
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.memory.BotDiaryMemory
import io.luna.game.model.mob.bot.memory.BotGoalsMemory
import io.luna.game.model.mob.bot.memory.BotOutlookMemory
import io.luna.game.model.mob.bot.memory.BotGoalsMemory.Kind
import io.luna.util.RandomUtils
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sets and follows a bot's goals: an ambition for the account and an aim in one skill (see [BotGoalsMemory]).
 *
 * Targets are the levels a player reads in the skill guide, one category per skill, so a bot aims for 60 Woodcutting
 * because of yew trees and not for a round number. A target is at least a few levels off, never past what the
 * account may train, and the bot goes for it in the work it already does.
 *
 * @author TheLining
 */
object BotGoals {

    /**
     * One level worth working toward in a skill.
     *
     * @property level The level.
     * @property reason What it unlocks, as the guide puts it, or `null`.
     */
    class Step(val level: Int, val reason: String?)

    /**
     * What a level gained meant for the bot's goals.
     */
    enum class News {

        /**
         * The aim is a few levels off.
         */
        CLOSE,

        /**
         * A goal was met.
         */
        REACHED
    }

    /**
     * What a bot can say about a goal.
     *
     * @property skill The skill's name, or "combat" or "total".
     * @property level The level now.
     * @property target The level aimed for.
     * @property unlock What the target unlocks, or [MISSING].
     * @property ambition The ambition in a few words, or [MISSING].
     */
    class Line(val skill: String, val level: Int, val target: Int, val unlock: String, val ambition: String) {

        /**
         * The levels still to go.
         */
        val left get() = maxOf(0, target - level)
    }

    /**
     * A level gained and what it meant.
     */
    class LevelNews(val news: News, val line: Line)

    /**
     * Stands in for a part of a line the bot has nothing for, so the line is skipped.
     */
    const val MISSING = "\u0000"

    /**
     * The skill guide categories that hold each skill's main unlocks. Hitpoints and Farming are never aims.
     */
    private val LADDERS = mapOf(
        Skill.ATTACK to listOf("Weapons"),
        Skill.STRENGTH to listOf("Weapons"),
        Skill.DEFENCE to listOf("Melee"),
        Skill.RANGED to listOf("Bows", "Armour"),
        Skill.PRAYER to listOf("Prayers"),
        Skill.MAGIC to listOf("Other"),
        Skill.COOKING to listOf("Meat & fish"),
        Skill.WOODCUTTING to listOf("Trees"),
        Skill.FLETCHING to listOf("Bows"),
        Skill.FISHING to listOf("Fish"),
        Skill.FIREMAKING to listOf("Logs"),
        Skill.CRAFTING to listOf("Leather", "Dragonhide"),
        Skill.SMITHING to listOf("Smelting"),
        Skill.MINING to listOf("Ores"),
        Skill.HERBLORE to listOf("Potions"),
        Skill.AGILITY to listOf("Courses"),
        Skill.THIEVING to listOf("Pickpocket", "Stalls"),
        Skill.SLAYER to listOf("Monsters"),
        Skill.RUNECRAFTING to listOf("Runes")
    )

    /**
     * The skills that make up a PK build.
     */
    private val BUILD_SKILLS = listOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
                                      Skill.PRAYER)

    /**
     * The most levels an aim is set ahead before a round level on the way is taken instead.
     */
    private const val FURTHEST_STEP = 15

    /**
     * The highest combat level.
     */
    private const val MOST_COMBAT = 126

    /**
     * The highest total level of the skills a bot can have.
     */
    private const val MOST_TOTAL = 21 * 99

    /**
     * How many ticks pass between two checks of a bot's goals while nothing happens.
     */
    private const val REVIEW_TICKS = 100L

    /**
     * The world tick from which this bot's goals are checked again.
     */
    private var Bot.nextGoalReview by Attr.long()

    /**
     * What the bot is about to say about a goal, for the tags of goal lines.
     */
    var Bot.goalLine by Attr.obj { Line("null", 0, 0, MISSING, MISSING) }

    /**
     * Each skill's steps, lowest first, one per level.
     */
    private val steps: Map<Int, List<Step>> by lazy {
        val guides = SkillGuides.ALL.associateBy { Skill.getId(it.name) }
        LADDERS.mapValues { (skill, categories) ->
            val guide = guides[skill] ?: return@mapValues emptyList()
            guide.categories.filter { it.name in categories }.flatMap { it.entries }
                .mapNotNull { entry -> entry.level?.takeIf { it in 2..99 }?.let { Step(it, reason(entry.text)) } }
                .sortedBy { it.level }.distinctBy { it.level }
        }
    }

    /**
     * Checks the bot's goals when the brain is asked for work: counts the session that just closed toward the aim,
     * gives up on an aim that has stalled, and sets an ambition and an aim where there are none. Without a session
     * closing, this runs at most once every [REVIEW_TICKS].
     *
     * @param bot The bot.
     * @param closed The session that just closed, or `null`.
     */
    @JvmStatic
    fun review(bot: Bot, closed: BotDiaryMemory.Closed?) {
        if (bot.isEventBot) {
            return
        }
        val goals = bot.memory.goals
        if (closed != null && closed.skill != BotDiaryMemory.NO_SKILL) {
            goals.sessionEnded(closed.skill, closed.started)
        }
        val tick = world.currentTick
        if (closed == null && tick < bot.nextGoalReview) {
            return
        }
        bot.nextGoalReview = tick + REVIEW_TICKS
        checkAmbition(bot)
        if (goals.hasAim()) {
            val skill = goals.aimSkill()
            when {
                bot.skill(skill).staticLevel >= goals.aimTarget() -> goals.aimReached()
                goals.stalled() -> {
                    goals.dropAim()
                    bot.memory.outlook.felt(BotOutlookMemory.Event.GAVE_UP_GOAL)
                }
                !canAim(bot, skill) -> goals.dropAim()
            }
        }
        if (!goals.hasAmbition()) {
            pickAmbition(bot)
        }
        if (!goals.hasAim()) {
            pickAim(bot)
        }
    }

    /**
     * Updates the bot's goals after it gains a level, and says what that meant for them. A skilling session on an
     * aim just reached winds down, so the next one can use what the level unlocked.
     *
     * @param bot The bot.
     * @param skill The skill.
     * @param level The level reached.
     * @return The news for the bot to talk about, or `null`.
     */
    @JvmStatic
    fun onLevelUp(bot: Bot, skill: Int, level: Int): LevelNews? {
        if (bot.isEventBot) {
            return null
        }
        val goals = bot.memory.goals
        goals.levelled(skill)
        var news: LevelNews? = null
        if (goals.aimSkill() == skill) {
            val target = goals.aimTarget()
            val reason = goals.aimReason()
            if (level >= target) {
                goals.aimReached()
                news = LevelNews(News.REACHED, line(bot, skill, target, reason))
                windDown(bot, skill)
            } else if (target - level <= BotGoalsMemory.CLOSE) {
                news = LevelNews(News.CLOSE, line(bot, skill, target, reason))
            }
        }
        val ambition = checkAmbition(bot)
        if (news == null && ambition != null) {
            news = LevelNews(News.REACHED, ambition)
        }
        if (!goals.hasAmbition()) {
            pickAmbition(bot)
        }
        if (!goals.hasAim()) {
            pickAim(bot)
        }
        return news
    }

    /**
     * Gives the aim's skill its share of the skill roll.
     *
     * @param bot The bot.
     * @param weights The skill roll's weights, with the rest of the bot's memory already applied.
     */
    fun pullSkillWeights(bot: Bot, weights: MutableMap<Int, Double>) {
        val goals = bot.memory.goals
        val skill = goals.aimSkill()
        val own = weights[skill] ?: return
        val others = weights.entries.sumOf { if (it.key == skill) 0.0 else it.value }
        weights[skill] = goals.aimWeight(own, others, bot.memory.interestIn(skill, skill in bot.preferences.skills))
    }

    /**
     * Decides whether a skilling session whose time is up runs on to reach the aim. It does only if the session is
     * about the aim, the aim is close at the pace of this session, and the bot keeps to its plans.
     *
     * @param bot The bot.
     * @return How much longer to run, or `null` to stop.
     */
    fun runOn(bot: Bot): Duration? {
        val diary = bot.memory.diary
        val goals = bot.memory.goals
        val skill = diary.openSkill()
        if (skill == BotDiaryMemory.NO_SKILL || skill != goals.aimSkill()) {
            return null
        }
        val experience = bot.skills.sumOf { it.experience.toLong() }
        val minutes = goals.runOnMinutes(skill, diary.openRate(experience))
        if (minutes <= 0.0 || !rand(goals.focus())) {
            return null
        }
        return (minutes * 1.15 + 1.0).minutes
    }

    /**
     * @return The melee skill the bot is aiming for, which its attack style should train, or `null`.
     */
    fun meleeAim(bot: Bot): Int? =
        bot.memory.goals.aimSkill().takeIf { it == Skill.ATTACK || it == Skill.STRENGTH || it == Skill.DEFENCE }

    /**
     * @return What the bot can say when asked what it is going for, or `null` if it has no aim.
     */
    fun answer(bot: Bot): Line? {
        val goals = bot.memory.goals
        return if (goals.hasAim()) line(bot, goals.aimSkill(), goals.aimTarget(), goals.aimReason()) else null
    }

    /**
     * Whether a goal line has everything it names. Lines that name an unlock or an ambition the bot lacks are skipped.
     */
    fun complete(line: String): Boolean = MISSING !in line

    /**
     * The level the bot should aim for next in a skill: the first step in the skill's guide at least [gap] levels
     * up, or the cap past the last step. A step more than [FURTHEST_STEP] levels off gives way to the round level on
     * the way, so an aim stays within reach.
     *
     * @param skill The skill.
     * @param level The current level.
     * @param cap The highest level the account trains it to.
     * @param gap The fewest levels to aim ahead.
     * @return The step, or `null` if the skill is at its cap.
     */
    internal fun target(skill: Int, level: Int, cap: Int, gap: Int): Step? {
        if (level >= cap) {
            return null
        }
        val floor = minOf(level + maxOf(1, gap), cap)
        val step = steps[skill].orEmpty().firstOrNull { it.level in floor..cap } ?: Step(cap, null)
        val round = minOf((floor + 9) / 10 * 10, cap)
        return if (step.level - level > FURTHEST_STEP && round < step.level) Step(round, null) else step
    }

    /**
     * @return Whether the bot can aim for a level in [skill]: the account still trains it, something the bot does
     * trains it, on purpose or while making money, and it is not Hitpoints or Farming. Attack and Strength are aims only
     * for a bot that fights in melee, and Ranged only for one that fights at range, since combat follows the bot's main
     * style. Prayer is trained only on purpose.
     */
    internal fun canAim(bot: Bot, skill: Int): Boolean {
        if (skill == Skill.HITPOINTS || skill == Skill.FARMING || skill !in LADDERS) {
            return false
        }
        if (!BotCombatActionHandler.wantsToTrain(bot, skill) || bot.skill(skill).staticLevel >= cap(bot, skill)) {
            return false
        }
        val preferences = bot.preferences
        fun open(activity: BotActivity): Boolean {
            val interest = preferences.activities[activity] ?: return false
            return interest.isFinite() && interest > 0.0 && preferences.canChooseActivity(activity)
        }
        val skilling = open(BotActivity.TRAINING_SKILLS) || open(BotActivity.PROFIT_SKILLS)
        val combat = open(BotActivity.TRAINING_COMBAT) || open(BotActivity.PROFIT_COMBAT)
        return when (skill) {
            Skill.MAGIC -> skilling || open(BotActivity.TRAINING_COMBAT)
            Skill.RANGED -> fightsAtRange(bot) && combat
            Skill.ATTACK, Skill.STRENGTH -> !fightsAtRange(bot) && combat
            Skill.DEFENCE -> combat
            Skill.PRAYER -> skill in SkillingCoordinator.factories && open(BotActivity.TRAINING_SKILLS)
            else -> skill in SkillingCoordinator.factories && skilling
        }
    }

    /**
     * @return Whether the bot's main style is ranged: its build has no melee, or its Ranged is above its Attack and
     * Strength.
     */
    private fun fightsAtRange(bot: Bot): Boolean {
        val build = bot.preferences.pk?.build()
        if (build != null && build.targetLevel(Skill.ATTACK) <= 1 && build.targetLevel(Skill.STRENGTH) <= 1) {
            return true
        }
        return bot.skill(Skill.RANGED).staticLevel >
            maxOf(bot.skill(Skill.ATTACK).staticLevel, bot.skill(Skill.STRENGTH).staticLevel)
    }

    /**
     * Ends the skilling session on [skill] a few minutes from now: the bot finishes what it is doing and moves on.
     */
    private fun windDown(bot: Bot, skill: Int) {
        if (bot.memory.diary.openSkill() != skill) {
            return
        }
        val within = rand(2, 6).minutes
        bot.scriptStack.scripts.filterIsInstance<ZonedBotScript>().forEach { it.windDown(within) }
    }

    /**
     * Records the ambition as met or forgets it once it no longer fits the account.
     *
     * @return What the bot can say about it if it was met, or `null`.
     */
    private fun checkAmbition(bot: Bot): Line? {
        val goals = bot.memory.goals
        val target = goals.ambitionTarget()
        when (goals.ambitionKind() ?: return null) {
            Kind.SKILL -> {
                val skill = goals.ambitionSkill()
                val level = bot.skill(skill).staticLevel
                if (level >= target) {
                    goals.ambitionReached(skill, level)
                    return line(bot, skill, level, null)
                }
                if (!canAim(bot, skill)) {
                    goals.clearAmbition()
                }
            }
            Kind.BUILD -> {
                if (bot.preferences.pk?.build() == null) {
                    goals.clearAmbition()
                } else if (unfinishedBuild(bot).isEmpty()) {
                    goals.ambitionReached(BotGoalsMemory.COMBAT_LEVEL, bot.combatLevel)
                }
            }
            Kind.COMBAT -> if (bot.combatLevel >= target) {
                goals.ambitionReached(BotGoalsMemory.COMBAT_LEVEL, bot.combatLevel)
                return Line("combat", bot.combatLevel, target, MISSING, MISSING)
            }
            Kind.TOTAL -> {
                val total = totalLevel(bot)
                if (total >= target) {
                    goals.ambitionReached(BotGoalsMemory.TOTAL_LEVEL, total)
                    return Line("total", total, target, MISSING, MISSING)
                }
            }
        }
        return null
    }

    /**
     * Sets an ambition by what kind of player the bot is: one skill to 99 for skillers and no-lifers, the PK build for
     * PKers, a combat level for heroes and kids, and a total level for everyone else or when nothing else fits.
     */
    private fun pickAmbition(bot: Bot) {
        val goals = bot.memory.goals
        val eligible = Skill.IDS.filter { canAim(bot, it) }
        if (eligible.isEmpty()) {
            return
        }
        when (bot.personality.type) {
            PersonalityTemplateType.PKER -> if (unfinishedBuild(bot).isNotEmpty()) {
                goals.setAmbition(Kind.BUILD, BotGoalsMemory.NO_SKILL, 0)
            }
            PersonalityTemplateType.SKILLER, PersonalityTemplateType.NO_LIFE -> {
                val skill = RandomUtils.weightedRoll(eligible.associateWith { sqrt(bot.skill(it).experience + 1_000.0) })
                if (skill != null) {
                    goals.setAmbition(Kind.SKILL, skill, cap(bot, skill))
                }
            }
            PersonalityTemplateType.HERO, PersonalityTemplateType.KID -> {
                val combat = bot.combatLevel
                val target = minOf((combat + 3 + 9) / 10 * 10, MOST_COMBAT)
                if (target > combat && eligible.any { it in BUILD_SKILLS }) {
                    goals.setAmbition(Kind.COMBAT, BotGoalsMemory.NO_SKILL, target)
                }
            }
            else -> Unit
        }
        if (!goals.hasAmbition()) {
            val total = totalLevel(bot)
            val target = minOf((total + 25 + 99) / 100 * 100, MOST_TOTAL)
            if (target > total) {
                goals.setAmbition(Kind.TOTAL, BotGoalsMemory.NO_SKILL, target)
            }
        }
    }

    /**
     * Sets an aim. Most often, by how focused the bot is, the aim serves the ambition; otherwise it is something on
     * the side, most likely what the bot has been doing lately or a skill close to its next unlock.
     */
    private fun pickAim(bot: Bot) {
        val goals = bot.memory.goals
        val eligible = Skill.IDS.filter { canAim(bot, it) && !goals.recentlyDropped(it) }
        if (eligible.isEmpty()) {
            return
        }
        val skill = (if (rand(goals.focus())) servesAmbition(bot, eligible) else null) ?: onTheSide(bot, eligible)
        val level = bot.skill(skill).staticLevel
        val step = target(skill, level, cap(bot, skill), goals.gap()) ?: return
        goals.setAim(skill, step.level, step.reason, level)
    }

    /**
     * @return A skill that moves the bot toward its ambition, or `null`.
     */
    private fun servesAmbition(bot: Bot, eligible: List<Int>): Int? {
        val goals = bot.memory.goals
        val weights: Map<Int, Double> = when (goals.ambitionKind() ?: return null) {
            Kind.SKILL -> return goals.ambitionSkill().takeIf { it in eligible }
            Kind.BUILD -> unfinishedBuild(bot).filter { it in eligible }.associateWith {
                (cap(bot, it) - bot.skill(it).staticLevel).toDouble()
            }
            Kind.COMBAT -> eligible.filter { it in BUILD_SKILLS }.associateWith {
                (100 - bot.skill(it).staticLevel).toDouble()
            }
            Kind.TOTAL -> eligible.sortedBy { bot.skill(it).staticLevel }.take(5).associateWith { 1.0 }
        }
        return if (weights.isEmpty()) null else RandomUtils.weightedRoll(weights)
    }

    /**
     * @return A skill for an aim on the side.
     */
    private fun onTheSide(bot: Bot, eligible: List<Int>): Int {
        val diary = bot.memory.diary
        val weights = eligible.associateWith { skill ->
            val level = bot.skill(skill).staticLevel
            val close = steps[skill].orEmpty().any { it.level > level && it.level - level <= BotGoalsMemory.CLOSE }
            0.2 + diary.minutesOn(skill) / 60.0 + if (close) 1.0 else 0.0
        }
        return RandomUtils.weightedRoll(weights) ?: eligible.random()
    }

    /**
     * @return The build skills still short of what the bot's PK build calls for.
     */
    private fun unfinishedBuild(bot: Bot): List<Int> {
        val build = bot.preferences.pk?.build() ?: return emptyList()
        return BUILD_SKILLS.filter { build.targetLevel(it) > 1 && bot.skill(it).staticLevel < build.targetLevel(it) }
    }

    /**
     * @return The highest level the account trains [skill] to.
     */
    private fun cap(bot: Bot, skill: Int): Int =
        if (Skill.isCombatSkill(skill)) bot.preferences.pk?.build()?.targetLevel(skill) ?: 99 else 99

    private fun totalLevel(bot: Bot): Int = bot.skills.sumOf { it.staticLevel }

    /**
     * @return What the bot can say about an aim in [skill] for [target].
     */
    private fun line(bot: Bot, skill: Int, target: Int, reason: String?): Line {
        val name = Skill.getName(skill).lowercase()
        val goals = bot.memory.goals
        val ambition = when (goals.ambitionKind()) {
            Kind.SKILL -> "${goals.ambitionTarget()} ${Skill.getName(goals.ambitionSkill()).lowercase()}"
            Kind.COMBAT -> "${goals.ambitionTarget()} combat"
            Kind.TOTAL -> "${goals.ambitionTarget()} total"
            Kind.BUILD, null -> null
        }
        return Line(name, bot.skill(skill).staticLevel, target, reason ?: MISSING,
                    ambition?.takeIf { it != "$target $name" } ?: MISSING)
    }

    /**
     * Turns a guide row into what a player would say it unlocks: the words before any detail, in lowercase, or
     * `null` if that is too long to say in passing.
     */
    internal fun reason(text: String): String? {
        val words = text.substringBefore(" - ").substringBefore(",").substringBefore(" (").trim().lowercase()
        return words.takeIf { it.isNotEmpty() && it.length <= 24 }
    }
}
