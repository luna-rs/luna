package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.generate.YanilleDungeonCrossingSpec
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.agility
import api.predef.hitpoints
import game.player.item.consume.potion.Potion
import game.skill.agility.yanille.YanilleDungeon
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the native dungeon obstacles and lets the walker replan through the spider-cave stairs after a fall.
 * The bars land on their end object, one tile before the next ordinary walking leg.
 *
 * @author lare96
 */
object YanilleDungeonCrossing : CrossingHandler {

    const val ID = "yanille_dungeon"
    val LEVELS = setOf(40, 49, 57, 67)
    private val antipoisons = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS,
        Potion.SUPER_ANTIPOISON, Potion.ANTIPOISON_POTION).flatMap { it.doses.reversed() }

    fun flag(level: Int) = "yanille_agility_$level"

    fun ready(bot: Bot, level: Int) = bot.agility.level >= level

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Yanille obstacle has no action"
        val id = action.objectId ?: return "the Yanille obstacle has no object id"
        val position = action.objectPosition ?: return "the Yanille obstacle has no object position"
        val spec = YanilleDungeonCrossingSpec.ALL.firstOrNull { id in it.closedObjectIds }
            ?: return "unsupported Yanille obstacle $id"
        if (spec.placements.none { it.pos == position && it.from == leg.from && it.to == leg.to }) {
            return "unsupported Yanille obstacle approach"
        }
        if (context.arrived(leg, 0)) return null
        context.walk(leg.from, 0)?.let { return it }
        val level = when (id) {
            YanilleDungeon.LEDGE -> 40
            YanilleDungeon.PIPE -> 49
            YanilleDungeon.MONKEY_BARS -> 57
            YanilleDungeon.RUBBLE_UP -> 67
            else -> 0
        }
        if (!ready(context.bot, level)) return "the Yanille obstacle needs current Agility $level"
        val risky = id == YanilleDungeon.LEDGE || id == YanilleDungeon.MONKEY_BARS
        if (risky && context.bot.hitpoints.level <= YanilleDungeon.fallDamage(context.bot)) {
            return "Hitpoints are too low to survive a Yanille fall"
        }
        val obj = context.find(position, id) ?: return "Yanille obstacle $id is missing at $position"
        if (!context.handler.interactions.interactOnLeg(1, obj)) return "could not use Yanille obstacle $id"
        val landing = if (id == YanilleDungeon.MONKEY_BARS)
            leg.to.translate(0, if (leg.to.y == YanilleDungeon.BARS_SOUTH) 1 else -1) else leg.to
        val finished = waitFor(20.seconds) {
            !context.bot.isLocked && (context.bot.position == landing ||
                risky && context.bot.y in 9540..9599 && context.bot.position != leg.from)
        }
        if (!finished) return "the Yanille obstacle did not finish"
        if (context.bot.position != landing) {
            return if (recoverFall(context)) "fell into the spider cave and climbed out; replan from the north room"
            else "fell into the spider cave and could not reach its native exit"
        }
        return if (landing == leg.to) null else context.walk(leg.to, 0)
    }

    /**
     * Leaves a failed crossing through its ordinary stairs before another route attempt starts.
     * Missing supplies never prevent the safest available escape, and all movement stays inside this native leg.
     */
    internal suspend fun recoverFall(context: LegContext): Boolean = withTimeoutOrNull(45.seconds) {
        val bot = context.bot
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return@withTimeoutOrNull false
        if (bot.status.isPoisoned() && !bot.status.isAntiPoison()) {
            val cure = antipoisons.firstOrNull { bot.inventory.contains(it) }
            if (cure != null && context.handler.inventory.clickItem(1, cure)) {
                waitFor(3.seconds) { !bot.status.isPoisoned() || bot.status.isAntiPoison() || !bot.isAlive }
            }
        }
        if (bot.healthPercent <= 50) context.handler.inventory.eatAnyFood()
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return@withTimeoutOrNull false
        val front = Position(2578, 9583)
        if (context.walk(front, 0) != null || bot.position != front) return@withTimeoutOrNull false
        val stairs = context.find(Position(2578, 9584), 2316) ?: return@withTimeoutOrNull false
        context.handler.interactions.interactOnLeg(1, stairs) && waitFor(20.seconds) {
            bot.position == Position(2567, 9524) && !bot.isLocked &&
                bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
        }
    } == true
}
