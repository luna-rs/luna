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
import game.content.randomEvents.MiningRandomEvents
import game.content.randomEvents.WoodcuttingRandomEvents
import io.luna.game.model.EntityState
import io.luna.game.model.item.Bank.BankInterface
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.overlay.OverlayType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Restores owned gathering-tool heads and handles or pays Bob and Nurmof their native repair prices.
 * Native random-event metadata supplies every damaged form, output and fee. Head assembly uses ordinary stationary
 * areas; broken axes stage in Lumbridge and broken pickaxes in the Dwarven Mine before approaching their repairer.
 * Worn damage is unequipped normally, and only owned parts and currency are consumed through native interactions.
 *
 * @param bot The bot maintaining its existing gathering tools.
 * @property tool Native tool tier and repairer metadata.
 * @property broken Whether to repair a broken tool instead of joining its head and handle.
 * @param duration Session budget managed by the inventory lifecycle.
 * @param zones Existing bank/work staging areas.
 * @author lare96
 */
class RepairGatheringToolBotScript(
    bot: Bot,
    val tool: Tool,
    val broken: Boolean,
    duration: Duration,
    zones: MutableList<SubZone> = if (broken) mutableListOf(tool.kind.zone)
        else StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Both native repairers offer the same exact confirmation option.
         */
        const val ACCEPT = "Yes, please."

        /**
         * Family identity distinguishes identically named tiers in saved maintenance sessions.
         *
         * @property npc Native repair NPC, approached through normal interaction navigation.
         * @property zone Existing staging area containing that repairer.
         * @author lare96
         */
        enum class Kind(val npc: Int, val zone: SubZone) {
            AXE(519, SubZone.LUMBRIDGE_COURT_YARD),
            PICKAXE(594, SubZone.DWARVEN_MINE)
        }

        /**
         * Shared inventory recipe derived from native tool metadata rather than copied tier IDs or prices.
         *
         * @property kind Chooses the native repairer and existing staging area.
         * @property tier Stable source enum name retained in saved sessions.
         * @property restored Original usable tool returned by the native conversion.
         * @property head Detached head used only with this family's matching handle.
         * @property broken Damaged tool passed to its native repairer.
         * @property handle Matching source handle consumed by head assembly.
         * @property repairCost Native fee paid once for each still-owned broken tool.
         * @author lare96
         */
        class Tool internal constructor(val kind: Kind, val tier: String, val restored: Int, val head: Int,
                                        val broken: Int, val handle: Int, val repairCost: Int)

        /**
         * Every source-backed axe and pickaxe tier shares the same bounded maintenance runner.
         */
        val TOOLS = WoodcuttingRandomEvents.Parts.entries.map {
            Tool(Kind.AXE, it.name, it.axe.id, it.head, it.broken, WoodcuttingRandomEvents.HANDLE, it.repairCost)
        } + MiningRandomEvents.Parts.entries.map {
            Tool(Kind.PICKAXE, it.name, it.pickaxe.id, it.head, it.broken, MiningRandomEvents.HANDLE, it.repairCost)
        }

        /**
         * Native family and tier alongside inherited session/retry state. Missing family preserves old axe saves.
         *
         * @author lare96
         */
        open class RepairData : InventoryScriptData() {
            /**
             * Native tool family; legacy RepairAxeBotScript snapshots contain axes only.
             */
            var kind = Kind.AXE
            /**
             * Native tier name, retaining the original axe snapshot field.
             */
            var parts = ""
            /**
             * NPC repair instead of owned head-and-handle assembly.
             */
            var broken = false

            override fun load(data: JsonObject) {
                super.load(data)
                kind = data.get("kind")?.asString?.let(Kind::valueOf) ?: Kind.AXE
                parts = data.get("parts")?.asString ?: ""
                broken = data.get("broken")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("kind", kind.name)
                data.addProperty("parts", parts)
                data.addProperty("broken", broken)
            }
        }
    }

    /**
     * Native conversion inputs; only the repairer's non-free tiers consume owned coins.
     */
    private val inputId = if (broken) tool.broken else tool.head
    private val materials = buildList {
        add(Item(inputId))
        if (broken && tool.repairCost > 0) add(Item(995, tool.repairCost))
        if (!broken) add(Item(tool.handle))
    }

    /**
     * Restores the exact native family/tier and inherited independent retry budgets.
     */
    constructor(bot: Bot, data: RepairData) :
        this(bot, TOOLS.single { it.kind == data.kind && it.tier == data.parts }, data.broken,
            data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Includes a worn broken tool that can be unequipped before supply planning, without inventing repair funds.
     */
    fun isEligible(): Boolean = if (broken && bot.equipment[Equipment.WEAPON]?.id == inputId) {
        tool.repairCost == 0 || bot.ownsProductionSupplies(listOf(Item(995, tool.repairCost)))
    } else bot.ownsProductionSupplies(materials)

    override suspend fun beforeInventoryInit(): Boolean {
        if (!broken || bot.equipment[Equipment.WEAPON]?.id != inputId) return true
        if (!isActionSafe()) return false
        var bank = bot.overlays.getOverlay(BankInterface::class.java)
        if (bot.overlays.hasWindow() && bank == null) return false
        if (bot.inventory.isFull) {
            if (!handler.banking.travelToBankOpen()) return false
            bank = bot.overlays.getOverlay(BankInterface::class.java) ?: return false
            if (!handler.banking.depositInventory()) return false
        }
        if (bank != null) {
            if (!isActionSafe() || bot.overlays.getOverlay(BankInterface::class.java) !== bank ||
                bot.overlays.containsType(OverlayType.INPUT) || !handler.widgets.clickCloseInterface()) return false
        }
        if (!isActionSafe() || bot.overlays.hasWindow() || bot.equipment[Equipment.WEAPON]?.id != inputId) return false
        return handler.equipment.unequip(Equipment.WEAPON)
    }

    /**
     * Reserves one coin-stack slot for paid repairs and never withdraws more damaged tools than the owned fee covers.
     */
    fun bankBatch(): List<Item> {
        if (!broken) return bot.productionBatch(materials)
        val paid = tool.repairCost > 0
        val amount = minOf(bot.inventory.capacity() - (if (paid) 1 else 0), bot.bank.computeAmountForId(inputId),
            if (paid) bot.bank.computeAmountForId(995) / tool.repairCost else Int.MAX_VALUE)
        if (amount <= 0) return emptyList()
        return listOf(Item(inputId, amount)) + if (paid) listOf(Item(995, tool.repairCost * amount)) else emptyList()
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials))) return true
        if (!broken) {
            attemptProduction(inputId) { handler.inventory.useItem(tool.head).onItem(tool.handle) }
            return true
        }
        val npc = world.locator.findNearestNpc(tool.kind.zone.inside, 80) {
            it.id == tool.kind.npc && it.state == EntityState.ACTIVE
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
        it.kind = tool.kind
        it.parts = tool.tier
        it.broken = broken
        saveInventoryState(it)
    }
}
