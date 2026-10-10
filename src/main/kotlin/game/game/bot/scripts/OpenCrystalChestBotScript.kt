package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import com.google.gson.JsonObject
import game.content.crystalChest.OpenCrystalChestAction
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Uses owned crystal keys on a loaded chest through the normal item-on-object interaction.
 *
 * Each cycle deposits the inventory, withdraws one unnoted key, and lets [OpenCrystalChestAction] handle locks,
 * object changes, key consumption, and reward rolls. The script waits for that action to finish before banking
 * the loot. One key per bank trip leaves room for rewards. Three failed cycles or session expiry end the task.
 * No key purchases, reward changes, or market-profit assumptions are introduced.
 *
 * Chest positions come from loaded world objects rather than hardcoded map coordinates. Saved state retains
 * the destination, remaining session time, and failure budget, while live objects are resolved again each cycle.
 *
 * @param bot The bot using the chest.
 * @property chestPosition The position of a loaded closed crystal chest.
 * @param duration Maximum time allowed for this session.
 * @author lare96
 */
class OpenCrystalChestBotScript(
    bot: Bot,
    val chestPosition: Position,
    private val duration: Duration
) : BotScript(bot) {
    companion object {
        /** Closed chest object registered by crystalChestHandler.kts. */
        const val CHEST = 172
        /** Crystal key consumed by the existing chest action. */
        const val KEY = 989
        /** Consecutive unsuccessful cycles before stopping. */
        private const val MAX_FAILURES = 3

        /**
         * Persistent destination, remaining session time, and retry state.
         *
         * @author lare96
         */
        class ChestData : BotScriptData() {
            /** Destination coordinates used to resolve the live chest again. */
            var position = Position(0, 0)
            /** Remaining session duration at the time of serialization. */
            var duration = Duration.ZERO
            /** Consecutive failed opening or banking cycles. */
            var failures = 0
            override fun load(data: JsonObject) {
                position = Position(data.get("x").asInt, data.get("y").asInt, data.get("z").asInt)
                duration = data.get("duration").asLong.milliseconds
                failures = data.get("failures")?.asInt ?: 0
            }
            override fun save(data: JsonObject) {
                data.addProperty("x", position.x)
                data.addProperty("y", position.y)
                data.addProperty("z", position.z)
                data.addProperty("duration", duration.inWholeMilliseconds)
                data.addProperty("failures", failures)
            }
        }
    }

    /** Monotonic elapsed time for this running instance. */
    private val started = TimeSource.Monotonic.markNow()
    /** Consecutive failed cycles, reset after verified key consumption and loot banking. */
    private var failures = 0

    /** Restores the destination, remaining session time, and exhausted retry budget. */
    constructor(bot: Bot, data: ChestData) : this(bot, data.position, data.duration) { failures = data.failures }

    /** Total key ownership across inventory and bank. */
    private fun keys() = bot.inventory.computeAmountForId(KEY).toLong() + bot.bank.computeAmountForId(KEY)

    /** Safety required before initiating a new opening or banking interaction. */
    private fun safe() = bot.health > 0 && !bot.isLocked && !bot.combat.inCombat() &&
        bot.actions.size(ActionType.STRONG) == 0

    override suspend fun run(): Boolean {
        if (failures >= MAX_FAILURES || started.elapsedNow() >= duration || keys() == 0L) return true
        if (!safe() || bot.actions.size(ActionType.WEAK) > 0) return false
        val success = withTimeoutOrNull(300_000) {
            val banking = bot.actionHandler.banking
            if (!banking.travelToBankDepositAll() || !safe()) return@withTimeoutOrNull false
            banking.clickBankingMode(false)
            if (!banking.withdrawAll(listOf(Item(KEY))) || !bot.inventory.contains(KEY)) return@withTimeoutOrNull false
            if (!bot.actionHandler.widgets.clickCloseInterface() || !safe()) return@withTimeoutOrNull false
            val chest = world.locator.findObjectsOnTile(chestPosition) { it.id == CHEST }.firstOrNull()
                ?: return@withTimeoutOrNull false
            if (!bot.actionHandler.inventory.useItem(KEY).onObject(chest)) return@withTimeoutOrNull false
            if (!waitFor(15.seconds) {
                KEY !in bot.inventory && !bot.isLocked && bot.actions.first(OpenCrystalChestAction::class.java) == null
            }) return@withTimeoutOrNull false
            safe() && banking.travelToBankDepositAll()
        } == true
        if (success) failures = 0 else failures++
        return failures >= MAX_FAILURES || (success && keys() == 0L)
    }

    override suspend fun finish() {
        bot.actions.first(OpenCrystalChestAction::class.java)?.takeIf {
            it.gameObject.position == chestPosition
        }?.interrupt()
    }

    override fun snapshot(): ChestData = ChestData().also {
        it.position = chestPosition
        it.duration = (duration - started.elapsedNow()).coerceAtLeast(Duration.ZERO)
        it.failures = failures
    }
}
