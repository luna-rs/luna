package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.BankResourceCoordinator
import game.skill.prayer.Bone
import game.skill.prayer.buryBone.BuryBoneAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Trains Prayer by burying one owned bone type through its normal first inventory option.
 *
 * Every existing [Bone] recipe uses the same script and safe banked processing zones. Partial bank stock
 * is supported, up to twenty-eight bones per withdrawal. [InventoryBotScript] provides verified unnoted
 * banking, safety checks, progress detection, and independent persistent retry budgets. Missing startup
 * bones request a total stock target of 1,000. No bones are spawned, purchased, or looted by this script.
 *
 * The player [BuryBoneAction] consumes one bone and awards its configured experience. Its shared cooldown
 * is checked before clicking, so normal cooldown deferrals do not count as failed production attempts.
 * Snapshots retain the bone recipe, duration, zones, and budgets. Profit selection excludes this activity.
 * Bank-relief sessions consume only unreserved owned bones without recording replacement demand or accepting loot.
 *
 * @param bot The bot training Prayer.
 * @property bone The existing bone recipe to bury throughout the session.
 * @param duration Session duration managed by the inherited zone lifecycle.
 * @param zones Existing safe banked processing zones.
 * @property bankRelief Whether only expendable owned bones may be consumed.
 * @author lare96
 */
class BuryBoneBotScript(
    bot: Bot,
    val bone: Bone,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_PRAYER),
    val bankRelief: Boolean = false
) : InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_PRAYER
    override val verifiedProductionWithdrawals = true
    override val ownedSuppliesOnly get() = bankRelief
    override fun allowsLoot(item: io.luna.game.model.item.GroundItem): Boolean = !bankRelief && super.allowsLoot(item)

    companion object {
        /**
         * Saved bone recipe and common inventory session configuration and retry budgets.
         *
         * @author lare96
         */
        class BoneData : InventoryScriptData() {
            /** Source [Bone] enum name. */
            var recipe = Bone.BONES.name
            /**
             * Whether only expendable owned bones may be used without recording replacement demand.
             */
            var bankRelief = false
            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: Bone.BONES.name
                bankRelief = data.get("bank_relief")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                if (bankRelief) data.addProperty("bank_relief", true) else data.remove("bank_relief")
            }
        }
    }

    /** Minimum carried supply for one normal burial. */
    private val materials = listOf(bone.boneItem)

    /** Restores the recipe, session configuration, and persistent retry budgets. */
    constructor(bot: Bot, data: BoneData) :
        this(bot, Bone.valueOf(data.recipe), data.duration, data.zones, data.bankRelief) {
        restoreInventoryState(data)
    }

    /** Every implemented bone recipe is level-independent and requires an owned bone. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked bones, or a smaller final batch. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> {
        if (bankRelief && !BankResourceCoordinator.canProcess(bot, SKILL_PRAYER, materials)) {
            stop()
            return emptyList()
        }
        return productionWithdraw(materials, requestMissing = !bankRelief)
    }
    override fun bankWithdraw(): List<Item> {
        if (bankRelief && !BankResourceCoordinator.canProcess(bot, SKILL_PRAYER, materials)) {
            stop()
            return emptyList()
        }
        return bankBatch().also { if (it.isEmpty()) stop() }
    }
    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))
    override suspend fun onExecuteInZone(): Boolean {
        if (bankRelief && !BankResourceCoordinator.canProcess(bot, SKILL_PRAYER, materials)) {
            stop()
            return true
        }
        if (!productionReady(bot.inventory.containsAll(materials)) || !BuryBoneAction.isReady(bot)) return true
        attemptProduction(bone.id) { handler.inventory.clickItem(1, bone.id) }
        return true
    }
    override suspend fun finish() {
        bot.actions.first(BuryBoneAction::class.java)?.interrupt()
    }
    override fun snapshot(): BoneData = BoneData().also {
        it.recipe = bone.name
        it.bankRelief = bankRelief
        saveInventoryState(it)
    }
}
