package game.bot.scripts.skills

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProductionPlan
import api.bot.script.ProductionRecipe
import api.predef.SKILL_PRAYER
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.PrayerBonePurchases
import engine.bot.coordinator.skill.PrayerScriptFactory
import game.skill.prayer.Bone
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * Buys a quoted bone lot and hands the acquired stock to the normal Prayer training scripts.
 * The shared trade guard owns spending limits and the native trade owns the transfer.
 *
 * @author lare96
 */
internal class PrayerPurchaseBotScript private constructor(
    bot: Bot, private val duration: Duration, private val plan: ProductionPlan?, private val initialStock: Int,
    private val listed: Boolean, private var attempted: Boolean
) : BotScript(bot) {

    constructor(bot: Bot, duration: Duration, order: PrayerBonePurchases.Order? = null) : this(
        bot, duration, order?.let { purchasePlan(bot, it) }, order?.let { PrayerBoneStock.owned(bot, it.bone.id) } ?: 0,
        order?.listed == true, false)

    constructor(bot: Bot, data: PurchaseData) :
        this(bot, data.duration.milliseconds, data.plan, data.initialStock, data.listed, data.attempted)

    companion object {
        private fun purchasePlan(bot: Bot, order: PrayerBonePurchases.Order): ProductionPlan {
            val bone = order.bone
            val target = PrayerBoneStock.owned(bot, bone.id) + order.quantity
            val deadline = System.currentTimeMillis() + 5.minutes.inWholeMilliseconds
            val recipe = ProductionRecipe("prayer-stock:${bone.name}", SKILL_PRAYER, 1,
                mapOf(bone.id to target), emptySet(), emptyMap(), 0.0)
            return ProductionPlan(recipe, true, 1, order.coins, mapOf(bone.id to order.ceiling), deadline).apply {
                supplyDeadline = deadline
            }
        }
    }

    /**
     * Saves the remaining purchase guard and ownership at dispatch so completed transfers are counted once.
     *
     * @author lare96
     */
    class PurchaseData : BotScriptData() {
        var duration = 0L
        var plan: ProductionPlan? = null
        var initialStock = 0
        var listed = false
        var attempted = false

        override fun load(data: JsonObject) {
            duration = data.get("duration").asLong
            plan = data.getAsJsonObject("plan")?.let(ProductionPlan::load)
            initialStock = data.get("initial_stock").asInt
            listed = data.get("listed").asBoolean
            attempted = data.get("attempted").asBoolean
        }

        override fun save(data: JsonObject) {
            data.addProperty("duration", duration)
            plan?.let { data.add("plan", it.save()) }
            data.addProperty("initial_stock", initialStock)
            data.addProperty("listed", listed)
            data.addProperty("attempted", attempted)
        }
    }

    override suspend fun run(): Boolean {
        if (!BotCombatActionHandler.wantsToTrain(bot, SKILL_PRAYER)) return true
        val id = plan?.recipe?.materials?.keys?.single()
        if (plan != null && id != null) {
            if (!attempted && System.currentTimeMillis() < plan.supplyDeadline) {
                attempted = true
                BotTradeIntentions.recordProductionPlan(bot, plan, this)
                if (handler.shop.obtainWantedItem(plan, id, plan.target(id), duration)) return false
            }
            BotTradeIntentions.clearProductionPlan(bot, plan, this)
            val acquired = PrayerBoneStock.owned(bot, id) - initialStock
            if (acquired > 0) PrayerBoneStock.retainPurchase(bot, id, acquired)
            else if (attempted) PrayerBonePurchases.recordMiss(bot, id, listed)
        }
        if (PrayerBoneStock.hasNotes(bot) &&
            withTimeoutOrNull(120_000L) { handler.banking.travelToBankDepositAll() } != true) return true
        if (!awaitActionSafety()) return true
        val preferred = id?.let(Bone.ID_TO_BONE::get)?.takeIf { PrayerBoneStock.owned(bot, it.id) > initialStock }
        PrayerScriptFactory.ownedTraining(bot, duration, preferred)?.let { next ->
            next.trainingGoal = trainingGoal
            bot.scriptStack.softPushHead(next)
        }
        return true
    }

    override suspend fun finish() {
        if (!isPaused()) plan?.let { BotTradeIntentions.clearProductionPlan(bot, it, this) }
    }

    override fun snapshot() = PurchaseData().also {
        it.duration = duration.inWholeMilliseconds
        it.plan = plan
        it.initialStock = initialStock
        it.listed = listed
        it.attempted = attempted
    }
}
