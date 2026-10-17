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
 *
 * @param bot The bot training Prayer.
 * @property bone The existing bone recipe to bury throughout the session.
 * @param duration Session duration managed by the inherited zone lifecycle.
 * @param zones Existing safe banked processing zones.
 * @author lare96
 */
class BuryBoneBotScript(
    bot: Bot,
    val bone: Bone,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved bone recipe and common inventory session configuration and retry budgets.
         *
         * @author lare96
         */
        class BoneData : InventoryScriptData() {
            /** Source [Bone] enum name. */
            var recipe = Bone.BONES.name
            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: Bone.BONES.name
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /** Minimum carried supply for one normal burial. */
    private val materials = listOf(bone.boneItem)

    /** Restores the recipe, session configuration, and persistent retry budgets. */
    constructor(bot: Bot, data: BoneData) : this(bot, Bone.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Every implemented bone recipe is level-independent and requires an owned bone. */
    fun isEligible(): Boolean = bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked bones, or a smaller final batch. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials)
    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }
    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))
    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials)) || !BuryBoneAction.isReady(bot)) return true
        attemptProduction(bone.id) { handler.inventory.clickItem(1, bone.id) }
        return true
    }
    override suspend fun finish() {
        bot.actions.first(BuryBoneAction::class.java)?.interrupt()
    }
    override fun snapshot(): BoneData = BoneData().also {
        it.recipe = bone.name
        saveInventoryState(it)
    }
}
