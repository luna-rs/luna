package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.generate.NativeAgilityShortcutSpec
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.predef.agility
import api.predef.world
import game.skill.agility.shortcut.AgilityShortcut
import game.skill.agility.shortcut.ShortcutMoves
import game.skill.agility.AgilityApproach
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.path.route.RoutePathfinder
import io.luna.game.model.path.route.RouteStrategy
import io.luna.game.model.path.PathResultType
import kotlin.time.Duration.Companion.seconds

/**
 * Sends native object and rope packets from the shortcut's starting tile or a reachable face on the same side.
 * Every traversal rechecks current Agility, survival and supplies after its approach; falls use native landings.
 *
 * @author lare96
 */
object NativeAgilityShortcutCrossing : CrossingHandler {

    const val ID = "native_agility_shortcut"

    fun flag(level: Int): String = "native_agility_$level"

    fun ready(bot: Bot, level: Int): Boolean = bot.agility.level >= level

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "the Agility shortcut has no action"
        val passage = NativeAgilityShortcutSpec.PASSAGES.firstOrNull {
            it.objectId == action.objectId && it.position == action.objectPosition &&
                it.from == leg.from && it.to == leg.to
        } ?: return "unsupported native Agility shortcut"
        if (context.arrived(leg, 0) && settled(context.bot)) return null
        context.walk(leg.from, 0)?.let { return it }
        val bot = context.bot
        if (!settled(bot) || !ShortcutMoves.ready(bot) || !ready(bot, passage.level)) {
            return "the shortcut needs current Agility ${passage.level} and freedom to move"
        }
        if (bot.health <= passage.maxDamage) return "Hitpoints are too low to survive this shortcut"
        if (passage.stones.isNotEmpty()) {
            for ((id, position) in passage.stones) {
                if (context.arrived(leg, 0) && settled(bot)) return null
                if (!settled(bot) || !ShortcutMoves.ready(bot) || !ready(bot, passage.level)) {
                    return "the stepping stones need current Agility ${passage.level}"
                }
                if (bot.health <= passage.maxDamage) return "Hitpoints are too low to survive a stepping-stone fall"
                val stone = context.find(position, id) ?: return "stepping stone $id is missing at $position"
                if (!world.collisionManager.reached(bot, stone, InteractionPolicy.STANDARD_SIZE) ||
                    !bot.output.sendObjectInteraction(1, stone)) return "could not jump onto stepping stone $id"
                if (!waitFor(20.seconds) {
                    bot.state != EntityState.ACTIVE || !bot.isAlive ||
                        settled(bot) && (bot.position == position || context.arrived(leg, 0))
                }) return "the stepping-stone jump did not finish"
                if (bot.state != EntityState.ACTIVE || !bot.isAlive) return "the stepping-stone crossing was interrupted"
            }
            return if (context.arrived(leg, 0)) null else context.walk(leg.to, 0)
        }
        val obj = context.find(passage.position, passage.objectId)
            ?: return "Agility shortcut ${passage.objectId} is missing at ${passage.position}"
        val stand = clickStand(passage, obj, world.collisionManager)
            ?: return "the shortcut cannot be reached from ${leg.from}"
        if (stand != leg.from) {
            context.walk(stand, 0)?.let { return it }
            if (!settled(bot) || !ShortcutMoves.ready(bot) || !ready(bot, passage.level) ||
                bot.health <= passage.maxDamage) return "the shortcut's admission changed during its approach"
        }
        if (!world.collisionManager.reached(bot, obj, AgilityShortcut.reach(obj))) {
            return "the shortcut cannot be reached from $stand"
        }
        val sent = if (passage.rope) {
            val slot = bot.inventory.computeIndexForId(AgilityShortcut.ROPE)
            slot >= 0 && bot.output.useItemOnObject(slot, AgilityShortcut.ROPE, obj)
        } else bot.output.sendObjectInteraction(1, obj)
        if (!sent) return "could not use Agility shortcut ${passage.objectId}"
        return if (waitFor(20.seconds) { context.arrived(leg, 0) && settled(bot) }) null
        else "the Agility shortcut did not reach ${leg.to}"
    }

    /**
     * Finds a nearby clickable face on the native starting side when the endpoint itself cannot reach the object.
     * Both short walks must be possible, so the native approach can return to its exact starting tile after the click.
     */
    internal fun clickStand(passage: NativeAgilityShortcutSpec.Passage, obj: GameObject,
                            collision: CollisionManager): Position? {
        val policy = AgilityShortcut.reach(obj)
        if (collision.reached(passage.from, obj, policy)) return passage.from
        val shortcut = AgilityShortcut.values().firstOrNull { native ->
            passage.from in native.ends && passage.to in native.ends &&
                native.objects.any { it.first == obj.id && it.second == obj.position }
        } ?: return null
        val candidates = buildList {
            for (dx in -AgilityApproach.RADIUS..AgilityApproach.RADIUS)
                for (dy in -AgilityApproach.RADIUS..AgilityApproach.RADIUS) {
                    val stand = passage.from.translate(dx, dy)
                    if (shortcut.startingTile(stand) == passage.from && collision.reached(stand, obj, policy)) add(stand)
                }
        }.sortedWith(compareBy({ it.computeLongestDistance(passage.from) }, { it.x }, { it.y }))
        val pathfinder = RoutePathfinder(collision, 1, 0, RouteStrategy.NORMAL)
        return candidates.firstOrNull { stand ->
            pathfinder.find(passage.from, stand).type in setOf(PathResultType.COMPLETE, PathResultType.EMPTY) &&
                pathfinder.find(stand, passage.from).type in setOf(PathResultType.COMPLETE, PathResultType.EMPTY)
        }
    }

    private fun settled(bot: Bot): Boolean =
        bot.state == EntityState.ACTIVE && bot.isAlive && !bot.isLocked &&
            bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
}
