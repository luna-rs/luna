package game.bot.scripts

import api.bot.action.BotNpcDialogues
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.world
import com.google.gson.JsonObject
import game.content.randomEvents.WoodcuttingRandomEvents
import game.content.randomEvents.WoodcuttingRandomEvents.Parts
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Restores owned axe heads and handles or pays Bob's native broken-axe repair price.
 * Both conversions use normal player interactions; source repair metadata supplies every tier and price.
 * Head assembly uses ordinary stationary areas, while broken tools stage at Lumbridge before approaching Bob.
 * No axes, parts or coins are manufactured or purchased and exhausted owned supplies end the shared inventory session.
 *
 * @param bot The bot restoring its owned axe.
 * @property parts Source metadata identifying the original axe and damaged forms.
 * @property broken Whether this session uses Bob instead of joining a head and handle.
 * @param duration Session budget managed by the inventory lifecycle.
 * @param zones Existing bank/work staging areas.
 * @author lare96
 */
class RepairAxeBotScript(
    bot: Bot,
    val parts: Parts,
    val broken: Boolean,
    duration: Duration,
    zones: MutableList<SubZone> = if (broken) mutableListOf(SubZone.LUMBRIDGE_COURT_YARD)
        else StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Bob's native NPC identity and exact repair-confirmation option.
         */
        const val BOB = 519
        const val ACCEPT = "Yes, please."

        /**
         * Source tier and repair path alongside inherited session and retry state.
         *
         * @author lare96
         */
        class RepairData : InventoryScriptData() {
            /**
             * Native axe tier identifying its real head, broken item and repair fee.
             */
            var parts = ""
            /**
             * Bob repair instead of owned head-and-handle assembly.
             */
            var broken = false

            override fun load(data: JsonObject) {
                super.load(data)
                parts = data.get("parts")?.asString ?: ""
                broken = data.get("broken")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("parts", parts)
                data.addProperty("broken", broken)
            }
        }
    }

    /**
     * Native conversion inputs; only Bob's non-free repairs consume coins.
     */
    private val inputId = if (broken) parts.broken else parts.head
    private val materials = buildList {
        add(Item(inputId))
        if (broken && parts.repairCost > 0) add(Item(995, parts.repairCost))
        if (!broken) add(Item(WoodcuttingRandomEvents.HANDLE))
    }

    /**
     * Restores the exact damaged tier and inherited independent retry budgets.
     */
    constructor(bot: Bot, data: RepairData) :
        this(bot, Parts.valueOf(data.parts), data.broken, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Allows a worn broken axe to be recovered through normal unequipping before inventory supply planning.
     */
    fun isEligible(): Boolean = if (broken && bot.equipment[Equipment.WEAPON]?.id == inputId) {
        parts.repairCost == 0 || bot.ownsProductionSupplies(listOf(Item(995, parts.repairCost)))
    } else bot.ownsProductionSupplies(materials)

    override suspend fun beforeInventoryInit(): Boolean {
        if (broken && bot.equipment[Equipment.WEAPON]?.id == inputId) {
            if (bot.inventory.isFull && !handler.banking.travelToBankDepositAll()) return false
            return handler.equipment.unequip(Equipment.WEAPON)
        }
        return true
    }

    /**
     * Reserves one coin-stack slot for paid repairs and never withdraws more damaged tools than the owned fee covers.
     */
    fun bankBatch(): List<Item> {
        if (!broken) return bot.productionBatch(materials)
        val paid = parts.repairCost > 0
        val amount = minOf(bot.inventory.capacity() - (if (paid) 1 else 0), bot.bank.computeAmountForId(inputId),
            if (paid) bot.bank.computeAmountForId(995) / parts.repairCost else Int.MAX_VALUE)
        if (amount <= 0) return emptyList()
        return listOf(Item(inputId, amount)) + if (paid) listOf(Item(995, parts.repairCost * amount)) else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials))) return true
        if (!broken) {
            attemptProduction(inputId) { handler.inventory.useItem(parts.head).onItem(WoodcuttingRandomEvents.HANDLE) }
            return true
        }
        val npc = world.locator.findNearestNpc(SubZone.LUMBRIDGE_COURT_YARD.inside, 80) {
            it.id == BOB && it.state == EntityState.ACTIVE
        }
        val approached = npc != null && handler.interactions.approach(npc, timeout = 45.seconds,
            canContinue = { isActionSafe() && bot.inventory.containsAll(materials) })
        if (!isActionSafe()) return true
        attemptProduction(inputId, available = approached) {
            val before = bot.inventory.computeAmountForId(inputId)
            npc != null && BotNpcDialogues.respond(bot, npc, listOf(ACCEPT), allowed = { isActionSafe() },
                completed = { bot.inventory.computeAmountForId(inputId) < before }) {
                handler.inventory.useItem(inputId).onNpc(npc)
            }
        }
        return true
    }

    override fun snapshot(): RepairData = RepairData().also {
        it.parts = parts.name
        it.broken = broken
        saveInventoryState(it)
    }
}
