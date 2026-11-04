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
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.agility.Agility.courseProgress
import game.skill.agility.pyramid.AgilityPyramid
import game.skill.agility.yanille.YanilleDungeon
import game.player.item.consume.potion.Potion
import game.skill.agility.skullball.Skullball
import game.skill.agility.pyramid.AgilityPyramid.pyramidTopTaken
import game.item.greegree.Greegree
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletableFuture
import java.util.ArrayDeque
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

    override val potionSkillId: Int get() = SKILL_AGILITY
    override val actionSafety: ActionSafety get() = if (course.isYanille) ActionSafety.COMBAT else ActionSafety.NON_COMBAT
    override val allowsCombatResponse: Boolean get() = !course.isYanille

    companion object {
        /**
         * Ninja greegrees accepted by the native Ape Atoll course; other monkey forms cannot enter its obstacles.
         */
        private val NINJA_GREEGREES = setOf(4024, 4025)

        /**
         * The Werewolf course's private lap item and the trainer who accepts it through the normal first option.
         */
        private const val WEREWOLF_STICK = 4179
        private const val STICK_TRAINER = 1664

        /**
         * Real carried cures protect the optional fall recovery through the poison-spider cave.
         */
        internal val YANILLE_ANTIPOISONS = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS,
            Potion.SUPER_ANTIPOISON, Potion.ANTIPOISON_POTION).flatMap { it.doses.reversed() }

        /**
         * Checks owned admission equipment and whether a fully healed bot can survive the course's largest fall.
         * The native handlers retain their level, quest and individual obstacle checks.
         */
        fun canTrain(bot: Bot, course: AgilityBotCourse): Boolean = when (course) {
            AgilityBotCourse.APE_ATOLL -> NINJA_GREEGREES.any { bot.itemTracker.count(it) > 0 }
            AgilityBotCourse.WEREWOLF -> bot.hitpoints.staticLevel > 45
            AgilityBotCourse.AGILITY_PYRAMID -> bot.hitpoints.staticLevel > 10
            else -> !course.isYanille || YANILLE_ANTIPOISONS.any { bot.itemTracker.count(it) > 0 }
        }

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
            /**
             * A completed lap still needs its ordinary trainer hand-in or pyramid-top sale.
             */
            var rewardPending = false

            override fun load(data: JsonObject) {
                super.load(data)
                course = data.get("course").asString
                step = data.get("step").asInt
                failures = data.get("failures").asInt
                recovering = data.get("recovering")?.asBoolean ?: false
                rewardPending = data.get("rewardPending")?.asBoolean ?: false
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
                data.addProperty("rewardPending", rewardPending)
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
     * Retains the native reward hand-in between the final landing and the next lap, including across pauses.
     */
    internal var rewardPending = false
        private set

    /**
     * Saved checkpoints take precedence over inferring an entry for a newly selected course visit.
     */
    private var restored = false

    /**
     * Restores a saved course without retaining live object references or a navigation request.
     */
    constructor(bot: Bot, data: AgilityData) : this(bot, AgilityBotCourse.valueOf(data.course), data.duration) {
        step = data.step
        failures = data.failures
        landing = data.landing
        recovering = data.recovering
        rewardPending = data.rewardPending
        restored = true
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        val escaping = course.isYanille && YanilleAgilityTraining.inSpiderCave(bot.position)
        if (step !in course.steps.indices || failures !in 0..2 || !escaping &&
            (bot.skill(SKILL_AGILITY).level < course.minimumLevel || !canTrain(bot, course))) {
            return false
        }
        if (escaping && (!handler.inventory.hasAnyFood() || YANILLE_ANTIPOISONS.none { bot.inventory.contains(it) }))
            forceBanking = true
        val recoveredStep = if (landing == null && !rewardPending) course.recoveryStep(bot.position) else null
        if (recoveredStep != null) {
            step = recoveredStep
            recovering = true
            landing = null
        } else if (!recovering && landing == null && !insideCourse() && bot.position.z == 0) {
            step = 0
        } else if (!restored && step == 0 && landing == null && !rewardPending && insideCourse()) {
            physicalCheckpoint()?.let { checkpoint ->
                step = checkpoint
                rewardPending = step == 0 && (course == AgilityBotCourse.WEREWOLF &&
                    bot.inventory.contains(WEREWOLF_STICK) || course == AgilityBotCourse.AGILITY_PYRAMID &&
                    bot.inventory.contains(AgilityPyramid.PYRAMID_TOP))
            }
        }
        // A saved movement index is not proof of successful native obstacles, especially after a fall.
        // Retain live native progress on pause; a fresh login earns its next bonus on a complete lap.
        return true
    }

    override suspend fun equipment() = null

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean =
        handler.webWalkTo(zone = zone, attempts = if (course.isYanille) 4 else 1,
            bank = bank, fallback = !course.isYanille)

    override suspend fun onBankRequested(initial: Boolean): Boolean {
        if (course.isYanille) {
            return !recovering && landing == null && (!handler.inventory.hasAnyFood() ||
                YANILLE_ANTIPOISONS.none { bot.inventory.contains(it) })
        }
        if (!initial || recovering || landing != null || rewardPending || bot.position.z != 0) return false
        val requested = when (course) {
            AgilityBotCourse.GNOME_STRONGHOLD -> false
            AgilityBotCourse.APE_ATOLL -> !NINJA_GREEGREES.any {
                bot.inventory.contains(it) || bot.equipment.contains(it)
            } || !handler.inventory.hasAnyFood()
            AgilityBotCourse.WEREWOLF -> bot.equipment[Equipment.HEAD] != null || !handler.inventory.hasAnyFood()
            else -> !handler.inventory.hasAnyFood()
        }
        if (requested && course == AgilityBotCourse.APE_ATOLL && bot.equipment.weapon?.id in NINJA_GREEGREES &&
            !handler.equipment.unequip(Equipment.WEAPON)) {
            stop()
            return false
        }
        return requested
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (course.isYanille && YANILLE_ANTIPOISONS.none { bot.inventory.contains(it) }) {
            val cure = YANILLE_ANTIPOISONS.firstOrNull { bot.bank.contains(it) }
            if (cure == null || !handler.banking.withdraw(Item(cure)) || !bot.inventory.contains(cure)) {
                val wanted = Potion.SUPER_ANTIPOISON.fourDose
                if (bot.preferences.getWantedItem(wanted) == null) bot.preferences.addWantedItem(wanted, 3)
                stop()
                return
            }
        }
        if (course == AgilityBotCourse.APE_ATOLL && bot.equipment.weapon?.id !in NINJA_GREEGREES) {
            val greegree = NINJA_GREEGREES.firstOrNull { bot.inventory.contains(it) || bot.bank.contains(it) }
            // Holding a greegree outside Ape Atoll or the zoo is rejected by the native equipment handler.
            if (greegree == null || (!bot.inventory.contains(greegree) && !handler.banking.withdraw(Item(greegree)))) {
                stop()
                return
            }
        }
        if (course == AgilityBotCourse.WEREWOLF && !handler.equipment.unequip(Equipment.HEAD)) {
            stop()
            return
        }
        if (!handler.banking.withdrawAnyFood(8)) {
            bot.preferences.allSuitableFood().forEach {
                if (!course.isYanille || bot.preferences.getWantedItem(it.id) == null)
                    bot.preferences.addWantedItem(it.id, 24)
            }
            stop()
        }
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (!isSettled()) return true
        if (landing != null) {
            if (!reconcileLanding()) stop()
            return true
        }
        if (rewardPending) {
            if (!handInReward() && !failedAttempt()) stop()
            return true
        }
        if (course.isYanille && bot.status.isPoisoned() && !bot.status.isAntiPoison()) {
            val cure = YANILLE_ANTIPOISONS.firstOrNull { bot.inventory.contains(it) }
            if (cure != null) {
                if (!handler.inventory.clickItem(1, cure) ||
                    !waitFor(5.seconds) { !bot.status.isPoisoned() || bot.status.isAntiPoison() }) {
                    if (!failedAttempt()) stop()
                }
                return true
            }
            forceBanking = true
        }
        if (recovering) {
            if (course.isYanille && bot.healthPercent <= 50 && handler.inventory.hasAnyFood()) {
                val health = bot.health
                if (handler.inventory.eatAnyFood() && bot.health > health) return true
                forceBanking = true
            }
            if (!recover()) {
                if (!failedAttempt()) stop()
            }
            return true
        }
        // The upper rubble landing is a separate cave copy, outside this variant's lower work zone.
        if (course == AgilityBotCourse.YANILLE_RUBBLE && !forceBanking && insideCourse()) {
            if (!executeInZone()) stop()
            return true
        }
        // The two upper Pyramid floors are on a separate native map copy. Finish those local steps before the
        // generic zoned lifecycle considers a return journey to the course's ground-floor arrival.
        if (course == AgilityBotCourse.AGILITY_PYRAMID && AgilityPyramid.levelAt(bot.position) >= 4) {
            if (!executeInZone()) stop()
            return true
        }
        return false
    }

    override suspend fun executeInZone(): Boolean {
        if (failures >= 3 || bot.skill(SKILL_AGILITY).level < course.minimumLevel) return false
        // Native Agility actions own the player lock and strong queue until traversal finishes.
        if (!isSettled()) return true
        if (landing != null) return reconcileLanding()
        if (bot.overlays.hasWindow() && !handler.widgets.clickCloseInterface()) return failedAttempt()
        val next = course.steps[step]
        val maximumHit = when {
            course == AgilityBotCourse.WEREWOLF && next.obstacle == WEREWOLF_ZIP_LINE -> 45
            course == AgilityBotCourse.AGILITY_PYRAMID -> 10
            course.isYanille -> YanilleDungeon.fallDamage(bot)
            else -> 5
        }
        if (course != AgilityBotCourse.GNOME_STRONGHOLD &&
            (bot.healthPercent <= 50 || bot.health <= maximumHit)) {
            val health = bot.health
            if (!handler.inventory.eatAnyFood() || bot.health <= health) {
                if (course.isYanille) {
                    forceBanking = true
                    return true
                }
                bot.log("Agility supplies exhausted; ending the session before another damaging obstacle.")
                return false
            }
            return true
        }
        if (course == AgilityBotCourse.APE_ATOLL && bot.equipment.weapon?.id !in NINJA_GREEGREES) {
            val greegree = NINJA_GREEGREES.firstOrNull { bot.inventory.contains(it) } ?: return false
            if (!Greegree.worksAt(bot.position) || !handler.equipment.equip(greegree)) return false
            return true
        }
        if (course == AgilityBotCourse.WEREWOLF && next.obstacle == WEREWOLF_ZIP_LINE &&
            !handler.equipment.unequip(Equipment.HEAD)) return failedAttempt()
        if (course == AgilityBotCourse.WEREWOLF && next.obstacle == WEREWOLF_SKULL_SLOPE &&
            !fetchStick()) return failedAttempt()
        if (next.obstacle == PYRAMID_TOP_ROCKS && bot.pyramidTopTaken) {
            landing = bot.position
            return reconcileLanding()
        }
        if (bot.position.z != next.plane) return failedAttempt()
        val obj = findObstacle(next) ?: return failedAttempt()
        val entry = next.approach(obj)
        if (bot.position != entry) {
            for (waypoint in next.waypoints) {
                if (!walkTo(waypoint)) return reconcileDisplacement()
            }
        }
        if (entry != null && !walkTo(entry)) return reconcileDisplacement()
        // Walking can suspend; never interrupt a native action which began while approaching.
        if (!isSettled()) return true
        if (bot.skill(SKILL_AGILITY).level < course.minimumLevel) return false
        if (obj.state != EntityState.ACTIVE || bot.position.z != next.plane ||
            (entry != null && bot.position != entry)) return failedAttempt()
        landing = next.destination(obj, bot.position) ?: return failedAttempt()
        val dispatchedAt = world.currentTick
        val dispatched = if (next.policy == null) handler.interactions.interact(1, obj)
            else handler.interactions.interact(1, obj, next.policy) { isActionSafe() }
        if (!dispatched) {
            // Navigation may have failed after the packet was sent. Preserve an active traversal.
            if (isSettled() && !hasLanded()) return failedAttempt()
        }
        waitFor(30.seconds) { hasLanded() || (world.currentTick > dispatchedAt && isSettled() &&
            course.recoveryStep(bot.position) != null) }
        return if (isSettled()) reconcileLanding() else true
    }

    /**
     * Requires both the expected landing and completion of the native lock, action and walking queue.
     */
    internal fun hasLanded(): Boolean = landing != null && bot.position == landing && isSettled() &&
        (course.steps[step].obstacle != PYRAMID_TOP_ROCKS || bot.pyramidTopTaken)

    /**
     * The Pyramid's upper copy is a separate activity footprint, rather than the desert between both map squares.
     */
    private fun workingArea() = if (course == AgilityBotCourse.AGILITY_PYRAMID &&
        bot.position in SubZone.AGILITY_PYRAMID_UPPER.area) SubZone.AGILITY_PYRAMID_UPPER.area else course.zone.area

    /**
     * Recognizes both parts of the Pyramid when a saved session already stands on an upper floor.
     */
    private fun insideCourse() = bot.position in course.zone.area ||
        course == AgilityBotCourse.AGILITY_PYRAMID && bot.position in SubZone.AGILITY_PYRAMID_UPPER.area ||
        course == AgilityBotCourse.YANILLE_RUBBLE && YanilleDungeon.besideRubbleDown(bot.position)

    /**
     * A long obstacle can have clickable parts at both ends. Prefer the part reachable from the declared forward
     * entry before distance, so an earlier walking checkpoint cannot select its backward-facing end.
     */
    private fun findObstacle(next: AgilityStep): GameObject? {
        val area = workingArea()
        val searchCenter = next.searchCenter ?: area.centerPosition.setZ(next.plane)
        return world.locator.findObjects(searchCenter, if (next.searchCenter == null) area.tileRadius else 5) {
            it.state == EntityState.ACTIVE && it.id in next.objectIds && it.position.z == next.plane &&
                (next.searchCenter != null || it.position in area) && next.matches(it)
        }.minWithOrNull(compareBy<GameObject> {
            if (world.collisionManager.reached(next.approach(it) ?: bot.position, it,
                    next.policy ?: InteractionPolicy.STANDARD_SIZE)) 0 else 1
        }.thenBy { it.position.computeLongestDistance(bot.position) })
    }

    /**
     * A new visit may follow an aborted one on an upper platform. Exact native entries and landings identify its
     * next physical step; this never reconstructs native obstacle credit, rewards or experience.
     */
    private fun physicalCheckpoint(): Int? {
        for ((index, next) in course.steps.withIndex()) {
            if (next.plane != bot.position.z) continue
            val obj = findObstacle(next) ?: continue
            if (next.approach(obj) == bot.position) return index
        }
        for ((index, previous) in course.steps.withIndex()) {
            val obj = findObstacle(previous) ?: continue
            val entry = previous.approach(obj) ?: continue
            if (previous.destination(obj, entry) == bot.position) return (index + 1) % course.steps.size
        }
        return null
    }

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
                if (course == AgilityBotCourse.WEREWOLF && bot.inventory.contains(WEREWOLF_STICK))
                    rewardPending = true
                return true
            }
            return failedAttempt()
        }
        landing = null
        failures = 0
        step = (step + 1) % course.steps.size
        if (step == 0) {
            bot.courseProgress.remove(course.course)
            rewardPending = course == AgilityBotCourse.WEREWOLF || course == AgilityBotCourse.AGILITY_PYRAMID
        }
        return true
    }

    /**
     * A rolling or sliding block may interrupt an ordinary approach as well as an explicit obstacle click.
     */
    private fun reconcileDisplacement(): Boolean {
        if (!isSettled()) {
            // A live Pyramid block can abort navigation before its fall reaches the lower floor.
            landing = null
            recovering = true
            return true
        }
        val recovery = course.recoveryStep(bot.position)
        if (recovery != null) {
            step = recovery
            landing = null
            recovering = true
            failures = 0
            return true
        }
        return failedAttempt()
    }

    /**
     * Collects only this bot's visible native stick. An expired throw loses the bonus, so the lap still finishes.
     */
    private suspend fun fetchStick(): Boolean {
        if (bot.inventory.contains(WEREWOLF_STICK)) return true
        val stick = world.locator.findItems(Position(3541, 9911), 5) {
            it.id == WEREWOLF_STICK && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
        }.firstOrNull() ?: return true
        return handler.loot.pickup(stick) { isActionSafe() && bot.position in course.zone.area }
    }

    /**
     * Uses the ordinary trainer option or item-on-Simon handler and confirms the actual native container change.
     */
    private suspend fun handInReward(): Boolean {
        val id = if (course == AgilityBotCourse.WEREWOLF) WEREWOLF_STICK else AgilityPyramid.PYRAMID_TOP
        if (!bot.inventory.contains(id)) {
            rewardPending = false
            failures = 0
            return true
        }
        val npcId = if (course == AgilityBotCourse.WEREWOLF) STICK_TRAINER else AgilityPyramid.SIMON
        val base = if (course == AgilityBotCourse.WEREWOLF) Position(3528, 9865) else AgilityPyramid.EXIT
        val npc = world.locator.findNpcs(base, 35) { it.id == npcId && it.state == EntityState.ACTIVE }.firstOrNull()
            ?: return false
        val expectedCoins = bot.inventory.computeAmountForId(AgilityPyramid.COINS).toLong() +
            bot.inventory.computeAmountForId(id).toLong() * AgilityPyramid.TOP_PRICE
        val dispatched = if (course == AgilityBotCourse.WEREWOLF) handler.interactions.interact(1, npc)
            else handler.inventory.useItem(id).onNpc(npc)
        if (!dispatched || !waitFor(5.seconds) { !bot.inventory.contains(id) &&
            (course == AgilityBotCourse.WEREWOLF ||
                bot.inventory.computeAmountForId(AgilityPyramid.COINS).toLong() >= expectedCoins) }) return false
        handler.widgets.clickCloseInterface()
        rewardPending = false
        failures = 0
        return true
    }

    override suspend fun beforeLeavingZone(): Boolean {
        if (!isSettled()) return false
        if (landing != null) return !reconcileLanding()
        if (course.isYanille && forceBanking && !recovering) return true
        if (rewardPending) {
            if (!handInReward()) return !failedAttempt()
            return false
        }
        // Expiry finishes the current lap through native actions. A fresh future session must not inherit an
        // upper-floor position with a ground-floor step zero. Missing supplies or the common failure budget still end it.
        if ((insideCourse() || course.isYanille && recovering) && (recovering || step != 0)) {
            if (recovering) {
                if (!recover()) return !failedAttempt()
            } else if (!executeInZone()) return true
            return false
        }
        if (course == AgilityBotCourse.APE_ATOLL && bot.equipment.weapon?.id in NINJA_GREEGREES &&
            !handler.equipment.unequip(Equipment.WEAPON)) return !failedAttempt()
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
    private suspend fun walkTo(tile: Position): Boolean {
        if (bot.position == tile && bot.walking.isEmpty) return true
        return withTimeoutOrNull(30.seconds) {
            handler.webWalkTo(tile, bank = BankAccess.NONE, fallback = !course.isYanille) &&
                bot.position == tile && bot.walking.isEmpty
        } == true
    }

    /**
     * Climbs out through the real pit ladder, then approaches the next reachable obstacle.
     * Skipping a failed obstacle never awards a checkpoint or lap bonus.
     */
    private suspend fun recover(): Boolean {
        course.recoveryStep(bot.position)?.let { step = it }
        if (course.isYanille && YanilleAgilityTraining.inSpiderCave(bot.position)) {
            if (!handler.inventory.hasAnyFood() || YANILLE_ANTIPOISONS.none { bot.inventory.contains(it) })
                forceBanking = true
            if (!AgilityCourseTravel.yanilleSpiderExit(bot)) return false
            if (forceBanking) {
                recovering = false
                failures = 0
                return true
            }
        }
        val ladder = course.pitLadder(bot.position)
        if (ladder != null) {
            if (!AgilityCourseTravel.climbOut(bot, ladder)) return false
        }
        val next = course.steps[step]
        val obj = findObstacle(next) ?: return false
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
        it.rewardPending = rewardPending
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
    )),
    APE_ATOLL(AgilityCourse.APE_ATOLL, SubZone.APE_ATOLL_AGILITY, listOf(
        AgilityStep(APE_STEPPING_STONE, 0, setOf(12568),
            approach = { Position(2755, 2742) }, destination = { _, _ -> Position(2753, 2742) }),
        AgilityStep(APE_TROPICAL_TREE, 0, setOf(12570),
            approach = { Position(2753, 2742) }, destination = { _, _ -> Position(2752, 2741, 2) }),
        AgilityStep(APE_MONKEY_BARS, 2, setOf(12573),
            approach = { Position(2752, 2741, 2) }, destination = { _, _ -> Position(2747, 2741) }),
        AgilityStep(APE_SKULL_SLOPE, 0, setOf(12576),
            approach = { Position(2747, 2741) }, destination = { _, _ -> Position(2744, 2741) }),
        AgilityStep(APE_ROPE, 0, setOf(12578),
            approach = { Position(2751, 2731) }, destination = { _, _ -> Position(2756, 2731) }),
        AgilityStep(APE_ZIP_VINE, 0, setOf(12618),
            approach = { Position(2757, 2733) }, destination = { _, _ -> Position(2770, 2747) })
    )),
    WEREWOLF(AgilityCourse.WEREWOLF, SubZone.WEREWOLF_AGILITY, werewolfBotSteps()),
    AGILITY_PYRAMID(AgilityCourse.AGILITY_PYRAMID, SubZone.AGILITY_PYRAMID, pyramidBotSteps()),
    YANILLE_LEDGE(AgilityCourse.YANILLE_DUNGEON, SubZone.YANILLE_AGILITY_LEDGE,
        YanilleAgilityTraining.ledge()),
    YANILLE_PIPE(AgilityCourse.YANILLE_DUNGEON, SubZone.YANILLE_AGILITY_PIPE,
        YanilleAgilityTraining.pipe()),
    YANILLE_BARS(AgilityCourse.YANILLE_DUNGEON, SubZone.YANILLE_AGILITY_BARS,
        YanilleAgilityTraining.bars()),
    YANILLE_RUBBLE(AgilityCourse.YANILLE_DUNGEON, SubZone.YANILLE_AGILITY_RUBBLE,
        YanilleAgilityTraining.rubble());

    /**
     * Per-obstacle dungeon admission is stricter than the shared native course's first level.
     */
    val minimumLevel: Int get() = maxOf(course.level, steps.maxOfOrNull { it.obstacle?.level ?: 0 } ?: 0)

    /**
     * These room crossings pay individual XP and never form a native bonus lap.
     */
    val isYanille: Boolean get() = course == AgilityCourse.YANILLE_DUNGEON

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
        APE_ATOLL -> when (position) {
            Position(2757, 2747) -> 0
            Position(2747, 2741) -> 3
            Position(2752, 2741) -> 1
            Position(2758, 2734) -> 5
            else -> null
        }
        WEREWOLF -> if (position in setOf(Position(3526, 9895), Position(3530, 9890), Position(3530, 9886))) 0 else null
        AGILITY_PYRAMID -> pyramidBotRecovery(position)
        YANILLE_LEDGE, YANILLE_PIPE, YANILLE_BARS, YANILLE_RUBBLE ->
            if (YanilleAgilityTraining.inSpiderCave(position)) 0 else null
    }
}

/**
 * Each Werewolf stone and hurdle is a native checkpoint interaction. The stick is collected beyond the pipes
 * before the slope; the trainer and reward handling remain in the common script lifecycle.
 */
private fun werewolfBotSteps(): List<AgilityStep> {
    val stones = listOf(Position(3538, 9873), Position(3538, 9875), Position(3538, 9877),
        Position(3540, 9877), Position(3540, 9879), Position(3540, 9881))
    return stones.zipWithNext().map { (from, to) ->
        AgilityStep(WEREWOLF_STEPPING_STONE, 0, setOf(5138), approach = { from },
            matches = { it.position == to }, destination = { _, _ -> to },
            policy = InteractionPolicy(InteractionType.DISTANCE, 2))
    } + listOf(9893, 9896, 9899).map { y ->
        AgilityStep(WEREWOLF_HURDLE, 0, setOf(5133), approach = { Position(3539, y - 1) },
            matches = { it.position == Position(3539, y) }, destination = { _, _ -> Position(3539, y + 1) })
    } + listOf(
        AgilityStep(WEREWOLF_PIPE, 0, setOf(5152), approach = { Position(3538, 9904) },
            matches = { it.position == Position(3538, 9905) }, destination = { _, _ -> Position(3538, 9910) }),
        AgilityStep(WEREWOLF_SKULL_SLOPE, 0, setOf(5136), approach = { Position(3533, 9910) },
            matches = { it.position == Position(3532, 9910) }, destination = { _, _ -> Position(3530, 9910) }),
        AgilityStep(WEREWOLF_ZIP_LINE, 0, setOf(5139), approach = { Position(3528, 9910) },
            matches = { it.position == Position(3528, 9911) }, destination = { _, _ -> Position(3528, 9871) })
    )
}

/**
 * Builds the Pyramid's clockwise route from native crossing metadata. Gold is collected on the western top
 * platform before the final jump separates it from the southern exit platform. Intermediate walking tiles
 * preserve the forward direction through rolling blocks and the two live sliding-block lanes.
 */
private fun pyramidBotSteps(): List<AgilityStep> {
    val stairs = listOf(Position(3354, 2831), Position(3356, 2833, 1), Position(3358, 2835, 2),
        Position(3360, 2837, 3), Position(3042, 4695, 2))
    val forward = mapOf(
        0 to listOf(Position(3354, 2840, 1), Position(3354, 2843, 1)),
        2 to listOf(Position(3375, 2849, 1), Position(3375, 2846, 1)),
        3 to listOf(Position(3375, 2837, 1), Position(3375, 2834, 1)),
        8 to listOf(Position(3367, 2849, 2), Position(3370, 2849, 2)),
        13 to listOf(Position(3365, 2847, 3), Position(3368, 2847, 3)),
        16 to listOf(Position(3048, 4701, 2), Position(3048, 4698, 2)),
        18 to listOf(Position(3043, 4699, 3), Position(3046, 4699, 3)))
    return buildList {
        for (level in 1..5) {
            val stairsPosition = stairs[level - 1]
            add(AgilityStep(null, stairsPosition.z, setOf(10857),
                approach = { obj ->
                    val desired = obj.position.translate(0, -1)
                    engine.obj.LadderDestination.climbingTiles(obj).filter { engine.obj.LadderDestination.canLand(it) }
                        .minByOrNull { it.computeLongestDistance(desired) }
                }, matches = { it.position == stairsPosition },
                destination = { obj, from -> engine.obj.StairDestination.destination(obj, from, true) }))
            if (level == 5) {
                add(AgilityStep(PYRAMID_TOP_ROCKS, 3, setOf(AgilityPyramid.TOP_ROCKS),
                    approach = { Position(3043, 4698, 3) },
                    matches = { it.position == Position(3043, 4698, 3) }, destination = { _, from -> from }))
            }
            for ((index, crossing) in AgilityPyramid.CROSSINGS.withIndex().filter { it.value.level == level }) {
                val ids = when (crossing.obstacle) {
                    PYRAMID_LOW_WALL -> setOf(AgilityPyramid.LOW_WALL)
                    PYRAMID_LEDGE -> AgilityPyramid.LEDGES.toSet()
                    PYRAMID_PLANK -> AgilityPyramid.PLANKS.toSet()
                    PYRAMID_CROSS_GAP -> AgilityPyramid.CROSS_GAPS.toSet()
                    else -> setOf(AgilityPyramid.JUMP_GAP)
                }
                add(AgilityStep(crossing.obstacle, crossing.starts.first().z, ids,
                    approach = { crossing.starts.first() }, matches = { it.position in crossing.locs },
                    destination = { _, from -> crossing.endFor(from) }, waypoints = forward[index].orEmpty()))
            }
        }
        add(AgilityStep(null, 3, setOf(10856), approach = { Position(3044, 4696, 3) },
            matches = { it.position == Position(3044, 4696, 3) }, destination = { _, _ -> AgilityPyramid.EXIT }))
    }
}

/**
 * Maps native failure landings onto the next forward route segment of the lower level. Every named landing
 * was checked against its cache walking component; replaying an earlier segment could cross a rolling block
 * backwards. This maps route position only and never supplies native obstacle or lap completion credit.
 */
private fun pyramidBotRecovery(position: Position): Int? = when (position) {
    Position(3352, 2846), Position(3365, 2853), Position(3377, 2843), Position(3370, 2830),
    Position(3362, 2830), Position(3377, 2835), Position(3377, 2847) -> 0
    Position(3355, 2839, 1), Position(3354, 2847, 1) -> 1
    Position(3362, 2851, 1) -> 2
    Position(3368, 2852, 1) -> 3
    Position(3374, 2839, 1), Position(3372, 2832, 1) -> 4
    Position(3364, 2831, 1) -> 5
    Position(3356, 2841, 2), Position(3357, 2845, 2) -> 8
    Position(3373, 2841, 2), Position(3366, 2850, 2) -> 10
    Position(3367, 2833, 2) -> 12
    Position(3358, 2841, 3) -> 15
    Position(3371, 2844, 3) -> 16
    Position(3371, 2839, 3) -> 17
    Position(3365, 2835, 3) -> 18
    Position(3049, 4697, 2), Position(3044, 4702, 2) -> 20
    else -> null
}

/**
 * One forward obstacle, with optional exact entry tile and the native action's expected landing tile.
 *
 * Nets calculate their landing relative to the player's entry, matching the existing handlers.
 * Exact approaches prevent a generic interaction from stopping on the wrong side of a log, net or pipe.
 *
 * @property obstacle Native obstacle represented by this step, or `null` for a native staircase or exit doorway.
 * @property plane Plane containing this obstacle's clickable object.
 * @property objectIds Loaded native objects accepted for this step.
 * @property approach Optional entry tile that must be reached before clicking.
 * @property destination Expected landing calculated from the object and actual entry tile.
 * @property waypoints Forward walking checkpoints between Pyramid hazards, omitted once recovery reaches the entry.
 * @property policy An explicit native reach policy for obstacles such as the Werewolf course's two-tile stones.
 * @property searchCenter An exact search anchor for a separate dungeon room outside the arrival subzone.
 * @author lare96
 */
data class AgilityStep(val obstacle: AgilityObstacle?, val plane: Int, val objectIds: Set<Int>,
                       val approach: (GameObject) -> Position? = { null },
                       val matches: (GameObject) -> Boolean = { true },
                       val destination: (GameObject, Position) -> Position?,
                       val waypoints: List<Position> = emptyList(),
                       val policy: InteractionPolicy? = null,
                       val searchCenter: Position? = null)

/**
 * Plays the native timed Skullball game through the Boss dialogue and actual Tap/Kick/Shoot interactions.
 * The native game alone tracks goals, elapsed ticks, the finish hole and XP. Saved scripts retain only their
 * activity budget and failures; login never recreates a lap or grants progress lost through native logout.
 *
 * @param bot The player of its own native ball.
 * @param duration Shared travel and play budget.
 * @author lare96
 */
class SkullballBotScript(bot: Bot, duration: Duration) :
    ZonedBotScript(bot, duration, mutableListOf(SubZone.WEREWOLF_SKULLBALL)) {
    companion object {
        /**
         * Native admission only; the existing game owns its Ring of Charos rule and current quest placeholder.
         */
        fun eligible(bot: Bot): Boolean = bot.agility.level >= Skullball.LEVEL && Skullball.passesAsWerewolf(bot)

        /**
         * No native lap, ball reference, timer or reward is serialized by the bot.
         *
         * @author lare96
         */
        class SkullballData : ZonedBotScriptData() {
            var failures = 0
            override fun load(data: JsonObject) {
                super.load(data)
                failures = data.get("failures")?.asInt ?: 0
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("failures", failures)
            }
        }
    }

    private var failures = 0
    private var observedGame: Skullball.Game? = null
    private var navigation: CompletableFuture<NavigationResult>? = null
    private var ownedOptions: OptionDialogue? = null
    private var stanceTiles: Set<Position> = emptySet()

    constructor(bot: Bot, data: SkullballData) : this(bot, data.duration) { failures = data.failures }

    override suspend fun equipment() = null
    override suspend fun onInit(resumed: Boolean): Boolean = eligible(bot) && failures < 3
    override suspend fun onBankRequested(initial: Boolean): Boolean = false

    override suspend fun executeInZone(): Boolean {
        if (!eligible(bot)) return false
        val game = Skullball.game(bot)
        if (game == null) {
            if (observedGame != null && bot.overlays.getOverlay(StandardInterface::class.java)?.id == Skullball.RESULT) {
                handler.widgets.clickCloseInterface()
                observedGame = null
                failures = 0
                return true
            }
            if (bot.overlays.hasWindow()) return record(false)
            return record(startGame())
        }
        observedGame = game
        if (game.finishIn > 0 || game.resultIn > 0 || game.moves > 0 || game.delay > 0) return true
        if (bot.position !in stanceTiles) stanceTiles = reachableStances()
        val shot = SkullballBotPlanner.plan(game.ball.position, game.goals,
            { from, direction -> Skullball.rollable(from, direction) },
            { stand -> stand in stanceTiles && !world.collisionManager.isBlocked(stand, false) })
            ?: return record(false)
        val ball = game.ball
        val before = ball.position
        if (!walk(shot.stand) || Skullball.game(bot) !== game || ball.position != before ||
            game.moves != 0 || !isActionSafe()) return record(false)
        val goals = game.goals
        if (!handler.interactions.interact(when (shot.shot) {
                Skullball.Shot.TAP -> 1
                Skullball.Shot.KICK -> 2
                Skullball.Shot.SHOOT -> 3
            }, ball) { isActionSafe() && Skullball.game(bot) === game && bot.position == shot.stand }) return record(false)
        return record(waitFor(12.seconds) {
            Skullball.game(bot) !== game || game.finishIn > 0 || game.resultIn > 0 ||
                game.moves == 0 && game.delay == 0 && (game.goals != goals || ball.position != before)
        })
    }

    /**
     * Selects only the Boss's actual start option; the ordinary dialogue reflex confirms his native player page.
     */
    private suspend fun startGame(): Boolean {
        val boss = world.locator.findNpcs(SubZone.WEREWOLF_SKULLBALL.inside, 20) {
            it.id == Skullball.BOSS && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        val response = "I would like to do the skullball course."
        if (!handler.interactions.interact(1, boss) { isActionSafe() && Skullball.game(bot) == null } ||
            !waitFor(6.seconds) { bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(response) == true })
            return false
        val page = bot.overlays.getOverlay(OptionDialogue::class.java) ?: return false
        ownedOptions = page
        if (!isActionSafe() || !handler.widgets.clickDialogueOption(response)) return false
        return waitFor(8.seconds) { Skullball.game(bot) != null }.also {
            if (it) observedGame = Skullball.game(bot)
        }
    }

    /**
     * Local native navigation reaches the exact side of the ball without a travel fallback or teleport.
     * Only this script's own pending navigator request may be cancelled on timeout or pause.
     */
    private suspend fun walk(tile: Position): Boolean {
        if (bot.position == tile) return true
        val pending = bot.navigator.navigate(tile, true)
        navigation = pending
        return try {
            withTimeoutOrNull(15.seconds) { pending.await() == NavigationResult.REACHED && bot.position == tile } == true
        } finally {
            if (navigation === pending) {
                if (bot.navigator.currentPending === pending) { bot.navigator.cancel(); bot.walking.clear() }
                navigation = null
            }
        }
    }

    /**
     * Static course walls can leave walkable pockets that the ball can reach but a player cannot. Compute the
     * bot's local cardinal walking component once, so planning never requires a stance inside such a pocket.
     */
    private fun reachableStances(): Set<Position> {
        val reached = hashSetOf(bot.position)
        val queue = ArrayDeque<Position>()
        queue.add(bot.position)
        val directions = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            for (direction in directions) {
                val next = from.translate(1, direction)
                if (Skullball.inside(next) && next !in reached &&
                    world.collisionManager.traversable(from, EntityType.PLAYER, direction)) {
                    reached.add(next)
                    queue.add(next)
                }
            }
        }
        return reached
    }

    private fun record(success: Boolean): Boolean {
        failures = if (success) 0 else failures + 1
        return failures < 3
    }

    override suspend fun beforeLeavingZone(): Boolean {
        val game = Skullball.game(bot)
        if (game == null) {
            if (observedGame != null && bot.overlays.getOverlay(StandardInterface::class.java)?.id == Skullball.RESULT)
                handler.widgets.clickCloseInterface()
            return true
        }
        if (game !== observedGame || failures >= 3 || game.started < 0) return true
        val lastUsefulTick = Skullball.FULL_XP_TICKS +
            kotlin.math.ceil(Skullball.FULL_XP / Skullball.XP_PER_TICK).toInt() + Skullball.FINISH_DELAY + Skullball.RESULT_DELAY
        if (world.currentTick - game.started > lastUsefulTick) return true
        return !executeInZone()
    }

    override suspend fun finish() {
        navigation?.let { if (bot.navigator.currentPending === it) { bot.navigator.cancel(); bot.walking.clear() } }
        navigation = null
        if (ownedOptions != null && bot.overlays.getOverlay(OptionDialogue::class.java) === ownedOptions)
            bot.overlays.closeWindows()
        val game = Skullball.game(bot)
        if (game != null && game === observedGame && game.finishIn == 0 && game.resultIn == 0 && !isPaused())
            Skullball.giveUp(bot)
    }

    override fun snapshot() = SkullballData().also {
        it.duration = duration
        it.zones = zones.toMutableList()
        it.failures = failures
    }
}

/**
 * Bounded shot search over the native ball's live position and next goal. It considers only unobstructed straight
 * native shot lengths and walkable kicking stances; source wall bounces remain native recovery rather than invented
 * movement. Every planned action is recalculated after the real ball settles.
 *
 * @author lare96
 */
internal object SkullballBotPlanner {
    /**
     * One ordinary interaction from an exact cardinal stance behind the ball.
     *
     * @author lare96
     */
    data class Kick(val stand: Position, val shot: Skullball.Shot)

    /**
     * Finds the first shot on a short route through the next source goal, or north into the final hole.
     * The caller supplies native collision checks so no remote occupancy knowledge or fabricated route is used.
     */
    fun plan(ball: Position, goal: Int, canRoll: (Position, Direction) -> Boolean,
             canStand: (Position) -> Boolean): Kick? {
        if (goal !in 0..Skullball.GOALS.size) return null
        val queue = ArrayDeque<Pair<Position, Kick?>>()
        val visited = hashSetOf(ball)
        queue.add(ball to null)
        val directions = listOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)
        while (queue.isNotEmpty() && visited.size <= 5000) {
            val (from, initial) = queue.removeFirst()
            for (direction in directions) {
                val stand = from.translate(1, direction.opposite())
                if (!canStand(stand)) continue
                for (shot in Skullball.Shot.entries.reversed()) {
                    val kick = initial ?: Kick(stand, shot)
                    var position = from
                    var scores = false
                    var clear = true
                    repeat(shot.squares) {
                        if (goal == Skullball.GOALS.size && position == Skullball.FINISH_FROM && direction == Direction.NORTH)
                            return kick
                        if (!clear || !canRoll(position, direction)) { clear = false; return@repeat }
                        position = position.translate(1, direction)
                        if (!Skullball.inside(position)) { clear = false; return@repeat }
                        if (goal < Skullball.GOALS.size && position == Skullball.GOALS[goal].middle &&
                            direction == Skullball.GOALS[goal].direction) scores = true
                    }
                    if (clear && scores) return kick
                    if (clear && visited.add(position)) queue.add(position to kick)
                }
            }
        }
        return null
    }
}
