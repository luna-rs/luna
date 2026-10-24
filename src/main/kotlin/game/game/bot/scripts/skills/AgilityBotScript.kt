package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import api.bot.zone.SubZone
import game.skill.agility.AgilityCourse
import game.skill.agility.AgilityObstacle
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.`object`.GameObject
import com.google.gson.JsonObject
import game.skill.agility.Agility.completedObstacles
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Repeats a supported Agility course through its existing native object interactions.
 *
 * The shared zoned lifecycle supplies session expiry and travel fallback. Agility requires no banking
 * or production supplies. Only arrival at the expected landing tile after the native action unlocks
 * advances the lap: native handlers award XP and mark obstacles when submitting an action, which is
 * too early to prove traversal. Missing objects, failed approaches and unconfirmed landings share
 * three consecutive attempts before this course is rejected.
 *
 * Snapshots retain the next obstacle, a pending landing and the failure count. A paused or restored
 * native action is reconciled before another interaction. Verified earlier obstacles rebuild the
 * native, non-persistent lap markers so a restored partial lap can still receive its normal bonus.
 * No experience, items, movement animation or teleportation is manufactured here.
 *
 * @property course The native course and its forward obstacle metadata.
 * @param duration Remaining session time managed by the shared zoned lifecycle.
 * @author lare96
 */
class AgilityBotScript(bot: Bot, val course: AgilityBotCourse, duration: Duration) :
    ZonedBotScript(bot, duration, mutableListOf(course.zone)) {

    companion object {
        /**
         * Persistent partial-lap checkpoint; live objects and navigation are resolved again on resume.
         *
         * @author lare96
         */
        class AgilityData : ZonedBotScriptData() {
            /**
             * Supported course enum name.
             */
            var course = AgilityBotCourse.GNOME_STRONGHOLD.name
            /**
             * Index of the next obstacle; zero also identifies a newly completed lap.
             */
            var step = 0
            /**
             * Consecutive unsuccessful obstacle attempts, retained across script restarts.
             */
            var failures = 0
            /**
             * Expected landing for an interaction dispatched before the snapshot was taken.
             */
            var landing: Position? = null

            override fun load(data: JsonObject) {
                super.load(data)
                course = data.get("course").asString
                step = data.get("step").asInt
                failures = data.get("failures").asInt
                landing = data.getAsJsonObject("landing")?.let {
                    Position(it.get("x").asInt, it.get("y").asInt, it.get("z").asInt)
                }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("course", course)
                data.addProperty("step", step)
                data.addProperty("failures", failures)
                landing?.let { position ->
                    data.add("landing", JsonObject().also {
                        it.addProperty("x", position.x)
                        it.addProperty("y", position.y)
                        it.addProperty("z", position.z)
                    })
                }
            }
        }
    }

    /**
     * Next forward obstacle, rather than a cached world object that can disappear during logout.
     */
    internal var step = 0
        private set
    /**
     * Shared budget for consecutive search, approach, dispatch and landing failures.
     */
    internal var failures = 0
        private set
    /**
     * Landing checkpoint set before dispatch so a pause cannot lose an in-flight interaction.
     */
    internal var landing: Position? = null
        private set

    /**
     * Restores a saved course without retaining live object references or a navigation request.
     */
    constructor(bot: Bot, data: AgilityData) : this(bot, AgilityBotCourse.valueOf(data.course), data.duration) {
        step = data.step
        failures = data.failures
        landing = data.landing
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (step !in course.steps.indices || failures !in 0..2 || bot.skill(SKILL_AGILITY).level < course.course.level) {
            return false
        }
        if (bot.position !in course.zone.area && bot.position.z == 0) {
            step = 0
            landing = null
        }
        bot.completedObstacles.removeAll(course.course)
        course.steps.take(step).forEach { bot.completedObstacles.put(course.course, it.obstacle) }
        return true
    }

    override suspend fun onBankRequested(initial: Boolean) = false

    override suspend fun executeInZone(): Boolean {
        if (failures >= 3 || bot.skill(SKILL_AGILITY).level < course.course.level) return false
        // Native Agility actions own the player lock and strong queue until traversal finishes.
        if (!isSettled()) return true
        if (landing != null) return reconcileLanding()
        val next = course.steps[step]
        if (bot.position.z != next.plane) return failedAttempt()
        val obj = world.locator.findObjects(course.zone.area.centerPosition.setZ(next.plane), course.zone.area.tileRadius) {
            it.state == EntityState.ACTIVE && it.id in next.objectIds &&
                it.position.z == next.plane && it.position in course.zone.area
        }.minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return failedAttempt()
        val entry = next.approach(obj)
        if (entry != null && !walkTo(entry)) return failedAttempt()
        // Walking can suspend; never interrupt a native action which began while approaching.
        if (!isSettled()) return true
        if (bot.skill(SKILL_AGILITY).level < course.course.level) return false
        if (obj.state != EntityState.ACTIVE || bot.position.z != next.plane ||
            (entry != null && bot.position != entry)) return failedAttempt()
        landing = next.destination(obj, bot.position)
        if (!handler.interactions.interact(1, obj)) {
            // Navigation may have failed after the packet was sent. Preserve an active traversal.
            if (isSettled() && !hasLanded()) return failedAttempt()
        }
        waitFor(20.seconds) { hasLanded() }
        return if (isSettled()) reconcileLanding() else true
    }

    /**
     * Requires both the expected landing and completion of the native lock, action and walking queue.
     */
    internal fun hasLanded(): Boolean = landing != null && bot.position == landing && isSettled()

    /**
     * Distinguishes normal course animation locks from an idle bot ready for its next obstacle.
     */
    private fun isSettled(): Boolean = !bot.isLocked && bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty

    /**
     * Advances a verified landing once, or counts an idle interrupted traversal as an unsuccessful attempt.
     */
    internal fun reconcileLanding(): Boolean {
        if (!isSettled()) return true
        if (!hasLanded()) return failedAttempt()
        landing = null
        failures = 0
        bot.completedObstacles.put(course.course, course.steps[step].obstacle)
        step = (step + 1) % course.steps.size
        if (step == 0) bot.completedObstacles.removeAll(course.course)
        return true
    }

    /**
     * Consumes the common obstacle budget without skipping a step or creating progress.
     */
    internal fun failedAttempt(): Boolean {
        landing = null
        return ++failures < 3
    }

    /**
     * Reaches an exact obstacle entry through the web without accepting adjacency as arrival.
     */
    private suspend fun walkTo(tile: Position): Boolean = withTimeoutOrNull(30.seconds) {
        handler.webWalkToAny(listOf(tile)) && bot.position == tile && bot.walking.isEmpty
    } == true


    override fun snapshot(): AgilityData = AgilityData().also {
        it.course = course.name
        it.duration = duration
        it.zones = zones.toMutableList()
        it.step = step
        it.failures = failures
        it.landing = landing
    }
}

/**
 * Ordered native obstacles and local approach tiles for supported Agility courses.
 *
 * This describes obstacle interactions rather than long-distance routes. Each entry uses the normal
 * object option; the existing course handlers own animations, movement, requirements and experience.
 * Additional completed courses can supply their subzone and steps without another bot script.
 * The reverse Gnome rope (4059) is deliberately excluded because a lap crosses eastward.
 *
 * @property course Native course whose completed-obstacle bookkeeping is retained between sessions.
 * @property zone Course footprint used by the shared travel system and loaded-object search.
 * @property steps Ordered forward obstacles, including supported alternate branches and pipes.
 * @author lare96
 */
enum class AgilityBotCourse(val course: AgilityCourse, val zone: SubZone, val steps: List<AgilityStep>) {
    GNOME_STRONGHOLD(AgilityCourse.GNOME_STRONGHOLD, SubZone.GNOME_STRONGHOLD_AGILITY, listOf(
        AgilityStep(GNOME_LOG_BALANCE, 0, setOf(2295),
            approach = { Position(2474, 3436) }, destination = { _, _ -> Position(2474, 3429) }),
        AgilityStep(GNOME_OBSTACLE_NET, 0, setOf(2285),
            approach = { it.position.translate(0, 1, 0) }, destination = { _, start -> start.translate(0, -2, 1) }),
        AgilityStep(GNOME_TREE_BRANCH, 1, setOf(2313),
            destination = { _, _ -> Position(2473, 3420, 2) }),
        AgilityStep(GNOME_BALANCING_ROPE, 2, setOf(2312),
            destination = { _, _ -> Position(2483, 3420, 2) }),
        AgilityStep(GNOME_TREE_BRANCH_2, 2, setOf(2314, 2315),
            destination = { obj, _ -> if (obj.id == 2314) Position(2486, 3419) else Position(2487, 3420) }),
        AgilityStep(GNOME_OBSTACLE_NET_2, 0, setOf(2286),
            approach = { it.position.translate(0, -1, 0) }, destination = { _, start -> start.translate(0, 2, 0) }),
        AgilityStep(GNOME_OBSTACLE_PIPE, 0, setOf(154, 4058),
            approach = { Position(if (it.id == 154) 2484 else 2487, 3430) },
            destination = { obj, _ -> Position(if (obj.id == 154) 2484 else 2487, 3437) })
    ))
}

/**
 * One forward obstacle, with optional exact entry tile and the native action's expected landing tile.
 *
 * Nets calculate their landing relative to the player's entry, matching the existing handlers.
 * Exact approaches prevent a generic interaction from stopping on the wrong side of a log, net or pipe.
 *
 * @property obstacle Native completion marker used when restoring a verified partial lap.
 * @property plane Plane containing this obstacle's clickable object.
 * @property objectIds Loaded native objects accepted for this step.
 * @property approach Optional entry tile that must be reached before clicking.
 * @property destination Expected landing calculated from the object and actual entry tile.
 * @author lare96
 */
data class AgilityStep(val obstacle: AgilityObstacle, val plane: Int, val objectIds: Set<Int>,
                       val approach: (GameObject) -> Position? = { null },
                       val destination: (GameObject, Position) -> Position)
