package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.ogre.AcheyProduct
import game.skill.fletching.ogre.BrutalArrow
import game.skill.fletching.ogre.ChiselBonesAction
import game.skill.fletching.ogre.ChiselBonesMenu
import game.skill.fletching.ogre.CutAcheyLogAction
import game.skill.fletching.ogre.OgreFletching.ACHEY_LOGS
import game.skill.fletching.ogre.OgreFletching.BOW_STRING
import game.skill.fletching.ogre.OgreFletching.CHISEL
import game.skill.fletching.ogre.OgreFletching.COMP_BOW_LEVEL
import game.skill.fletching.ogre.OgreFletching.COMP_OGRE_BOW
import game.skill.fletching.ogre.OgreFletching.FEATHER
import game.skill.fletching.ogre.OgreFletching.FEATHERS_PER_ARROW
import game.skill.fletching.ogre.OgreFletching.FLIGHTED_OGRE_ARROW
import game.skill.fletching.ogre.OgreFletching.HAMMER
import game.skill.fletching.ogre.OgreFletching.KNIFE
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW_LEVEL
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW_SHAFT
import game.skill.fletching.ogre.OgreFletching.SET_AMOUNT
import game.skill.fletching.ogre.OgreFletching.SHAFTS_PER_LOG
import game.skill.fletching.ogre.OgreFletching.TIPS_PER_BONE
import game.skill.fletching.ogre.OgreFletching.UNSTRUNG_COMP_BOW
import game.skill.fletching.ogre.OgreFletching.WOLFBONE_ARROWTIPS
import game.skill.fletching.ogre.OgreFletching.WOLF_BONES
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Makes ogre ammunition and composite ogre bows through their normal item interactions and menus.
 * Native actions own conversion, random yields, levels and experience. Banking balances complete recipe
 * units, including four feathers per shaft, and reserves room for the largest possible shaft or tip yield.
 * The shared inventory lifecycle supplies safe travel, verified withdrawals, expiry and saved retry budgets.
 *
 * @param bot The bot fletching ogre equipment.
 * @property recipe The production step retained throughout this session.
 * @param duration The production session duration.
 * @param zones Existing bank-supported Fletching destinations.
 * @author lare96
 */
class OgreFletchingBotScript(
    bot: Bot,
    val recipe: Recipe,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_FLETCHING)
) : InventoryBotScript(bot, duration, zones) {
    override val trainingSkill = SKILL_FLETCHING
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * A native ogre recipe with its consumed quantities, reusable tools and menu route.
         *
         * @author lare96
         */
        data class Recipe(
            val key: String, val level: Int, val used: Int, val target: Int, val output: Int,
            val materials: List<Item>, val tools: Set<Int> = emptySet(), val batchSize: Int = SET_AMOUNT,
            val maxOutput: Int = 1, val acheyProduct: AcheyProduct? = null, val bones: Boolean = false
        )

        /**
         * Stable production identifiers; levels and yields come from the native recipes.
         */
        val RECIPES: Map<String, Recipe> = (
            AcheyProduct.entries.map { product ->
                Recipe(if (product == AcheyProduct.COMP_OGRE_BOW) "CUT_BOW" else "SHAFTS",
                    product.level, KNIFE, ACHEY_LOGS, product.id,
                    if (product == AcheyProduct.COMP_OGRE_BOW) listOf(Item(ACHEY_LOGS), Item(WOLF_BONES))
                    else listOf(Item(ACHEY_LOGS)), setOf(KNIFE), batchSize = 1,
                    maxOutput = if (product == AcheyProduct.COMP_OGRE_BOW) 1 else SHAFTS_PER_LOG.last,
                    acheyProduct = product)
            } + listOf(
                Recipe("TIPS", OGRE_ARROW_LEVEL, CHISEL, WOLF_BONES, WOLFBONE_ARROWTIPS,
                    listOf(Item(WOLF_BONES)), setOf(CHISEL), batchSize = 1,
                    maxOutput = TIPS_PER_BONE.last, bones = true),
                Recipe("FLIGHTED", OGRE_ARROW_LEVEL, FEATHER, OGRE_ARROW_SHAFT, FLIGHTED_OGRE_ARROW,
                    listOf(Item(OGRE_ARROW_SHAFT), Item(FEATHER, FEATHERS_PER_ARROW))),
                Recipe("OGRE", OGRE_ARROW_LEVEL, WOLFBONE_ARROWTIPS, FLIGHTED_OGRE_ARROW, OGRE_ARROW,
                    listOf(Item(FLIGHTED_OGRE_ARROW), Item(WOLFBONE_ARROWTIPS))),
                Recipe("STRING_BOW", COMP_BOW_LEVEL, BOW_STRING, UNSTRUNG_COMP_BOW, COMP_OGRE_BOW,
                    listOf(Item(UNSTRUNG_COMP_BOW), Item(BOW_STRING)), batchSize = 1)
            ) + BrutalArrow.entries.map { arrow ->
                Recipe("BRUTAL:${arrow.name}", arrow.level, arrow.nails, FLIGHTED_OGRE_ARROW, arrow.id,
                    listOf(Item(FLIGHTED_OGRE_ARROW), Item(arrow.nails)), setOf(HAMMER))
            }
        ).associateBy { it.key }

        /**
         * Saved recipe and inherited session/retry state.
         *
         * @author lare96
         */
        class OgreFletchingData : InventoryScriptData() {
            var recipe = "SHAFTS"

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe").asString
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /**
     * Permanent Fletching level required for selection and startup.
     */
    val requiredLevel = recipe.level

    /**
     * Restores the production step, duration, zones and retry counters.
     */
    constructor(bot: Bot, data: OgreFletchingData) :
        this(bot, RECIPES.getValue(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Requires one complete owned recipe unit, including its reusable tools.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel &&
        bot.ownsProductionSupplies(recipe.materials, recipe.tools)

    /**
     * Balances up to one thousand recipe units against bank stock and inventory capacity throughout production.
     * Stackable inputs occupy one slot regardless of their recipe ratio. Random yields reserve their maximum.
     */
    fun bankBatch(): List<Item> {
        if (recipe.tools.any { bot.bank.computeAmountForId(it) < 1 }) return emptyList()
        val available = minOf(1_000, recipe.materials.minOf { bot.bank.computeAmountForId(it.id) / it.amount })
        val units = (available downTo 1).firstOrNull { count ->
            // Between the first and last conversion, slot usage is linear; checking its endpoints suffices.
            listOf(0, 1, count - 1, count).all { made ->
                val remaining = count - made
                val inputs = recipe.materials.sumOf {
                    if (remaining == 0) 0 else if (bot.inventory.stackable(it.id)) 1 else remaining * it.amount
                }
                val output = if (made == 0) 0 else if (bot.inventory.stackable(recipe.output)) 1
                    else made * recipe.maxOutput
                recipe.tools.size + inputs + output <= bot.inventory.capacity()
            }
        } ?: return emptyList()
        return recipe.tools.map { Item(it) } + recipe.materials.map { Item(it.id, it.amount * units) }
    }

    override fun withdraw(): List<Item> = productionWithdraw(recipe.materials, recipe.tools,
        levelEligible = bot.skill(SKILL_FLETCHING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /**
     * Checks the next native partial batch, tools, released input slots and worst-case output capacity.
     */
    private fun hasMaterials(): Boolean {
        if (!bot.inventory.containsAll(recipe.materials) || recipe.tools.any { it !in bot.inventory }) return false
        val units = minOf(recipe.batchSize,
            recipe.materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount })
        val outputAmount = units * recipe.maxOutput
        if (bot.inventory.stackable(recipe.output) &&
            bot.inventory.computeAmountForId(recipe.output).toLong() + outputAmount > Int.MAX_VALUE) return false
        val freed = recipe.materials.sumOf {
            val consumed = units * it.amount
            if (!bot.inventory.stackable(it.id)) consumed
            else if (bot.inventory.computeAmountForId(it.id) == consumed) 1 else 0
        }
        val output = if (!bot.inventory.stackable(recipe.output)) outputAmount
            else if (bot.inventory.contains(recipe.output)) 0 else 1
        return bot.inventory.computeRemainingSize() + freed >= output
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), bot.skill(SKILL_FLETCHING).level >= requiredLevel)) return true
        attemptProduction(recipe.materials.first().id, outputId = recipe.output) { startProduction() }
        return true
    }

    /**
     * Opens the native cutting or bone menu when required; attachments and stringing finish on item use.
     */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(recipe.used).onItem(recipe.target)) return false
        if (recipe.acheyProduct != null) {
            if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
            val units = recipe.materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
            handler.widgets.clickMakeItem(recipe.acheyProduct.ordinal, units)
        } else if (recipe.bones) {
            if (!waitFor(3.seconds) { ChiselBonesMenu::class in bot.overlays }) return false
            return bot.output.clickButton(ChiselBonesMenu.MAKE_ALL)
        }
        return true
    }

    override suspend fun finish() {
        if (recipe.acheyProduct != null) bot.actions.first(CutAcheyLogAction::class.java)?.interrupt()
        if (recipe.bones) bot.actions.first(ChiselBonesAction::class.java)?.interrupt()
    }

    override fun snapshot(): OgreFletchingData = OgreFletchingData().also {
        it.recipe = recipe.key
        saveInventoryState(it)
    }
}
