package engine.bot.coordinator

import api.bot.action.CombatLoadout
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePurpose
import api.bot.script.EquipmentDropPlan
import api.bot.zone.SubZone
import api.predef.ext.totalItemAmount
import api.predef.slayer
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.NpcDropGatheringScript
import game.player.item.consume.food.Food
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.Task
import java.util.IdentityHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Admits one optional genuine equipment supplier per reviewed camp using owned kits and finite physical supplies.
 * Personal needs remain distinct from funded resale; an unbacked wish or expected rare drop is never spendable cash.
 * All state belongs to the game thread and leases recover at the saved absolute trip deadline.
 *
 * @author lare96
 */
internal object EquipmentDropJobs {

    /**
     * A camp reservation limits added supplier activity without reserving native NPCs from other players.
     *
     * @author lare96
     */
    private data class Lease(val owner: String, val plan: String, val until: Long, val itemId: Int)

    private val leases = HashMap<SubZone, Lease>()
    private val active = IdentityHashMap<Bot, NpcDropGatheringScript>()
    private val watchdogs = IdentityHashMap<Bot, Task>()

    /**
     * Admission checks a small prebuilt source list at an ordinary activity boundary, never scanning player holdings.
     */
    fun candidate(bot: Bot, now: Long = System.currentTimeMillis()): NpcDropGatheringScript? {
        if (bot.preferences.isSkiller || !bot.isAlive || bot.combat.inCombat()) return null
        expire(now)
        val personalTargets = BotTradeIntentions.generatedEquipmentAspirations(bot)
        val purse = bot.totalItemAmount(995).toLong()
        val allowance = (BotTradeManager.transferableAmount(bot, 995).toDouble() *
            BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        val candidates = EquipmentDropSources.all().shuffled().sortedByDescending { it.itemId in personalTargets }
        for (source in candidates) {
            if (source.zone in leases) continue
            val owned = bot.totalItemAmount(source.itemId)
            if (owned == Int.MAX_VALUE) continue
            val personal = source.itemId in personalTargets ||
                BotTradeIntentions.hasUnmetPersonalSupplyDemand(bot, source.itemId)
            if (!personal) continue
            if (loadout(bot, source) == null) continue
            val carriedFood = bot.inventory.filterNotNull().filter { it.id in Food.ID_TO_FOOD }.map { it.id }.distinct()
            if (carriedFood.size > 1) continue
            val minimumHeal = if (source.zone == SubZone.ASGARNIAN_WYVERNS) 16 else 14
            val minimumFood = if (source.zone == SubZone.ASGARNIAN_WYVERNS) 20 else 14
            val food = Food.ID_TO_FOOD.values.distinctBy { it.id }.filter {
                (carriedFood.isEmpty() || it.id == carriedFood.single()) &&
                it.heal >= minimumHeal && bot.totalItemAmount(it.id) >= minimumFood
            }.minByOrNull { it.heal } ?: continue
            val foodCount = minOf(20, bot.totalItemAmount(food.id))
            // This personal outing uses only its finite physically owned food basket.
            val price = 0L
            val travelCoins = if (source.zone == SubZone.BRIMHAVEN_DUNGEON_FIRE_GIANTS) 875L else 0L
            val cost = price * foodCount + travelCoins
            if (travelCoins > allowance || cost > allowance) continue
            val minimumBid = 0
            val plan = EquipmentDropPlan(source.key, source.itemId, personal, owned,
                now + EquipmentDropPlan.MAX_MILLIS, allowance, mapOf(food.id to foodCount),
                mapOf(food.id to price), purse, lastOwned = mapOf(food.id to bot.totalItemAmount(food.id)),
                minimumBid = minimumBid)
            if (!acquire(bot, plan, source, now)) continue
            val names = ImmutableSetMultimap.builder<SubZone, String>()
                .putAll(source.zone, source.names).build()
            return NpcDropGatheringScript(bot, source.itemId, plan.stockTarget,
                EquipmentDropPlan.MAX_MILLIS.milliseconds, mutableListOf(source.zone), names,
                sellOnCompletion = !personal, equipmentPlan = plan, monsterRequirement = source.monster)
        }
        return null
    }

    /**
     * Uses actual native capability and owned protection independently of any Slayer assignment the bot may hold.
     */
    internal fun loadout(bot: Bot, source: EquipmentDropSources.Source): CombatLoadout? {
        if (!bot.isAlive || source.zone.entryBlocker(bot) != null ||
            CombatCoordinator.combatReadiness(bot, BotGearPurpose.MELEE) < source.minimumReadiness ||
            source.monster?.let { bot.slayer.level < it.level } == true ||
            !NpcCombatScript.campSuppliesOwned(bot, source.zone)) return null
        val selected = NpcCombatScript.slayerLoadout(bot, BotGearPurpose.MELEE, source.monster,
            nativeRules = true) ?: return null
        val weapon = selected.gear[Equipment.WEAPON] ?: return null
        if (!bot.itemTracker.contains(weapon) ||
            NpcCombatScript.travelTools(bot, listOf(source.zone)).any { !bot.itemTracker.contains(it.id) }) return null
        return selected
    }

    fun acquire(bot: Bot, plan: EquipmentDropPlan, source: EquipmentDropSources.Source,
                now: Long = System.currentTimeMillis()): Boolean {
        expire(now)
        if (plan.ended || now >= plan.deadline || plan.deadline - now > EquipmentDropPlan.MAX_MILLIS ||
            plan.sourceKey != source.key || plan.itemId != source.itemId) return false
        val current = leases[source.zone]
        val owner = bot.username.orEmpty()
        if (current != null && (current.owner != owner || current.plan != plan.identity)) return false
        leases[source.zone] = Lease(owner, plan.identity, plan.deadline, plan.itemId)
        return true
    }

    fun attach(bot: Bot, script: NpcDropGatheringScript) {
        active[bot] = script
        watchdogs.remove(bot)?.cancel()
        val plan = script.equipmentPlan ?: return
        val watchdog = object : Task(1) {
            override fun execute() {
                if (active[bot] !== script) {
                    if (watchdogs[bot] === this) watchdogs.remove(bot)
                    cancel()
                    return
                }
                script.observeEquipmentSupplies()
                if (System.currentTimeMillis() >= plan.deadline || !bot.isAlive || plan.exposure > plan.maximumCoins) {
                    plan.end()
                    script.stopEquipmentCombat()
                    release(bot, plan)
                    script.stop()
                } else if (script.isPaused()) release(bot, plan)
            }
        }
        watchdogs[bot] = watchdog
        bot.world.schedule(watchdog)
    }

    fun release(bot: Bot, plan: EquipmentDropPlan) {
        val zone = EquipmentDropSources.find(plan.sourceKey)?.zone
        if (zone != null && leases[zone]?.let { it.owner == bot.username.orEmpty() && it.plan == plan.identity } == true)
            leases.remove(zone)
        if (active[bot]?.equipmentPlan === plan) {
            active.remove(bot)
            watchdogs.remove(bot)?.cancel()
        }
    }

    fun valid(bot: Bot, plan: EquipmentDropPlan, now: Long = System.currentTimeMillis()): Boolean {
        val source = EquipmentDropSources.find(plan.sourceKey) ?: return false
        if (leases[source.zone]?.let { it.owner == bot.username.orEmpty() && it.plan == plan.identity && it.until > now } != true)
            return false
        val opponent = bot.combat.lastCombatWith
        if (!plan.mayContinue(now) || !bot.isAlive || bot.healthPercent < 35.0 ||
            bot.combat.inCombat() && (opponent !is Npc || opponent.id !in source.npcIds) ||
            source.zone.entryBlocker(bot) != null ||
            CombatCoordinator.combatReadiness(bot, BotGearPurpose.MELEE) < source.minimumReadiness ||
            source.monster?.let { bot.slayer.level < it.level } == true) return false
        if (plan.personal) return source.itemId in BotTradeIntentions.generatedEquipmentAspirations(bot) ||
            BotTradeIntentions.hasUnmetPersonalSupplyDemand(bot, source.itemId) || bot.totalItemAmount(source.itemId) >= plan.stockTarget
        return false
    }

    fun creditedKill(bot: Bot, npc: Npc) { active[bot]?.creditedEquipmentKill(npc) }

    fun death(bot: Bot) {
        val script = active[bot] ?: return
        val plan = script.equipmentPlan ?: return
        plan.end()
        script.stopEquipmentCombat()
        release(bot, plan)
        script.stop()
    }

    private fun expire(now: Long) {
        for ((bot, script) in active.toMap()) {
            val plan = script.equipmentPlan ?: continue
            if (plan.deadline <= now) {
                plan.end()
                script.stopEquipmentCombat()
                release(bot, plan)
                script.stop()
            }
        }
        leases.entries.removeIf { it.value.until <= now }
    }
}
