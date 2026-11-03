package game.bot.scripts

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.combat.spells.IbanStaff
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Restores the existing personal Iban charge balance by using an owned staff on Underground Pass's native well.
 * Shared inventory production supplies banking, travel and persistent retries; progress is the real charge balance,
 * never a manufactured staff or a quest-state change. An already worn staff is unequipped through its normal click.
 *
 * @author lare96
 */
class RechargeIbanBotScript(bot: Bot, duration: Duration,
                          zones: MutableList<SubZone> = mutableListOf(SubZone.UNDERGROUND_PASS_IBAN_WELL)) :
    InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        val WELL_POSITION = Position(2373, 9718)

        /**
         * Retains duration, work zones and the generic interaction/banking failure budgets across saved sessions.
         *
         * @author lare96
         */
        class IbanRechargeData : InventoryScriptData()
    }

    constructor(bot: Bot, data: IbanRechargeData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Charge state belongs to the player; buying or moving another staff cannot replenish a depleted account.
     */
    fun isEligible(): Boolean = bot.itemTracker.contains(IbanStaff.STAFF) && IbanStaff.remaining(bot) < IbanStaff.CAPACITY

    override suspend fun equipment(): BotGearLocator? = null

    override suspend fun beforeInventoryInit(): Boolean {
        if (!isEligible()) return false
        if (bot.equipment[Equipment.WEAPON]?.id == IbanStaff.STAFF) {
            if (bot.inventory.isFull && !handler.banking.travelToBankDepositAll()) return false
            if (bot.bank.isOpen && !handler.widgets.clickCloseInterface()) return false
            if (!handler.equipment.unequip(Equipment.WEAPON) || IbanStaff.STAFF !in bot.inventory) return false
        }
        return true
    }

    override fun withdraw(): List<Item> = productionWithdraw(listOf(Item(IbanStaff.STAFF)), requestMissing = false)

    override fun bankWithdraw(): List<Item> = listOf(Item(IbanStaff.STAFF))

    override suspend fun onInventoryBankRequested(): Boolean {
        if (IbanStaff.remaining(bot) >= IbanStaff.CAPACITY) {
            stop()
            return false
        }
        return requestProductionBank(IbanStaff.STAFF in bot.inventory)
    }

    override suspend fun onExecuteInZone(): Boolean {
        val before = IbanStaff.remaining(bot)
        if (before >= IbanStaff.CAPACITY) {
            stop()
            return true
        }
        if (!productionReady(IbanStaff.STAFF in bot.inventory)) return true
        val well = world.locator.findObjectsOnTile(WELL_POSITION) {
            it.id == IbanStaff.WELL && it.state == EntityState.ACTIVE
        }.firstOrNull()
        attemptProduction(IbanStaff.STAFF, available = well != null,
            progressCondition = { IbanStaff.remaining(bot) > before }) {
            well != null && handler.inventory.useItem(IbanStaff.STAFF).onObject(well)
        }
        if (IbanStaff.remaining(bot) >= IbanStaff.CAPACITY) stop()
        return true
    }

    override fun snapshot() = IbanRechargeData().also { saveInventoryState(it) }
}
