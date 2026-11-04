package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.script.BotScript
import api.predef.world
import game.content.coalTrucks.CoalTrucks
import game.skill.mining.Pickaxe
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the personal coal-truck store through native item-on-object and Remove-coal clicks.
 * Full carts and normal mine departures haul real coal from the Seers depot to a native bank.
 * The Mining script retains its tool and targeting lifecycle; this helper never creates or transfers stock directly.
 *
 * A saved pending flag recovers an interrupted final withdrawal even after the cart became empty. Each haul has
 * at most six depot loads and a ten-minute deadline. Four minutes per bank route permits the ordinary gated walk
 * around Hemenster when the bot cannot use the log shortcut. Failed travel defers the cart while banking resumes.
 *
 * @author lare96
 */
internal class CoalTruckMining(
    private val bot: Bot,
    private val handler: BotActionHandler,
    private val canContinue: () -> Boolean
) {
    var haulPending = false
    var retryAt = 0L

    fun ready(): Boolean = System.currentTimeMillis() >= retryAt && valid()

    private fun valid(): Boolean = bot.state == EntityState.ACTIVE &&
        BotScript.ActionSafety.NON_COMBAT.allows(bot) && canContinue()

    private fun coal(): Int = bot.inventory.computeAmountForId(CoalTrucks.COAL)

    private fun truck(position: Position): GameObject? =
        world.locator.findObjects(position, 0) { it.id == CoalTrucks.TRUCK }.firstOrNull()

    private suspend fun approach(target: GameObject): Boolean =
        handler.interactions.approach(target, timeout = 60.seconds, allowOutOfView = true) { valid() }

    private fun defer(): Boolean {
        retryAt = System.currentTimeMillis() + 300_000
        return false
    }

    /**
     * Stores a full carried load at the mine, continuing mining while capacity remains.
     * A partial deposit that reaches capacity immediately starts the bounded town haul.
     */
    suspend fun handleFullInventory(): Boolean {
        if (!ready()) return false
        if (CoalTrucks.storedCoal(bot) >= CoalTrucks.CAPACITY) {
            haulPending = true
            return haul()
        }
        if (coal() == 0) return false
        val target = truck(CoalTrucks.MINE_TRUCK_POSITION) ?: return defer()
        val before = CoalTrucks.storedCoal(bot)
        val carried = coal()
        val stored = withTimeoutOrNull(70_000) {
            approach(target) && handler.inventory.useItem(CoalTrucks.COAL).onObject(target) { valid() } &&
                waitFor(5.seconds) { CoalTrucks.storedCoal(bot) > before && coal() < carried }
        } == true
        if (!stored) return defer()
        if (CoalTrucks.storedCoal(bot) >= CoalTrucks.CAPACITY) {
            haulPending = true
            return haul()
        }
        return !bot.inventory.isFull
    }

    /**
     * Banks carried coal first, then removes and banks each real depot load. Failed banking retains both the
     * native remaining cart stock and the pending flag, including the final load already in the inventory.
     */
    suspend fun haul(): Boolean {
        if (!ready()) return false
        haulPending = true
        val success = withTimeoutOrNull(600_000) {
            if (!bank()) return@withTimeoutOrNull false
            repeat(6) {
                if (CoalTrucks.storedCoal(bot) == 0) return@withTimeoutOrNull true
                if (!valid() || !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
                val target = truck(CoalTrucks.DEPOT_TRUCK_POSITION) ?: return@withTimeoutOrNull false
                val before = CoalTrucks.storedCoal(bot)
                val carried = coal()
                if (!approach(target) || !handler.interactions.interact(1, target) { valid() } ||
                    !waitFor(5.seconds) { CoalTrucks.storedCoal(bot) < before && coal() > carried } ||
                    !bank()) return@withTimeoutOrNull false
            }
            CoalTrucks.storedCoal(bot) == 0
        } == true
        val closed = withTimeoutOrNull(5_000) { handler.widgets.clickCloseInterface() } == true
        if (!success || !closed) return defer()
        haulPending = false
        retryAt = 0L
        return true
    }

    private suspend fun bank(): Boolean = withTimeoutOrNull(240_000) {
        if (!valid()) return@withTimeoutOrNull false
        val bank = handler.webWalkToNearestBank(fallback = false) ?: return@withTimeoutOrNull false
        if (!valid()) return@withTimeoutOrNull false
        if (!bot.bank.isOpen && (!handler.interactions.interact(2, bank) { valid() } ||
            !waitFor(5.seconds) { bot.bank.isOpen })) return@withTimeoutOrNull false
        if (!handler.banking.depositInventory(Pickaxe.ID_TO_PICKAXE.keys)) return@withTimeoutOrNull false
        valid() && coal() == 0 && !bot.inventory.isFull
    } == true
}
