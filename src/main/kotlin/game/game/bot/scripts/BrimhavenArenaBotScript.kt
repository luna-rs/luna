package game.bot.scripts

import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.brimhavenArena.ArenaObstacles
import game.content.brimhavenArena.BrimhavenArena
import game.content.brimhavenArena.BrimhavenArena.paid
import game.player.item.consume.food.Food
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture

/**
 * Pays for one bounded arena visit and follows moving pillars through native clicks and walking traps.
 * Food and the fee come from owned supplies. Failed crossings use real recovery ropes; native handlers own
 * tickets, damage, experience and rewards. Pauses retain admission and finish native actions, then leave on resume.
 *
 * @author lare96
 */
class BrimhavenArenaBotScript(bot: Bot, private val data: ArenaData = ArenaData()) : BotScript(bot) {
    companion object {
        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) &&
            bot.state == EntityState.ACTIVE && bot.isAlive && !bot.inWilderness() &&
            bot.controllers.primary.javaClass == PlayerController::class.java && !bot.overlays.hasWindow() &&
            (BrimhavenArena.inside(bot.position) && BrimhavenArena.session(bot) != null ||
                BrimhavenArena.session(bot) == null && bot.health >= 10 &&
                (bot.paid || bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995) >= 200) &&
                (bot.inventory.asSequence() + bot.bank.asSequence()).filterNotNull().any {
                    (Food.ID_TO_FOOD[it.id]?.heal ?: 0) >= 6
                })

        internal fun reward(tickets: Int): BrimhavenArena.Reward? = when {
            tickets >= 10 -> BrimhavenArena.Reward.XP_TEN
            tickets > 0 -> BrimhavenArena.Reward.XP_ONE
            else -> null
        }
    }

    /**
     * Keeps admission attempts, elapsed budgets and earned-ticket accounting across saves and pauses.
     *
     * @author lare96
     */
    class ArenaData : BotScriptData() {
        var attempted = false
        var entered = false
        var leaving = false
        var deadline = System.currentTimeMillis() + 900_000
        var lastTag = System.currentTimeMillis()
        var failures = 0
        var initialTickets = 0
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            entered = data.get("entered").asBoolean
            leaving = data.get("leaving").asBoolean
            deadline = data.get("deadline").asLong
            lastTag = data.get("lastTag").asLong
            failures = data.get("failures").asInt
            initialTickets = data.get("initialTickets").asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted); data.addProperty("entered", entered)
            data.addProperty("leaving", leaving); data.addProperty("deadline", deadline)
            data.addProperty("lastTag", lastTag); data.addProperty("failures", failures)
            data.addProperty("initialTickets", initialTickets)
        }
    }

    private var ownedSession: BrimhavenArena.Session? = null
    private var navigation: CompletableFuture<NavigationResult>? = null
    private var ownedDialogue: OptionDialogue? = null
    private var ownedRewards: BrimhavenArena.Rewards? = null
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem) = false

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat() &&
        bot.controllers.primary.javaClass == PlayerController::class.java
    private fun settled(): Boolean = !bot.isLocked && bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
    private fun visiting(): Boolean = live() && ownedSession != null && BrimhavenArena.session(bot) === ownedSession &&
        BrimhavenArena.inside(bot.position) && System.currentTimeMillis() < data.deadline + 120_000
    private fun objects(plane: Int) = world.locator.findObjects(BrimhavenArena.tile(31, 31, plane), 33) {
        it.state == EntityState.ACTIVE && BrimhavenArena.inside(it.position) && it.position.z == plane
    }

    /**
     * Waits on observed native state with a real deadline and cooperative cancellation.
     */
    private suspend fun await(timeout: Long = 20_000, condition: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !condition()) delay(100)
        live() && condition()
    } == true

    override suspend fun init(resumed: Boolean): Boolean {
        if (!live() || System.currentTimeMillis() >= data.deadline + 120_000) return true
        if (BrimhavenArena.inside(bot.position)) {
            val current = BrimhavenArena.session(bot) ?: return true
            if (ownedSession != null && current !== ownedSession) return true
            ownedSession = current
            if (!data.entered) data.initialTickets = bot.inventory.computeAmountForId(BrimhavenArena.TICKET)
            data.attempted = true; data.entered = true; data.leaving = true
            return false
        }
        if (data.entered && data.leaving && bot.position.isWithinDistance(BrimhavenArena.ENTRANCE, 16)) return false
        if (data.attempted || System.currentTimeMillis() >= data.deadline || !eligible(bot)) return true
        data.attempted = true
        return withTimeoutOrNull(minOf(180_000, data.deadline - System.currentTimeMillis())) {
            if (!handler.banking.travelToBankDepositAll() ||
                !handler.banking.withdrawAnyFood(12, minimumHeal = 6, retry = false) ||
                (!bot.paid && !handler.banking.withdraw(Item(995, 200))) ||
                !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull true
            if (!handler.webWalkTo(BrimhavenArena.ENTRANCE, bank = BankAccess.NONE) || !live() ||
                System.currentTimeMillis() >= data.deadline) return@withTimeoutOrNull true
            if (!bot.paid) {
                val clerk = world.locator.findNpcs(BrimhavenArena.ENTRANCE, 16) { it.id == 437 }.firstOrNull()
                    ?: return@withTimeoutOrNull true
                if (!handler.interactions.interact(1, clerk) { live() && !BrimhavenArena.inside(bot.position) } ||
                    !await { bot.overlays.getOverlay(OptionDialogue::class.java) != null }) return@withTimeoutOrNull true
                ownedDialogue = bot.overlays.getOverlay(OptionDialogue::class.java)
                if (!handler.widgets.clickDialogueOption("Pay 200 coins to enter") || !await { bot.paid })
                    return@withTimeoutOrNull true
            }
            data.initialTickets = bot.inventory.computeAmountForId(BrimhavenArena.TICKET)
            val ladder = world.locator.findObjects(Position(2809, 3194), 0) { it.id == 3617 }.firstOrNull()
                ?: return@withTimeoutOrNull true
            handler.interactions.interact(1, ladder) { live() && bot.paid && System.currentTimeMillis() < data.deadline }
            if (!await { settled() && BrimhavenArena.session(bot) != null }) return@withTimeoutOrNull true
            ownedSession = BrimhavenArena.session(bot)
            data.entered = true; data.lastTag = System.currentTimeMillis()
            false
        } ?: true
    }

    /**
     * Local collision navigation keeps each planned crossing on its own platform and never uses a teleport.
     * Only this request may be cancelled; native agility movement and another script's navigation are preserved.
     */
    private suspend fun walk(tile: Position, allowed: () -> Boolean): Boolean {
        if (!visiting() || !settled() || !allowed()) return false
        if (bot.position == tile) return true
        val pending = bot.navigator.navigate(tile, true)
        navigation = pending
        return try {
            withTimeoutOrNull(15_000) {
                while (!pending.isDone && visiting() && !bot.isLocked && allowed()) delay(100)
                bot.position == tile && settled() && allowed()
            } == true
        } finally {
            if (navigation === pending) {
                if (bot.navigator.currentPending === pending) { bot.navigator.cancel(); bot.walking.clear() }
                navigation = null
            }
        }
    }

    /**
     * Executes one edge and checks the actual landing, including native falls and trap knockbacks.
     */
    internal suspend fun cross(edge: BrimhavenArenaRoute.Crossing, goal: Int): Boolean {
        fun permitted() = visiting() && (data.leaving || BrimhavenArena.targetIndex == goal)
        if (!walk(edge.start, ::permitted) || !permitted()) return false
        if (edge.obj != null) {
            handler.interactions.interact(1, edge.obj, ArenaObstacles.policy(edge.obj)) { permitted() && settled() }
        } else walk(edge.trigger, ::permitted)
        await(30_000) { !visiting() || settled() && (bot.position != edge.start || !permitted()) }
        return visiting() && settled() && (bot.position == edge.end || bot.z == 0)
    }

    internal suspend fun recover(): Boolean {
        if (!visiting() || bot.z != 0) return false
        val ropes = objects(0).filter { it.id == 3610 }.sortedBy { it.position.computeLongestDistance(bot.position) }
        for (rope in ropes.take(3)) {
            val landing = rope.position.translate(0, 0, 3)
            handler.interactions.interact(1, rope) { visiting() && settled() && bot.z == 0 }
            if (await(10_000) { !visiting() || settled() && bot.z == 3 }) return visiting() && bot.position == landing
            if (!visiting()) return false
        }
        return false
    }

    override suspend fun run(): Boolean {
        if (!live() || System.currentTimeMillis() >= data.deadline + 120_000 || data.failures >= 5) return true
        if (!BrimhavenArena.inside(bot.position)) return redeem()
        if (!visiting()) return true
        if (!settled()) { delay(100); return false }
        val earned = bot.inventory.computeAmountForId(BrimhavenArena.TICKET) - data.initialTickets
        if (System.currentTimeMillis() >= data.deadline || System.currentTimeMillis() - data.lastTag >= 180_000 ||
            earned >= 10 || !handler.inventory.hasAnyFood()) data.leaving = true
        if (bot.health <= maxOf(6, bot.totalHealth / 2)) {
            if (!handler.inventory.eatAnyFood()) return true
            return false
        }
        if (bot.z == 0) return record(recover())
        val from = BrimhavenArenaRoute.platform(bot.position) ?: return record(false)
        val goal = if (data.leaving) 24 else BrimhavenArena.targetIndex
        if (from == goal) {
            if (data.leaving) {
                val ladder = objects(3).firstOrNull { it.id == 3618 } ?: return record(false)
                handler.interactions.interact(1, ladder) { visiting() && settled() }
                return record(await { !BrimhavenArena.inside(bot.position) && settled() })
            }
            val session = ownedSession!!
            if (session.tagged) { delay(600); return false }
            val pillar = objects(3).firstOrNull { it.id in setOf(3608, 3581) && it.position == BrimhavenArena.target }
                ?: return record(false)
            handler.interactions.interact(1, pillar) { visiting() && settled() && BrimhavenArena.targetIndex == goal }
            val tagged = await(5_000) { session.tagged || BrimhavenArena.targetIndex != goal || !visiting() }
            if (session.tagged) data.lastTag = System.currentTimeMillis()
            return record(tagged)
        }
        val edges = BrimhavenArenaRoute.crossings(bot, objects(3))
        val edge = BrimhavenArenaRoute.path(from, goal, edges)?.firstOrNull() ?: return record(false)
        val crossed = cross(edge, goal)
        return record(crossed || !data.leaving && BrimhavenArena.targetIndex != goal)
    }

    private suspend fun record(success: Boolean): Boolean {
        data.failures = if (success) 0 else data.failures + 1
        if (!data.leaving && data.failures >= 3) { data.leaving = true; data.failures = 0 }
        if (!success) delay(600)
        return data.failures >= 5
    }

    /**
     * Spends only the visit's carried tickets through Jackie's native reward buttons.
     */
    private suspend fun redeem(): Boolean {
        if (!data.entered || !data.leaving || BrimhavenArena.session(bot) != null ||
            !bot.position.isWithinDistance(BrimhavenArena.ENTRANCE, 16)) return true
        val reward = reward(bot.inventory.computeAmountForId(BrimhavenArena.TICKET) - data.initialTickets) ?: return true
        if (ownedRewards == null || bot.overlays.getOverlay(BrimhavenArena.Rewards::class.java) !== ownedRewards) {
            val trader = world.locator.findNpcs(BrimhavenArena.ENTRANCE, 16) { it.id == 1055 }.firstOrNull() ?: return true
            handler.interactions.interact(1, trader) { live() && settled() && !BrimhavenArena.inside(bot.position) }
            if (!await(5_000) { bot.overlays.getOverlay(BrimhavenArena.Rewards::class.java) != null }) return true
            ownedRewards = bot.overlays.getOverlay(BrimhavenArena.Rewards::class.java)
        }
        val before = bot.inventory.computeAmountForId(BrimhavenArena.TICKET)
        if (!handler.widgets.clickButton(8292, reward.buttons.first())) return true
        return !await(5_000) { bot.inventory.computeAmountForId(BrimhavenArena.TICKET) == before - reward.cost }
    }

    override fun paused() {
        data.attempted = true
        data.leaving = true
        cancelNavigation()
    }

    private fun cancelNavigation() {
        navigation?.let { if (bot.navigator.currentPending === it) { bot.navigator.cancel(); bot.walking.clear() } }
        navigation = null
    }

    override suspend fun finish() {
        cancelNavigation()
        if (ownedDialogue != null && bot.overlays.getOverlay(OptionDialogue::class.java) === ownedDialogue ||
            ownedRewards != null && bot.overlays.getOverlay(BrimhavenArena.Rewards::class.java) === ownedRewards)
            bot.overlays.closeWindows()
    }

    override fun snapshot() = ArenaData().also {
        it.attempted = data.attempted; it.entered = data.entered; it.leaving = data.leaving
        it.deadline = data.deadline; it.lastTag = data.lastTag; it.failures = data.failures
        it.initialTickets = data.initialTickets
    }
}
