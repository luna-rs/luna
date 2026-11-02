package api.bot.webwalk.walk.crossing

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.walk.LegContext
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.CraftRuneBotScript
import game.content.abyss.AbyssObstacle
import game.content.abyss.AbyssPassage
import game.content.abyss.AbyssRift
import game.content.abyss.ObstacleAttempt
import game.content.sailing.Entrana
import game.skill.mining.Pickaxe
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses native Abyss interactions for prepared Runecrafting trips. Ordinary travel cannot choose the dangerous entry.
 *
 * @author lare96
 */
object AbyssCrossing : CrossingHandler {

    const val ID = "abyss"
    const val ENTRY_ALLOWED = "abyss_entry_allowed"
    const val LAW_ALLOWED = "abyss_law_allowed"
    const val MAGE = 2257

    /**
     * Bot option three sends Attack; option four dispatches the native npc3 Teleport interaction.
     */
    const val MAGE_OPTION = 4
    val INNER = Position(3039, 4834)

    /**
     * The specialized owner must still be running with its native combat guards, essence and forced-skull budget.
     * A paused parent underneath another activity cannot authorize its child's travel through the Abyss.
     */
    fun optedIn(bot: Bot): Boolean = bot.scriptStack.findActive(CraftRuneBotScript::class.java)
        .map { it.isRunning() && it.permitsAbyssEntry() }.orElse(false)

    /**
     * Matches the native law rift's inventory and equipment search, including its hand-armour exception.
     */
    fun lawAllowed(player: Player): Boolean = (player.inventory.filterNotNull() + player.equipment.filterNotNull())
        .none { Entrana.isForbidden(it) && it.equipDef?.index != Equipment.HANDS }

    override suspend fun cross(context: LegContext, leg: PlanLeg): String? {
        val action = leg.action ?: return "Abyss crossing has no action"
        if (action.npcId == MAGE) return enter(context, leg)
        val id = action.objectId ?: return "Abyss crossing has no object"
        if (AbyssPassage.entries.any { it.id == id }) return passage(context, leg)
        val rift = AbyssRift.entries.firstOrNull { it.id == id && it.destination != null }
            ?: return "unsupported Abyss object $id"
        if (context.arrived(leg, 0)) return null
        if (rift == AbyssRift.LAW && !lawAllowed(context.bot)) return "equipment is forbidden through the law rift"
        context.walk(leg.from, 0)?.let { return it }
        if (context.interrupted || !context.bot.isAlive || context.bot.state != EntityState.ACTIVE) {
            return "Abyss trip was interrupted"
        }
        val position = action.objectPosition ?: return "Abyss rift has no position"
        val obj = context.find(position, id) ?: return "Abyss rift $id is missing"
        if (!context.handler.interactions.interactOnLeg(1, obj) && !context.arrived(leg, 0)) {
            return "could not reach Abyss rift $id"
        }
        return if (context.awaitArrival(leg, 0)) null else "Abyss rift did not reach ${leg.to}"
    }

    private suspend fun enter(context: LegContext, leg: PlanLeg): String? {
        val bot = context.bot
        if (context.interrupted || !optedIn(bot)) return "this trip has not prepared Abyss entry"
        if (!SubZone.ABYSS.area.contains(bot.position)) {
            context.walk(leg.from, 0)?.let { return it }
            val mage = context.findNpc(leg.from, setOf(MAGE), 16) ?: return "the Abyss mage is missing"
            if (context.interrupted || !optedIn(bot)) return "the Abyss trip was interrupted"
            val clicked = context.handler.interactions.interactOnLeg(MAGE_OPTION, mage)
            if (!clicked && !SubZone.ABYSS.area.contains(bot.position)) return "could not reach the Abyss mage"
            if (!waitFor(15.seconds) { (!bot.isLocked && SubZone.ABYSS.area.contains(bot.position)) || !optedIn(bot) } ||
                !optedIn(bot) || !SubZone.ABYSS.area.contains(bot.position)) return "the Abyss mage did not teleport the bot"
        }
        // The native mage chooses a random outer-ring landing. Walk to the graph arrival without teleporting again.
        return context.walk(leg.to, 0)
    }

    private suspend fun passage(context: LegContext, leg: PlanLeg): String? {
        val bot = context.bot
        if (context.arrived(leg, 0)) return null
        val crossed = withTimeoutOrNull(120.seconds) {
            repeat(3) {
                if (context.interrupted || !optedIn(bot)) return@withTimeoutOrNull false
                if (AbyssPassage.entries.any { bot.position.isWithinDistance(it.inner, 1) }) {
                    return@withTimeoutOrNull context.walk(leg.to, 0) == null
                }
                val objects = bot.world.locator.findObjects(SubZone.ABYSS.inside, 64) { obj ->
                    AbyssPassage.entries.any { it.id == obj.id }
                }
                val obj = choosePassage(bot, objects) ?: return@withTimeoutOrNull false
                val passage = AbyssPassage.entries.first { it.id == obj.id }
                if (context.handler.interactions.interactOnLeg(1, obj) && waitFor(20.seconds) {
                        (!bot.isLocked && bot.position.isWithinDistance(passage.inner, 1)) || !optedIn(bot)
                    } && optedIn(bot) && !bot.isLocked && bot.position.isWithinDistance(passage.inner, 1)) {
                    return@withTimeoutOrNull context.walk(leg.to, 0) == null
                }
            }
            false
        } == true
        return if (crossed) null else "could not cross an Abyss passage within the retry allowance"
    }

    /**
     * Chooses the player's open native morph first, then a usable obstacle with the strongest current skill.
     */
    internal fun choosePassage(bot: Bot, objects: Iterable<GameObject>): GameObject? = objects.mapNotNull { obj ->
        if (AbyssPassage.entries.none { it.id == obj.id }) return@mapNotNull null
        val score = when (ObstacleAttempt.obstacleFor(bot, obj)) {
            AbyssObstacle.PASSAGE -> 1000
            AbyssObstacle.ROCK -> if (Pickaxe.computePickType(bot) != null) bot.mining.level else -1
            AbyssObstacle.TENDRILS -> if (Axe.computeAxeType(bot) != null) bot.woodcutting.level else -1
            AbyssObstacle.BOIL -> if (bot.inventory.contains(ObstacleAttempt.TINDERBOX)) bot.firemaking.level else -1
            AbyssObstacle.EYES -> bot.thieving.level
            AbyssObstacle.GAP -> bot.agility.level
            else -> -1
        }
        if (score < 0) null else obj to score
    }.sortedWith(compareByDescending<Pair<GameObject, Int>> { it.second }
        .thenBy { bot.position.computeLongestDistance(it.first.position) }).firstOrNull()?.first
}
