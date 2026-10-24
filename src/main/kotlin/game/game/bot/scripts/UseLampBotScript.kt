package game.bot.scripts

import api.bot.action.BotCombatActionHandler
import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.ext.*
import com.google.gson.JsonObject
import game.item.experienceLamp.ExperienceLampInterface
import game.item.experienceLamp.ExperienceLampInterface.InterfaceSkill
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Redeems owned experience lamps through the normal inventory and interface buttons.
 *
 * Each session explicitly selects one supported skill before confirming each lamp. The skill selection must be
 * acknowledged by the player's lamp varp before confirmation, preventing a delayed selection from awarding XP
 * to a previous skill. The existing player handler consumes the lamp and determines the experience reward.
 *
 * [InventoryBotScript] provides safe banking, bounded interactions, progress checks, and persistent retry budgets.
 * Missing lamps end the session without purchase requests. General activities prefer the bot's skill preferences;
 * when none match the lamp interface they choose one supported skill. Saved sessions retain that choice.
 *
 * @param bot The bot redeeming its lamps.
 * @property skill The interface skill explicitly selected for every lamp in this session.
 * @param duration Session length managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking and travel support.
 * @author lare96
 */
class UseLampBotScript(
    bot: Bot,
    val skill: InterfaceSkill,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val trainingSkill get() = skill.skill
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Experience lamp item registered by the existing player handler. */
        const val LAMP = 2528
        /** Confirmation button on the experience lamp interface. */
        const val CONFIRM_BUTTON = 2831

        /**
         * Saved skill selection alongside inherited session configuration and retry budgets.
         *
         * @author lare96
         */
        class LampData : InventoryScriptData() {
            /** [InterfaceSkill] enum name selected for the complete session. */
            var selectedSkill = ""

            override fun load(data: JsonObject) {
                super.load(data)
                selectedSkill = data.get("selectedSkill")?.asString ?: ""
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("selectedSkill", selectedSkill)
            }
        }
    }

    /** One owned lamp is sufficient to start redeeming. */
    private val materials = listOf(Item(LAMP))

    /** Restores the chosen skill, session configuration, and inherited retry budgets. */
    constructor(bot: Bot, data: LampData) :
        this(bot, InterfaceSkill.valueOf(data.selectedSkill), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks unnoted lamp ownership across inventory and bank. */
    fun isEligible() = bot.ownsProductionSupplies(materials)

    /** Withdraws at most one inventory of banked lamps, allowing a smaller final batch. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(LAMP in bot.inventory)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(LAMP in bot.inventory)) return true
        attemptProduction(LAMP) { startRedemption() }
        return true
    }

    /** Opens the lamp, selects the saved skill, waits for acknowledgment, then confirms through player input. */
    private suspend fun startRedemption(): Boolean {
        if (!handler.inventory.clickItem(1, LAMP)) return false
        if (!waitFor(3.seconds) { ExperienceLampInterface::class in bot.overlays }) return false
        if (!isActionSafe() || !bot.output.clickButton(skill.button)) return false
        if (!waitFor(3.seconds) {
                bot.varpManager.getValue(PersistentVarp.XP_LAMP) == skill.varpValue
            }) return false
        return BotCombatActionHandler.wantsToTrain(bot, skill.skill) && isActionSafe() && ExperienceLampInterface::class in bot.overlays &&
            bot.output.clickButton(CONFIRM_BUTTON)
    }

    override fun snapshot(): LampData = LampData().also {
        it.selectedSkill = skill.name
        saveInventoryState(it)
    }
}
