package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.stringBow.Bow
import game.skill.fletching.stringBow.StringBowAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strings one configured bow type using owned unstrung bows and bow strings.
 * A single matching pair qualifies; bank withdrawals balance both inputs up to fourteen pairs, including
 * the last partial batch. Production uses the ordinary item interaction and make dialogue, with conversion
 * and experience supplied by [StringBowAction]. Arrow shafts are excluded because they cannot be strung.
 *
 * [InventoryBotScript] supplies travel, verified banking, safety, session expiry, and bounded retries.
 * Saved scripts retain their bow, zones, duration, and retry counters. Missing inputs request stock targets
 * through the shared production helper before the session stops.
 *
 * @param bot The bot stringing bows.
 * @property bow The bow recipe used throughout the session.
 * @param duration The session duration.
 * @param zones Existing processing zones with bank and travel support.
 * @author lare96
 */
class StringBowBotScript(
    bot: Bot,
    val bow: Bow,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    init {
        require(bow != Bow.ARROW_SHAFT) { "ARROW_SHAFT bow type cannot be used with this script." }
    }

    companion object {
        /** Saved bow recipe and inherited session/retry state. @author lare96 */
        class StringBowData : InventoryScriptData() {
            /** Bow enum retained under its existing snapshot field name. */
            var bow: Bow = Bow.SHORTBOW

            override fun load(data: JsonObject) {
                super.load(data)
                bow = Bow.valueOf(data.get("bow").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("bow", bow.name)
            }
        }
    }

    /** Permanent Fletching level required for selection and startup. */
    val requiredLevel = bow.level
    /** One matching unstrung bow and string, the minimum complete recipe. */
    private val materials = listOf(bow.unstrungItem, Item(Bow.BOW_STRING))

    /** Restores the legacy bow field together with saved zones and shared retry counters. */
    constructor(bot: Bot, data: StringBowData) : this(bot, data.bow, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level and one complete owned pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced pairs, capped by the smaller bank stock. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether the carried inputs can produce at least one configured bow. */
    private fun hasMaterials(): Boolean = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        attemptProduction(bow.unstrung) { startProduction() }
        return true
    }

    /** Opens the native single-recipe dialogue and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(Bow.BOW_STRING).onItem(bow.unstrung)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(StringBowAction::class.java)?.interrupt()
    }

    override fun snapshot(): StringBowData = StringBowData().also {
        it.bow = bow
        saveInventoryState(it)
    }
}
