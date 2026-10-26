package game.bot.scripts

import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.rangingGuild.ArcheryCompetition
import game.content.rangingGuild.ArcheryCompetition.count
import game.content.rangingGuild.ArcheryRewards
import game.content.rangingGuild.RangingGuildGate
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture

/**
 * Plays one paid archery round with owned supplies, native dialogue, ranged clicks and ticket exchange actions.
 * Paid progress survives pauses and logout. Admission and reward attempts are persisted before dispatch so an
 * interrupted callback cannot charge a second entry or buy a second bundle. Native content owns all scoring.
 *
 * @author lare96
 */
class RangingGuildBotScript(bot: Bot, private val data: ArcheryData = ArcheryData()) : BotScript(bot) {
    companion object {
        private val gatePolicy = InteractionPolicy(InteractionType.SIZE, 2)
        private val shotPolicy = InteractionPolicy.STANDARD_LINE_OF_SIGHT

        internal fun inside(position: Position): Boolean = position.isWithinDistance(ArcheryCompetition.JUDGE, 20) &&
            (position.x > RangingGuildGate.POSITION.x || position.y < RangingGuildGate.POSITION.y)

        internal fun ownedBow(bot: Bot): Int? = (bot.equipment.asSequence() + bot.inventory.asSequence() + bot.bank.asSequence())
            .filterNotNull().firstOrNull { it.id in AmmoType.BRONZE_ARROW.def.weapons &&
                it.equipDef.meetsAllRequirements(bot) }?.id

        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) && bot.state == EntityState.ACTIVE &&
            bot.isAlive && !bot.inWilderness() && bot.ranged.level >= 40 &&
            (!bot.overlays.hasWindow() || bot.count > 0 && bot.overlays.getOverlay(ArcheryCompetition.Result::class.java) != null) &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            (bot.count == 11 || BotCombatActionHandler.mayGainExperience(bot, SKILL_RANGED) && ownedBow(bot) != null &&
                (bot.count in 1..10 || bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995) >= 200))

        internal fun reward(tickets: Int): ArcheryRewards.Reward? = ArcheryRewards.Reward.values()
            .filter { it.cost <= tickets }.maxByOrNull { it.cost }

        /**
         * Read-only lane planning uses the same projectile collision as the native Fire-at handler.
         */
        internal fun firingTiles(target: GameObject): List<Position> =
            if (target.id != 2513 || target.state != EntityState.ACTIVE || target.position !in ArcheryCompetition.targets)
                emptyList()
            else (2669..2678).flatMap { x -> (3416..3424).map { y -> Position(x, y) } }.filter {
                it.isWithinDistance(target.position, 10) && !it.isWithinDistance(target.position, 4) &&
                    world.collisionManager.raycast(it, target.position)
            }
    }

    /**
     * Bounds one visit across saves, disconnections and pauses without replacing native round state.
     *
     * @author lare96
     */
    class ArcheryData : BotScriptData() {
        var attempted = false
        var prepared = false
        var admissionAttempted = false
        var entered = false
        var rewardAttempted = false
        var deadline = System.currentTimeMillis() + 600_000
        var failures = 0
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean; prepared = data.get("prepared").asBoolean
            admissionAttempted = data.get("admissionAttempted").asBoolean; entered = data.get("entered").asBoolean
            rewardAttempted = data.get("rewardAttempted").asBoolean; deadline = data.get("deadline").asLong
            failures = data.get("failures").asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted); data.addProperty("prepared", prepared)
            data.addProperty("admissionAttempted", admissionAttempted); data.addProperty("entered", entered)
            data.addProperty("rewardAttempted", rewardAttempted); data.addProperty("deadline", deadline)
            data.addProperty("failures", failures)
        }
    }

    private var navigation: CompletableFuture<NavigationResult>? = null
    private var ownedDialogue: OptionDialogue? = null
    private var ownedResult: ArcheryCompetition.Result? = null
    private var ownedExchange: ArcheryRewards.Exchange? = null
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem) = false

    private fun live(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && !bot.combat.inCombat() &&
        !bot.inWilderness() && bot.controllers.primary.javaClass == PlayerController::class.java &&
        System.currentTimeMillis() < data.deadline
    private fun settled(): Boolean = !bot.isLocked && bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
    private suspend fun await(condition: () -> Boolean): Boolean = withTimeoutOrNull(8_000) {
        while (live() && !condition()) delay(100)
        live() && condition()
    } == true

    override suspend fun init(resumed: Boolean): Boolean {
        if (!live() || data.failures >= 3) return true
        ownedResult = bot.overlays.getOverlay(ArcheryCompetition.Result::class.java)
        if (ownedResult != null && bot.count > 0 && !handler.widgets.clickButton(446, 568)) return true
        if (data.prepared) return !inside(bot.position)
        if (data.attempted || !eligible(bot)) return true
        data.attempted = true
        data.entered = bot.count in 1..11
        return withTimeoutOrNull(180_000) {
            val bow = if (bot.count == 11) null else ownedBow(bot) ?: return@withTimeoutOrNull true
            if (inside(bot.position) && !gate(false)) return@withTimeoutOrNull true
            if (!handler.banking.travelToBankDepositAll() || !live()) return@withTimeoutOrNull true
            if (bow != null && bot.equipment.computeIdForIndex(Equipment.WEAPON) != bow &&
                !handler.banking.withdraw(Item(bow))) return@withTimeoutOrNull true
            val fee = if (bot.count == 0) 200 else 100
            if ((bot.count == 0 || bot.bank.computeAmountForId(995) >= fee) &&
                !handler.banking.withdraw(Item(995, fee))) return@withTimeoutOrNull true
            if (bot.count in 1..10 && bot.bank.contains(882) && !handler.banking.withdraw(Item(882, 10)))
                return@withTimeoutOrNull true
            if (bot.bank.contains(1464) && !handler.banking.withdrawAll(1464)) return@withTimeoutOrNull true
            if (!handler.widgets.clickCloseInterface() || !live()) return@withTimeoutOrNull true
            if (bow != null && bot.equipment.computeIdForIndex(Equipment.WEAPON) != bow && !handler.equipment.equip(bow))
                return@withTimeoutOrNull true
            if (!handler.webWalkTo(RangingGuildGate.OUTSIDE, bank = BankAccess.NONE) || !live() || !gate(true))
                return@withTimeoutOrNull true
            data.prepared = true
            false
        } ?: true
    }

    /**
     * Local movement cannot teleport across the guild wall and cancels only this script's pending navigation.
     */
    private suspend fun walk(tile: Position): Boolean {
        if (!live() || !settled()) return false
        if (bot.position == tile) return true
        val pending = bot.navigator.navigate(tile, true)
        navigation = pending
        return try {
            withTimeoutOrNull(15_000) {
                while (!pending.isDone && live() && !bot.isLocked) delay(100)
                live() && settled() && bot.position == tile
            } == true
        } finally { cancelNavigation(pending) }
    }

    internal suspend fun gate(entering: Boolean): Boolean {
        if (!live() || entering && bot.ranged.level < 40) return false
        val start = if (entering) RangingGuildGate.OUTSIDE else RangingGuildGate.INSIDE
        val end = if (entering) RangingGuildGate.INSIDE else RangingGuildGate.OUTSIDE
        if (!walk(start)) return false
        val door = world.locator.findObjects(RangingGuildGate.POSITION, 0) { it.id == 2514 }.firstOrNull() ?: return false
        handler.interactions.interact(1, door, gatePolicy) { live() && bot.position == start && settled() }
        return await { settled() && bot.position == end }
    }

    private suspend fun judge(option: String? = null): Boolean {
        if (!walk(ArcheryCompetition.JUDGE)) return false
        val judge = world.locator.findNpcs(ArcheryCompetition.JUDGE, 16) { it.id == 693 }.firstOrNull() ?: return false
        if (!handler.interactions.interact(1, judge) { live() && inside(bot.position) }) return false
        if (option == null) return await { bot.count == 0 && settled() }
        if (!await { bot.overlays.getOverlay(OptionDialogue::class.java)?.options?.contains(option) == true }) return false
        ownedDialogue = bot.overlays.getOverlay(OptionDialogue::class.java)
        if (!live() || !settled()) return false
        return handler.widgets.clickDialogueOption(option)
    }

    internal suspend fun fire(): Boolean {
        if (!live() || !settled() || bot.count !in 1..10 ||
            !BotCombatActionHandler.mayGainExperience(bot, SKILL_RANGED)) return false
        if (bot.equipment.computeIdForIndex(Equipment.WEAPON) !in AmmoType.BRONZE_ARROW.def.weapons) return false
        if (bot.equipment.computeIdForIndex(Equipment.AMMUNITION) != 882) {
            if (!bot.inventory.contains(882) && (!judge("Buy ten replacement arrows (100 coins)") ||
                !await { bot.inventory.contains(882) })) return false
            if (!handler.equipment.equip(882)) return false
        }
        val targets = world.locator.findObjects(ArcheryCompetition.JUDGE, 20) { it.id == 2513 }
        val lanes = targets.flatMap { target -> firingTiles(target).map { it to target } }
            .sortedBy { (tile, _) -> tile.computeLongestDistance(bot.position) }
        for ((tile, target) in lanes.take(3)) {
            if (!walk(tile)) continue
            val before = bot.count
            handler.interactions.interact(1, target, shotPolicy) {
                live() && settled() && bot.count == before && bot.position == tile &&
                    BotCombatActionHandler.mayGainExperience(bot, SKILL_RANGED)
            }
            val fired = await { bot.count > before && settled() }
            ownedResult = bot.overlays.getOverlay(ArcheryCompetition.Result::class.java)
            if (ownedResult != null) handler.widgets.clickButton(446, 568)
            return fired
        }
        return false
    }

    internal suspend fun redeem(): Boolean {
        if (data.rewardAttempted) return true
        data.rewardAttempted = true
        val reward = reward(bot.inventory.computeAmountForId(1464)) ?: return true
        val merchant = world.locator.findNpcs(ArcheryCompetition.JUDGE, 20) { it.id == 694 }.firstOrNull() ?: return false
        if (!handler.interactions.interact(1, merchant) { live() && settled() && inside(bot.position) } ||
            !await { bot.overlays.getOverlay(ArcheryRewards.Exchange::class.java) != null }) return false
        ownedExchange = bot.overlays.getOverlay(ArcheryRewards.Exchange::class.java)
        val before = bot.inventory.computeAmountForId(1464)
        if (!live() || !handler.widgets.clickContainerAction(11942, 12033, 2, reward.ordinal, reward.item)) return false
        val bought = await { bot.inventory.computeAmountForId(1464) == before - reward.cost }
        if (bot.overlays.getOverlay(ArcheryRewards.Exchange::class.java) === ownedExchange)
            handler.widgets.clickButton(11942, 12032)
        return bought
    }

    override suspend fun run(): Boolean {
        if (!live() || !data.prepared || !inside(bot.position) || data.failures >= 3) return true
        if (!settled()) { delay(100); return false }
        val success = when {
            bot.count == 0 && data.entered -> { redeem(); gate(false); return true }
            bot.count == 0 -> {
                if (data.admissionAttempted) return true
                data.admissionAttempted = true
                judge("Enter the competition (200 coins)") && await { bot.count == 1 }
            }
            bot.count == 11 -> judge()
            !BotCombatActionHandler.mayGainExperience(bot, SKILL_RANGED) -> { gate(false); return true }
            else -> fire()
        }
        if (bot.count in 1..11) data.entered = true
        data.failures = if (success) 0 else data.failures + 1
        if (!success) delay(600)
        return data.failures >= 3
    }

    private fun cancelNavigation(pending: CompletableFuture<NavigationResult>? = navigation) {
        if (pending != null && bot.navigator.currentPending === pending) { bot.navigator.cancel(); bot.walking.clear() }
        if (navigation === pending) navigation = null
    }

    override fun paused() { cancelNavigation() }
    override suspend fun finish() {
        cancelNavigation()
        if (ownedDialogue != null && bot.overlays.getOverlay(OptionDialogue::class.java) === ownedDialogue ||
            ownedResult != null && bot.overlays.getOverlay(ArcheryCompetition.Result::class.java) === ownedResult ||
            ownedExchange != null && bot.overlays.getOverlay(ArcheryRewards.Exchange::class.java) === ownedExchange)
            bot.overlays.closeWindows()
    }

    override fun snapshot() = ArcheryData().also {
        it.attempted = data.attempted; it.prepared = data.prepared; it.admissionAttempted = data.admissionAttempted
        it.entered = data.entered; it.rewardAttempted = data.rewardAttempted; it.deadline = data.deadline
        it.failures = data.failures
    }
}
