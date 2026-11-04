package game.bot.scripts.skills

import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.world
import api.predef.gameService
import com.google.common.util.concurrent.Service
import com.google.gson.JsonObject
import game.skill.agility.roguesDen.RoguesDen
import game.skill.agility.roguesDen.RoguesDen.denRun
import game.skill.agility.roguesDen.RoguesDenMap
import game.skill.agility.roguesDen.RoguesDenNpcs
import game.skill.agility.roguesDen.RoguesDenPuzzles
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration

/**
 * Banks admission equipment, obtains Brian's jewel and attempts the Rogues' Den maze through native interactions.
 * Searches spend real skill points; puzzles, guards, safes, rewards and failed-run XP remain owned by the maze.
 * Each visit has a wall-clock deadline and at most three attempts, with jewel exits before any banking.
 *
 * @author lare96
 */
class RoguesDenBotScript(bot: Bot, private val data: MazeData) : BotScript(bot) {

    constructor(bot: Bot, duration: Duration) : this(bot, MazeData().also {
        it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds.coerceIn(60_000, 3_600_000)
    })

    companion object {
        private val POWDER = setOf(Position(2969, 4999, 1), Position(2970, 5000, 1))
        private val TILE = setOf(Position(3018, 5080, 1))
        private val GUARD_POLICY = InteractionPolicy(InteractionType.LINE_OF_SIGHT, RoguesDenNpcs.GUARD_SIGHT)
        private val STOPPED_STATES = setOf(Service.State.STOPPING, Service.State.TERMINATED, Service.State.FAILED)
        private const val MAX_ATTEMPTS = 3

        /**
         * Uses base admission levels; drained levels inside an existing run do not invalidate that run.
         */
        fun eligible(bot: Bot): Boolean = bot.skill(Skill.AGILITY).staticLevel >= 50 &&
            bot.skill(Skill.THIEVING).staticLevel >= 50
    }

    /**
     * Persistent visit budget. Native runs are not reconstructed after logout; the maze's login recovery owns them.
     *
     * @author lare96
     */
    class MazeData : BotScriptData() {
        var deadline = 0L
        var attempts = 0
        var failures = 0
        var stage = 0
        var inAttempt = false
        var leaving = false

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
            attempts = data.get("attempts").asInt.coerceIn(0, MAX_ATTEMPTS)
            failures = data.get("failures").asInt.coerceIn(0, 3)
            stage = data.get("stage").asInt.coerceIn(0, 2)
            inAttempt = data.get("inAttempt").asBoolean
            leaving = data.get("leaving").asBoolean
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
            data.addProperty("attempts", attempts)
            data.addProperty("failures", failures)
            data.addProperty("stage", stage)
            data.addProperty("inAttempt", inAttempt)
            data.addProperty("leaving", leaving)
        }
    }

    private var ownedRun: RoguesDen.Run? = null
    private var entering = false
    private var navigation: CompletableFuture<NavigationResult>? = null
    private val route = ArrayDeque<RoguesDenBotRoute.Step>()
    private var routeGoal: Set<Position> = emptySet()
    private var safeGoals: Set<Position>? = null
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem) = false

    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat() &&
        gameService.state() !in STOPPED_STATES
    private fun ordinary() = bot.controllers.primary.javaClass == PlayerController::class.java
    private fun settled() = !bot.isLocked && bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
    private fun visiting() = live() && ownedRun != null && bot.denRun === ownedRun && !ownedRun!!.leaving
    private fun amount(id: Int) = bot.inventory.computeAmountForId(id)

    /**
     * All waits are cooperative, bounded, and reconciled against native session identity.
     */
    private suspend fun await(timeout: Long = 12_000, condition: () -> Boolean): Boolean =
        withTimeoutOrNull(timeout) {
            while (live() && !condition()) delay(100)
            live() && condition()
        } == true

    override suspend fun init(resumed: Boolean): Boolean {
        if (!eligible(bot) || !live()) return true
        val existing = bot.denRun
        if (existing != null) {
            // Only a persisted/resumed attempt may adopt a run; an injected new script cannot take over another one.
            if (!data.inAttempt || ownedRun != null && ownedRun !== existing) return true
            ownedRun = existing
        } else if (!ordinary()) return true
        return false
    }

    override suspend fun run(): Boolean {
        if (!live()) return true
        if (System.currentTimeMillis() >= data.deadline || data.attempts >= MAX_ATTEMPTS && !data.inAttempt)
            data.leaving = true
        if (bot.denRun == null) {
            if (RoguesDenMap.inBox(bot.position)) return true
            if (data.inAttempt) {
                data.inAttempt = false
                ownedRun = null
                route.clear()
                data.stage = 0
                data.failures = 0
                if (!settleDialogue()) return true
            }
            if (data.attempts >= MAX_ATTEMPTS || System.currentTimeMillis() >= data.deadline) data.leaving = true
            if (data.leaving) {
                if (ordinary()) handler.banking.travelToBankDepositAll()
                return true
            }
            if (!ordinary() || !prepareAndEnter()) return record(false)
            return false
        }
        if (!visiting()) {
            if (bot.denRun === ownedRun && ownedRun?.leaving == true) {
                await { bot.denRun == null }
                return false
            }
            return true
        }
        if (data.leaving) {
            if (leaveWithJewel()) {
                data.failures = 0
                return false
            }
            return ++data.failures >= 3
        }
        if (!settled()) { delay(100); return false }
        if (data.stage == 0 && amount(RoguesDen.FLASH_POWDER) >= 10 ||
            data.stage == 1 && amount(RoguesDen.RIGHT_TILE) > 0) {
            data.stage++
            route.clear()
        }
        val goals = when (data.stage) {
            0 -> POWDER
            1 -> TILE
            else -> safeStands()
        }
        if (bot.position in goals) {
            if (data.stage < 2) return record(take(if (data.stage == 0) RoguesDen.FLASH_POWDER else RoguesDen.RIGHT_TILE))
            val safe = world.locator.findObjects(bot.position, 2) {
                it.id == 7237 && world.collisionManager.reached(bot, it, InteractionPolicy.STANDARD_SIZE)
            }.firstOrNull() ?: return record(false)
            if (!output.sendObjectInteraction(1, safe)) return record(false)
            return record(await { bot.denRun == null })
        }
        if (route.isEmpty() || routeGoal != goals || route.first().from != bot.position) {
            val plan = RoguesDenBotRoute.geometry()?.plan(bot.position, goals,
                bot.skill(Skill.AGILITY).level, bot.skill(Skill.THIEVING).level,
                bot.skill(Skill.THIEVING).staticLevel >= 80) ?: return record(false)
            route.clear()
            route.addAll(plan.steps)
            routeGoal = goals
        }
        val edge = route.firstOrNull() ?: return record(false)
        if (!prepareStep(edge)) return record(false)
        if (!visiting()) return false
        val crossed = if (bot.position == edge.to) true else if (bot.position != edge.from) false else
            if (edge.objectId == -1) walk(edge.to) else cross(edge)
        if (crossed) route.removeFirst() else route.clear()
        return record(crossed)
    }

    /**
     * Admission is jewel-only, including worn equipment. All removals and deposits use their ordinary widgets.
     */
    private suspend fun prepareAndEnter(): Boolean = withTimeoutOrNull(
        minOf(180_000L, data.deadline - System.currentTimeMillis()).coerceAtLeast(1)) {
        if (!ordinary() || !eligible(bot)) return@withTimeoutOrNull false
        if (bot.equipment.size() > 0 || bot.inventory.any { it != null && it.id != RoguesDen.JEWEL }) {
            if (!handler.banking.travelToBankDepositAll() || !handler.widgets.clickCloseInterface() ||
                !handler.equipment.unequipAll() || !handler.banking.travelToBankDepositAll() ||
                !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
        }
        if (!handler.webWalkTo(RoguesDen.START, bank = BankAccess.NONE, fallback = false) || !live() || !ordinary())
            return@withTimeoutOrNull false
        if (amount(RoguesDen.JEWEL) == 0) {
            val brian = world.locator.findNpcs(RoguesDen.START, 32) { it.id == RoguesDen.BRIAN }.firstOrNull()
                ?: return@withTimeoutOrNull false
            if (!handler.interactions.interact(1, brian) { live() && ordinary() }) return@withTimeoutOrNull false
            if (!dialogueUntil { amount(RoguesDen.JEWEL) > 0 }) return@withTimeoutOrNull false
        }
        if (!handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
        val entrance = world.locator.findObjects(RoguesDen.START, 3) { it.id == 7256 }.firstOrNull()
            ?: return@withTimeoutOrNull false
        if (!walk(entrance.position) || !ordinary() || bot.equipment.size() != 0 ||
            bot.inventory.any { it != null && it.id != RoguesDen.JEWEL }) return@withTimeoutOrNull false
        if (!requestEntry(entrance) || !await { bot.denRun != null && settled() })
            return@withTimeoutOrNull false
        ownedRun = bot.denRun
        entering = false
        data.stage = 0
        data.failures = 0
        route.clear()
        true
    } == true

    /**
     * Owns admission intent before its packet can execute, including cancellation during the following wait.
     */
    internal fun requestEntry(entrance: GameObject): Boolean {
        if (entrance.id != 7256 || bot.position != entrance.position || bot.denRun != null || entering ||
            data.attempts >= MAX_ATTEMPTS) return false
        entering = true
        data.inAttempt = true
        data.attempts++
        if (output.sendObjectInteraction(1, entrance)) return true
        entering = false
        data.inAttempt = false
        data.attempts--
        return false
    }

    /**
     * Handles Brian's first/repeat admission pages and the jewel's exit confirmation through client dialogue packets.
     */
    private suspend fun dialogueUntil(done: () -> Boolean): Boolean = withTimeoutOrNull(20_000) {
        repeat(24) {
            if (!live()) return@withTimeoutOrNull false
            if (done()) return@withTimeoutOrNull true
            val options = bot.overlays.getOverlay(OptionDialogue::class.java)
            if (options != null) {
                val answer = listOf("Yes actually, what've you got?", "Ok that sounds good!",
                    "I want to try the maze again!", "Yes I'd like to leave!").firstOrNull { it in options.options }
                    ?: return@withTimeoutOrNull false
                if (!handler.widgets.clickDialogueOption(answer)) return@withTimeoutOrNull false
            } else if (bot.overlays.has(DialogueInterface::class.java)) output.sendContinueDialogue()
            delay(650)
        }
        done()
    } == true

    private suspend fun settleDialogue(): Boolean {
        if (!bot.overlays.has(DialogueInterface::class.java)) return true
        return dialogueUntil { !bot.overlays.has(DialogueInterface::class.java) }
    }

    /**
     * Disarms each threatened trap with Search; native controller immunity applies only to that actual click.
     * Throws owned powder while a guard is in the native line-of-sight range before taking another step.
     */
    private suspend fun prepareStep(edge: RoguesDenBotRoute.Step): Boolean {
        if (!visiting()) return false
        val nearby = world.locator.findNpcs(bot.position, RoguesDenNpcs.GUARD_SIGHT) {
            it.id in RoguesDenNpcs.GUARDS && !RoguesDenNpcs.isDazed(it)
        }
        val guards = nearby.filter { it.position.isWithinDistance(edge.to, RoguesDenNpcs.GUARD_SIGHT) &&
            world.collisionManager.reached(bot, it, GUARD_POLICY) }
        for (guard in guards) {
            val index = bot.inventory.computeIndexForId(RoguesDen.FLASH_POWDER)
            if (index < 0 || !output.useItemOnNpc(index, RoguesDen.FLASH_POWDER, guard) ||
                !await(5_000) { !visiting() || RoguesDenNpcs.isDazed(guard) }) return false
        }
        if (nearby.any { !RoguesDenNpcs.isDazed(it) && it.position.isWithinDistance(edge.to, 1) }) return false
        if (RoguesDenNpcs.bladeTouches(edge.to, listOf(edge.from, edge.to))) { delay(650); return false }
        for (trap in RoguesDenMap.traps[edge.to].orEmpty()) {
            val run = ownedRun ?: return false
            if (trap.obj.position in run.disarmed) continue
            if (!visiting() || !output.sendObjectInteraction(5, trap.obj) ||
                !await { !visiting() || settled() && trap.obj.position in run.disarmed }) return false
            if (!visiting()) return false
        }
        return visiting() && settled()
    }

    /**
     * A maze walking request is one cardinal, collision-validated edge; the navigator cannot cut across another room.
     */
    private suspend fun walk(tile: Position): Boolean {
        if (!live() || !settled()) return false
        if (bot.position == tile) return true
        val pending = bot.navigator.navigate(tile, false)
        navigation = pending
        return try {
            await { bot.position == tile && settled() || pending.isDone }
            bot.position == tile && settled()
        } finally {
            cancelNavigation()
        }
    }

    private suspend fun cross(edge: RoguesDenBotRoute.Step): Boolean {
        val obj = world.locator.findObjects(edge.objectTile!!, 0) {
            it.id == edge.objectId && it.state == EntityState.ACTIVE
        }.firstOrNull() ?: return false
        if (!output.sendObjectInteraction(1, obj)) return false
        if (obj.id == 7234) {
            if (!await(5_000) { !visiting() || bot.overlays.getOverlay(StandardInterface::class.java)?.id == RoguesDenPuzzles.MOSAIC })
                return false
            if (!visiting() || amount(RoguesDen.RIGHT_TILE) == 0 ||
                !handler.widgets.clickButton(RoguesDenPuzzles.MOSAIC, 13754)) return false
        } else if (obj.id == 7232) {
            if (!await(5_000) { !visiting() || bot.overlays.getOverlay(StandardInterface::class.java)?.id == RoguesDenPuzzles.ROTATION })
                return false
            val solution = ownedRun?.dials?.copyOf()?.let(RoguesDenBotRoute::solveDials) ?: return false
            for ((index, clockwise) in solution) {
                val before = ownedRun?.dials?.copyOf() ?: return false
                if (!visiting() || !handler.widgets.clickContainerAction(RoguesDenPuzzles.ROTATION,
                    RoguesDenPuzzles.ROTATION_SLOTS, if (clockwise) 1 else 2, index, 5572)) return false
                if (!await(5_000) { !visiting() || ownedRun?.dials?.contentEquals(before) == false }) return false
            }
        }
        return await(20_000) { !visiting() || bot.position == edge.to && settled() } && visiting() && bot.position == edge.to
    }

    private suspend fun take(id: Int): Boolean {
        val before = amount(id)
        val item = withTimeoutOrNull(12_000) {
            var found: GroundItem? = null
            while (visiting() && found == null) {
                found = world.locator.findItems(bot.position, 0) { it.id == id }.firstOrNull()
                if (found == null) delay(650)
            }
            found
        } ?: return false
        return visiting() && output.sendGroundItemInteraction(1, item) &&
            await(5_000) { !visiting() || amount(id) > before } && visiting() && amount(id) > before
    }

    private fun safeStands(): Set<Position> = safeGoals ?: world.locator.findObjects(Position(3016, 5048, 1), 8) { it.id == 7237 }
        .flatMap { safe -> (listOf(safe.position) + Direction.NESW.map { safe.position.translate(1, it) }).filter {
            world.collisionManager.reached(it, safe, InteractionPolicy.STANDARD_SIZE)
        } }.toSet().also { safeGoals = it }

    /**
     * An unsuccessful leg retries at most three times before taking the ordinary jewel exit.
     */
    private suspend fun record(success: Boolean): Boolean {
        data.failures = if (success) 0 else data.failures + 1
        if (!success) {
            route.clear()
            delay(650)
            if (data.failures >= 3) data.leaving = true
        }
        return data.leaving && bot.denRun == null
    }

    private suspend fun leaveWithJewel(): Boolean {
        if (!live() || bot.denRun !== ownedRun || ownedRun == null) return false
        cancelNavigation()
        if (ownedRun!!.leaving) return await { bot.denRun == null }
        if (!await { settled() || bot.denRun == null }) return false
        if (bot.denRun == null) return true
        val index = bot.inventory.computeIndexForId(RoguesDen.JEWEL)
        return index >= 0 && output.sendInventoryItemClick(1, index, RoguesDen.JEWEL) &&
            dialogueUntil { bot.denRun == null }
    }

    private fun cancelNavigation() {
        navigation?.let { if (bot.navigator.currentPending === it) { bot.navigator.cancel(); bot.walking.clear() } }
        navigation = null
    }

    override fun paused() {
        data.leaving = true
        cancelNavigation()
    }

    override suspend fun finish() {
        cancelNavigation()
        // Once ticks stop, persistence and the maze's native login recovery own the unfinished run.
        if (!live()) return
        if (entering || ownedRun != null && bot.denRun === ownedRun) withContext(NonCancellable) {
            withTimeoutOrNull(25_000) {
                if (entering && ownedRun == null) {
                    await(5_000) { bot.denRun != null }
                    ownedRun = bot.denRun
                    entering = false
                }
                if (ownedRun != null && bot.denRun === ownedRun) leaveWithJewel()
            }
        }
    }

    override fun snapshot() = MazeData().also {
        it.deadline = data.deadline
        it.attempts = data.attempts
        it.failures = data.failures
        it.stage = data.stage
        it.inAttempt = data.inAttempt
        it.leaving = data.leaving
    }
}
