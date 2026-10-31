package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.ZonedBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import api.bot.zone.SubZone
import api.predef.ext.allSuitableFood
import game.skill.agility.AgilityCourse
import game.skill.agility.AgilityObstacle
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.`object`.GameObject
import com.google.gson.JsonObject
import game.skill.agility.Agility.courseProgress
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
 * The shared zoned lifecycle supplies session expiry and travel fallback. Dangerous courses stock food.
 * Only arrival at the expected landing tile after the native action unlocks
 * advances the lap. Native handlers award XP and checkpoint progress after traversal finishes.
 * Missing objects, failed approaches and unconfirmed landings share
 * three consecutive attempts before this course is rejected.
 *
 * Snapshots retain the next obstacle, a pending landing and the failure count. A paused or restored
 * native action is reconciled before another interaction. Native lap markers are never reconstructed
 * from movement checkpoints, since failed obstacles do not count toward a completion bonus.
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
            var recovering = false

            override fun load(data: JsonObject) {
                super.load(data)
                course = data.get("course").asString
                step = data.get("step").asInt
                failures = data.get("failures").asInt
                recovering = data.get("recovering")?.asBoolean ?: false
                landing = data.getAsJsonObject("landing")?.let {
                    Position(it.get("x").asInt, it.get("y").asInt, it.get("z").asInt)
                }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("course", course)
                data.addProperty("step", step)
                data.addProperty("failures", failures)
                data.addProperty("recovering", recovering)
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

    internal var recovering = false
        private set

    /**
     * Restores a saved course without retaining live object references or a navigation request.
     */
    constructor(bot: Bot, data: AgilityData) : this(bot, AgilityBotCourse.valueOf(data.course), data.duration) {
        step = data.step
        failures = data.failures
        landing = data.landing
        recovering = data.recovering
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (step !in course.steps.indices || failures !in 0..2 || bot.skill(SKILL_AGILITY).level < course.course.level) {
            return false
        }
        val recoveredStep = course.recoveryStep(bot.position)
        if (recoveredStep != null) {
            step = recoveredStep
            recovering = true
            landing = null
        } else if (!recovering && landing == null && !course.zone.contains(bot.position) && bot.position.z == 0) {
            step = 0
        }
        // A saved movement index is not proof of successful native obstacles, especially after a fall.
        // Retain live native progress on pause; a fresh login earns its next bonus on a complete lap.
        return true
    }

    override suspend fun equipment() = null

    override suspend fun onBankRequested(initial: Boolean) =
        initial && course != AgilityBotCourse.GNOME_STRONGHOLD && !recovering &&
            bot.position.z == 0 && !course.zone.contains(bot.position) && !handler.inventory.hasAnyFood()

    override suspend fun onBankOpen(initial: Boolean) {
        if (!handler.banking.withdrawAnyFood(8)) {
            bot.preferences.allSuitableFood().forEach { bot.preferences.addWantedItem(it.id, 750) }
            stop()
        }
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (!isSettled()) return true
        if (landing != null) {
            if (!reconcileLanding()) stop()
            return true
        }
        if (recovering) {
            if (!recover()) {
                if (!failedAttempt()) stop()
            }
            return true
        }
        return false
    }

    override suspend fun executeInZone(): Boolean {
        if (failures >= 3 || bot.skill(SKILL_AGILITY).level < course.course.level) return false
        // Native Agility actions own the player lock and strong queue until traversal finishes.
        if (!isSettled()) return true
        if (landing != null) return reconcileLanding()
        if (course != AgilityBotCourse.GNOME_STRONGHOLD &&
            (bot.healthPercent <= 50 || bot.health <= 5)) {
            val health = bot.health
            if (!handler.inventory.eatAnyFood() || bot.health <= health) {
                bot.log("Agility supplies exhausted; ending the session before another damaging obstacle.")
                return false
            }
            return true
        }
        val next = course.steps[step]
        if (bot.position.z != next.plane) return failedAttempt()
        val obj = world.locator.findObjects(course.zone.area.centerPosition.setZ(next.plane), course.zone.area.tileRadius) {
            it.state == EntityState.ACTIVE && it.id in next.objectIds &&
                it.position.z == next.plane && course.zone.contains(it.position) && next.matches(it)
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
        waitFor(30.seconds) { hasLanded() || (isSettled() && course.recoveryStep(bot.position) != null) }
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
        if (!hasLanded()) {
            val recovery = course.recoveryStep(bot.position)
            if (recovery != null) {
                landing = null
                step = recovery
                recovering = true
                failures = 0
                return true
            }
            return failedAttempt()
        }
        landing = null
        failures = 0
        step = (step + 1) % course.steps.size
        if (step == 0) bot.courseProgress.remove(course.course)
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
        handler.webWalkTo(tile, bank = BankAccess.NONE) && bot.position == tile && bot.walking.isEmpty
    } == true

    /**
     * Climbs out through the real pit ladder, then approaches the next reachable obstacle.
     * Skipping a failed obstacle never awards a checkpoint or lap bonus.
     */
    private suspend fun recover(): Boolean {
        val ladder = course.pitLadder(bot.position)
        if (ladder != null) {
            if (!AgilityCourseTravel.climbOut(bot, ladder)) return false
        }
        val next = course.steps[step]
        val obj = world.locator.findObjects(course.zone.area.centerPosition.setZ(next.plane), course.zone.area.tileRadius) {
            it.state == EntityState.ACTIVE && it.id in next.objectIds && it.position.z == next.plane && next.matches(it)
        }.minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
        val entry = next.approach(obj) ?: return false
        if (!walkTo(entry)) return false
        recovering = false
        failures = 0
        return true
    }


    override fun snapshot(): AgilityData = AgilityData().also {
        it.course = course.name
        it.duration = duration
        it.zones = zones.toMutableList()
        it.step = step
        it.failures = failures
        it.landing = landing
        it.recovering = recovering
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
            approach = { Position(2473, 3423, 1) },
            destination = { _, _ -> Position(2473, 3420, 2) }),
        AgilityStep(GNOME_BALANCING_ROPE, 2, setOf(2312),
            approach = { Position(2477, 3420, 2) },
            destination = { _, _ -> Position(2483, 3420, 2) }),
        AgilityStep(GNOME_TREE_BRANCH_2, 2, setOf(2314, 2315),
            destination = { _, _ -> Position(2487, 3420) }),
        AgilityStep(GNOME_OBSTACLE_NET_2, 0, setOf(2286),
            approach = { it.position.translate(0, -1, 0) }, destination = { _, start -> start.translate(0, 2, 0) }),
        AgilityStep(GNOME_OBSTACLE_PIPE, 0, setOf(154, 4058),
            approach = { Position(if (it.id == 154) 2484 else 2487, 3430) },
            matches = { it.position.y == 3431 },
            destination = { obj, _ -> Position(if (obj.id == 154) 2487 else 2484, 3437) })
    )),
    BARBARIAN_OUTPOST(AgilityCourse.BARBARIAN_OUTPOST, SubZone.BARBARIAN_OUTPOST_AGILITY, listOf(
        AgilityStep(BARBARIAN_ROPESWING, 0, setOf(2282),
            approach = { Position(2551, 3554) }, destination = { _, _ -> Position(2551, 3549) }),
        AgilityStep(BARBARIAN_LOG_BALANCE, 0, setOf(2294),
            approach = { Position(2551, 3546) }, destination = { _, _ -> Position(2541, 3546) }),
        AgilityStep(BARBARIAN_OBSTACLE_NET, 0, setOf(2284),
            approach = { Position(2539, 3546) }, destination = { _, _ -> Position(2537, 3546, 1) }),
        AgilityStep(BARBARIAN_BALANCING_LEDGE, 1, setOf(2302),
            approach = { Position(2536, 3547, 1) }, destination = { _, _ -> Position(2532, 3546, 1) }),
        AgilityStep(BARBARIAN_LADDER, 1, setOf(3205),
            approach = { Position(2532, 3546, 1) }, destination = { _, _ -> Position(2532, 3546) }),
        *listOf(2536, 2539, 2542).map { x ->
            AgilityStep(BARBARIAN_CRUMBLING_WALL, 0, setOf(1948),
                approach = { Position(x - 1, 3553) },
                matches = { it.position == Position(x, 3553) },
                destination = { _, _ -> Position(x + 1, 3553) })
        }.toTypedArray()
    )),
    WILDERNESS(AgilityCourse.WILDERNESS, SubZone.WILDERNESS_AGILITY, listOf(
        AgilityStep(WILDERNESS_OBSTACLE_PIPE, 0, setOf(2288),
            approach = { Position(3004, 3937) }, matches = { it.position.y == 3938 },
            destination = { _, _ -> Position(3004, 3950) }),
        AgilityStep(WILDERNESS_ROPESWING, 0, setOf(2283),
            approach = { Position(3005, 3953) }, destination = { _, _ -> Position(3005, 3958) }),
        AgilityStep(WILDERNESS_STEPPING_STONE, 0, setOf(2311),
            approach = { Position(3002, 3960) }, destination = { _, _ -> Position(2996, 3960) }),
        AgilityStep(WILDERNESS_LOG_BALANCE, 0, setOf(2297),
            approach = { Position(3002, 3945) }, destination = { _, _ -> Position(2994, 3945) }),
        AgilityStep(WILDERNESS_ROCKS, 0, setOf(2328),
            approach = { it.position.translate(0, 1) }, destination = { obj, _ -> obj.position.translate(0, -3) })
    ));

    /**
     * Course failure landing regions, including saved bots already walking toward a pit ladder.
     */
    fun pitLadder(position: Position): Position? = when {
        this == BARBARIAN_OUTPOST && position.z == 0 && position.x in 2535..2555 && position.y in 9940..9955 ->
            Position(2547, 9951)
        this == WILDERNESS && position.z == 0 && position.x in 2990..3010 && position.y in 10340..10368 ->
            Position(3005, 10363)
        else -> null
    }

    /**
     * Chooses the next physically reachable step after a fall, without altering native lap progress.
     */
    fun recoveryStep(position: Position): Int? = when (this) {
        GNOME_STRONGHOLD -> null
        BARBARIAN_OUTPOST -> when {
            pitLadder(position) != null -> 1
            position == Position(2545, 3542) -> 2
            position == Position(2534, 3545) -> 5
            else -> null
        }
        WILDERNESS -> if (pitLadder(position) != null || position == Position(3002, 3963)) 2 else null
    }
}

/**
 * One forward obstacle, with optional exact entry tile and the native action's expected landing tile.
 *
 * Nets calculate their landing relative to the player's entry, matching the existing handlers.
 * Exact approaches prevent a generic interaction from stopping on the wrong side of a log, net or pipe.
 *
 * @property obstacle Native obstacle represented by this movement step.
 * @property plane Plane containing this obstacle's clickable object.
 * @property objectIds Loaded native objects accepted for this step.
 * @property approach Optional entry tile that must be reached before clicking.
 * @property destination Expected landing calculated from the object and actual entry tile.
 * @author lare96
 */
data class AgilityStep(val obstacle: AgilityObstacle, val plane: Int, val objectIds: Set<Int>,
                       val approach: (GameObject) -> Position? = { null },
                       val matches: (GameObject) -> Boolean = { true },
                       val destination: (GameObject, Position) -> Position)
