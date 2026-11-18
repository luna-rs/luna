package game.bot.scripts

import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.zone.SubZone
import api.bot.script.EquipmentDropPlan
import api.bot.webwalk.plan.BankAccess
import api.predef.ext.totalItemAmount
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.SetMultimap
import com.google.gson.JsonObject
import engine.bot.gear.BotGearPurpose
import engine.bot.coordinator.EquipmentDropJobs
import engine.bot.coordinator.EquipmentDropSources
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.bot.scripts.trading.TradeBotScript
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import game.player.item.consume.food.Food
import game.obj.entrances.BrimhavenDungeonAccess
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.CombatAction
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import java.util.IdentityHashMap

/**
 * Kills named NPCs in supplied camps and banks one selected drop until the bot owns [stockTarget].
 * The inherited combat trip equips owned gear and brings food; this session never buries collected bones.
 * A profit outing advertises available stock through the normal player marketplace after banking or completion.
 * Feather gathering is restricted to bots below combat level 20, including restored and active sessions.
 *
 * @property itemId The unnoted item collected from NPC drops.
 * @property stockTarget Total banked and carried stock at which the trip ends.
 * @property sellOnCompletion Whether to offer the gathered item for sale.
 * @author lare96
 */
class NpcDropGatheringScript(
    bot: Bot,
    val itemId: Int,
    val stockTarget: Int,
    duration: Duration,
    zones: MutableList<SubZone>,
    private val targetNames: SetMultimap<SubZone, String>,
    val sellOnCompletion: Boolean = true,
    private val slayerTask: SlayerTaskType? = null,
    private val slayerMaster: SlayerMaster? = null,
    val equipmentPlan: EquipmentDropPlan? = null,
    monsterRequirement: SlayerTaskType? = null
) : NpcCombatScript(bot, duration, zones, targetNames, BotGearPurpose.MELEE, null,
    slayerTask, slayerMaster, monsterRequirement = monsterRequirement) {

    /**
     * Shares item-specific level restrictions with profit trip selection.
     *
     * @author lare96
     */
    companion object {
        private const val FEATHERS = 314

        /**
         * Keeps feather collection below combat level 20 without restricting other NPC drops.
         */
        internal fun levelEligible(itemId: Int, combatLevel: Int): Boolean =
            itemId != FEATHERS || combatLevel < 20
    }

    /**
     * Persists the exact target and NPC restrictions alongside the inherited combat session.
     *
     * @author lare96
     */
    class DropGatheringData : NpcCombatData() {
        var itemId = -1
        var stockTarget = 0
        var sellOnCompletion = true
        var equipmentPlan: EquipmentDropPlan? = null

        override fun load(data: JsonObject) {
            super.load(data)
            itemId = data.get("item_id")?.asInt ?: -1
            stockTarget = data.get("stock_target")?.asInt ?: 0
            sellOnCompletion = data.get("sell_on_completion")?.asBoolean ?: true
            equipmentPlan = data.getAsJsonObject("equipment_drop_plan")?.let(EquipmentDropPlan::load)
        }

        override fun save(data: JsonObject) {
            super.save(data)
            data.addProperty("item_id", itemId)
            data.addProperty("stock_target", stockTarget)
            data.addProperty("sell_on_completion", sellOnCompletion)
            equipmentPlan?.let { data.add("equipment_drop_plan", it.save()) }
        }
    }

    constructor(bot: Bot, data: DropGatheringData) : this(bot, data.itemId, data.stockTarget,
        data.duration, data.zones, data.names, data.sellOnCompletion, data.slayerTask, data.slayerMaster,
        data.equipmentPlan, data.monsterRequirement)

    private val engagedLives = IdentityHashMap<Npc, Int>()
    private val creditedLives = IdentityHashMap<Npc, Int>()
    private var finalLootPending = false

    override val ownedCombatSuppliesOnly: Boolean get() = equipmentPlan != null

    override val exclusiveNpcIds: Set<Int>? get() = equipmentPlan?.takeIf { itemId == EquipmentDropSources.ICE_GLOVES }
        ?.let { EquipmentDropSources.find(it.sourceKey)?.npcIds }

    override val targetSearchTimeout: Duration get() = if (equipmentPlan != null) 45.seconds else super.targetSearchTimeout

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        val plan = equipmentPlan ?: return super.travelToWorkZone(zone, bank)
        val remaining = plan.deadline - System.currentTimeMillis()
        if (remaining <= 0L || !EquipmentDropJobs.valid(bot, plan)) return false
        return withTimeoutOrNull(remaining) {
            handler.webWalkTo(zone, bank = BankAccess.NONE, fallback = false)
        } == true
    }

    internal fun observeEquipmentSupplies() {
        val plan = equipmentPlan ?: return
        plan.observe(plan.supplies.keys.associateWith { bot.totalItemAmount(it) }, bot.totalItemAmount(995).toLong())
    }

    override fun combatActivityAllowed(): Boolean {
        val plan = equipmentPlan ?: return true
        observeEquipmentSupplies()
        return !goalReached() && boundedFoodOnly() && EquipmentDropJobs.valid(bot, plan)
    }

    private fun boundedFoodOnly(): Boolean = equipmentPlan?.let { plan ->
        bot.inventory.filterNotNull().none { it.id in Food.ID_TO_FOOD && it.id !in plan.supplies }
    } != false

    /**
     * Stops this supplier's native offensive action before surrendering control to ordinary defensive responses.
     */
    internal fun stopEquipmentCombat() {
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        bot.combat.setFirstAttack(null)
        bot.actions.getAll(CombatAction::class.java).forEach { it.interrupt() }
        bot.reflex.isDisableCombatReflex = false
    }

    override suspend fun withdrawBoundedFood(): Boolean {
        val plan = equipmentPlan ?: return false
        val source = EquipmentDropSources.find(plan.sourceKey) ?: return false
        observeEquipmentSupplies()
        if (!EquipmentDropJobs.valid(bot, plan) || !bot.bank.isOpen) return false
        if (source.zone == SubZone.BRIMHAVEN_DUNGEON_FIRE_GIANTS && source.zone !in bot.subZones &&
            !BrimhavenDungeonAccess.hasPaid(bot)) {
            if (plan.spentCoins > 0L || plan.exposure + BrimhavenDungeonAccess.FEE > plan.maximumCoins) return false
            val missing = (BrimhavenDungeonAccess.FEE - bot.inventory.computeAmountForId(995)).coerceAtLeast(0)
            if (missing > 0 && !handler.banking.withdrawUnnoted(Item(995, missing)) {
                    !isPaused() && !isTerminated() && EquipmentDropJobs.valid(bot, plan)
                }) return false
            if (bot.inventory.computeAmountForId(995) < BrimhavenDungeonAccess.FEE) return false
        }
        for (id in plan.supplies.keys) {
            val count = minOf(plan.remainingSupply(id), bot.totalItemAmount(id), 20)
            val minimum = if (source.zone == SubZone.ASGARNIAN_WYVERNS) 20 else minOf(source.minimumFoodCount, count)
            if (count < maxOf(1, minimum)) return false
            val missing = (count - bot.inventory.computeAmountForId(id)).coerceAtLeast(0)
            if (missing > 0 && !handler.banking.withdrawUnnoted(Item(id, missing)) {
                    !isPaused() && !isTerminated() && EquipmentDropJobs.valid(bot, plan)
                }) return false
            if (bot.inventory.computeAmountForId(id) < minimum) return false
        }
        return true
    }

    /**
     * Only the native death callback with the credited killer can advance the saved kill counter.
     */
    internal fun creditedEquipmentKill(npc: Npc) {
        val plan = equipmentPlan ?: return
        if (npc.isAlive) return
        if (engagedLives[npc] != npc.spawnCount || creditedLives[npc] == npc.spawnCount) return
        val source = EquipmentDropSources.find(plan.sourceKey) ?: return
        if (npc.id !in source.npcIds || npc.basePosition !in source.zone.area) return
        creditedLives[npc] = npc.spawnCount
        plan.creditedKill()
        finalLootPending = true
    }

    override suspend fun interactWithTarget(option: Int, target: Npc): Boolean {
        val plan = equipmentPlan
        if (plan != null && engagedLives[target] != target.spawnCount) {
            if (!combatActivityAllowed() || !plan.engage()) return false
            engagedLives[target] = target.spawnCount
        }
        return super.interactWithTarget(option, target)
    }

    private fun goalReached(): Boolean = bot.totalItemAmount(itemId) >= stockTarget

    private val notedItemId: Int get() = api.predef.itemDef(itemId).notedId.orElse(-1)

    private fun carryingResource(): Boolean = itemId in bot.inventory ||
        notedItemId in bot.inventory

    private fun canContinueGathering(): Boolean {
        if (levelEligible(itemId, bot.combatLevel)) return true
        bot.combat.target = null
        stop()
        return false
    }

    override suspend fun onInit(resumed: Boolean): Boolean {
        val plan = equipmentPlan
        if (plan != null) {
            if (!boundedFoodOnly()) return false
            val source = EquipmentDropSources.find(plan.sourceKey) ?: return false
            observeEquipmentSupplies()
            if (plan.itemId != itemId || plan.stockTarget != stockTarget || originalZones != listOf(source.zone) ||
                plan.supplies.size != 1 || plan.supplies.values.any { it !in 1..20 } ||
                plan.supplies.keys.any { (Food.ID_TO_FOOD[it]?.heal ?: 0) < source.minimumFoodHeal } ||
                monsterRequirement != source.monster || slayerTask != null || slayerMaster != null ||
                targetNames[source.zone].toSet() != source.names ||
                !EquipmentDropJobs.acquire(bot, plan, source) || !EquipmentDropJobs.valid(bot, plan)) return false
            EquipmentDropJobs.attach(bot, this)
        }
        return levelEligible(itemId, bot.combatLevel) &&
            ItemDefinition.isIdValid(itemId) && stockTarget > 0 && originalZones.isNotEmpty() &&
            originalZones.all { targetNames[it].isNotEmpty() } &&
            ((slayerTask == null) == (slayerMaster == null)) &&
            super.onInit(resumed)
    }

    override fun wildernessNotingItems(): Set<Int> = setOf(itemId)

    override fun isLoot(item: GroundItem): Boolean =
        (item.id == itemId || item.id == notedItemId) && !goalReached() &&
            (equipmentPlan == null || !item.view.isGlobal && item.view.any { it === bot })

    override fun buryLootedBones(): Boolean = false

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        if (!canContinueGathering()) return false
        val plan = equipmentPlan
        if (plan != null) {
            val source = EquipmentDropSources.find(plan.sourceKey) ?: return false
            if (newFocus.id !in source.npcIds || newFocus.basePosition !in source.zone.area ||
                !combatActivityAllowed() || engagedLives[newFocus] != newFocus.spawnCount &&
                plan.engagements >= EquipmentDropPlan.MAX_KILLS) return false
        }
        if (bot.inventory.isFull || goalReached()) {
            forceBanking = true
            return false
        }
        return super.onAssignFocus(newFocus)
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (!canContinueGathering()) return true
        val plan = equipmentPlan
        if (plan != null) {
            observeEquipmentSupplies()
            // Give a final real kill its ordinary pickup pass before the saved cap ends combat.
            if (finalLootPending && bot.isAlive && System.currentTimeMillis() < plan.deadline) {
                finalLootPending = false
                lootItems()
            }
            if (!goalReached() && (!EquipmentDropJobs.valid(bot, plan) ||
                    plan.engagements >= EquipmentDropPlan.MAX_KILLS && !bot.combat.inCombat())) {
                bot.combat.target = null
                stop()
                return true
            }
        }
        if (goalReached()) {
            if (carryingResource()) {
                forceBanking = true
            } else {
                queueSale()
                stop()
                return true
            }
        }
        return super.beforeZoneWork()
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (goalReached()) {
            queueSale()
            stop()
            return
        }
        super.onBankOpen(initial)
    }

    override suspend fun completed() {
        queueSale()
    }

    override suspend fun finish() {
        equipmentPlan?.let { plan ->
            stopEquipmentCombat()
            observeEquipmentSupplies()
            EquipmentDropJobs.release(bot, plan)
            if (!isPaused()) plan.end()
        }
        super.finish()
    }

    private fun queueSale() {
        equipmentPlan?.let { if (it.personal || bot.totalItemAmount(itemId) <= it.startingStock) return }
        if (!sellOnCompletion || !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot, BotTradePrice.computePriceStrategy(bot), itemId)
        if (quote != null && quote.totalPrice().toLong() >= BotTradeManager.MINIMUM_OFFER_VALUE) {
            bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
                BotMarketplace.select(bot), saleTarget = itemId))
        }
    }

    override fun snapshot(): DropGatheringData = DropGatheringData().also {
        it.itemId = itemId
        it.stockTarget = stockTarget
        it.sellOnCompletion = sellOnCompletion
        observeEquipmentSupplies()
        it.equipmentPlan = equipmentPlan
        it.monsterRequirement = monsterRequirement
        it.slayerTask = slayerTask
        it.slayerMaster = slayerMaster
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.names = ImmutableSetMultimap.copyOf(targetNames)
    }
}
