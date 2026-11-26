package game.bot.scripts.skills

import api.bot.action.BotNpcDialogues
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import com.google.gson.JsonObject
import game.npc.spawn.rellekka.Fremennik
import game.npc.spawn.rellekka.FremennikArmour
import game.npc.spawn.wizardsTower.SplitbarkArmour
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Banks materials and fees, then commissions a specific piece through the maker's player dialogue.
 *
 * @author lare96
 */
class CommissionArmourBotScript(
    bot: Bot, private val recipe: Recipe, duration: Duration
) : InventoryBotScript(bot, duration, mutableListOf(recipe.zone)) {

    /**
     * Production inputs and the native dialogue option for one piece of armour.
     *
     * @author lare96
     */
    class Recipe(
        val product: Int, val materials: List<Item>, val coins: Int, val npcId: Int, val zone: SubZone,
        val optionText: () -> String
    ) {
        fun isAvailable(bot: Bot): Boolean =
            zone != SubZone.RELLEKKA_ARMOUR_MAKERS || Fremennik.isFremennik(bot)
    }

    companion object {
        private const val COINS = 995

        val RECIPES = buildMap {
            for (piece in SplitbarkArmour.entries) {
                put(piece.product, Recipe(piece.product, piece.materials(), piece.coins, SplitbarkArmour.WIZARD,
                    SubZone.WIZARDS_TOWER_ARMOUR, piece::optionText))
            }
            for (maker in FremennikArmour.entries) {
                for (piece in maker.pieces) {
                    val materials = listOf(Item(FremennikArmour.DAGANNOTH_HIDE, piece.hides), Item(piece.part))
                    put(piece.product, Recipe(piece.product, materials, piece.coins, maker.npc,
                        SubZone.RELLEKKA_ARMOUR_MAKERS, piece::optionText))
                }
            }
        }

        /**
         * Keeps the chosen piece and inherited production plan and retry counters across login.
         *
         * @author lare96
         */
        class CommissionData : InventoryScriptData() {
            var product: Int = -1

            override fun load(data: JsonObject) {
                super.load(data)
                product = data.get("product").asInt
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("product", product)
            }
        }
    }

    constructor(bot: Bot, data: CommissionData) : this(bot, RECIPES.getValue(data.product), data.duration) {
        restoreInventoryState(data)
    }

    override val verifiedProductionWithdrawals = true
    private val inputs = recipe.materials + Item(COINS, recipe.coins)

    override suspend fun equipment() = null

    override fun withdraw(): List<Item> =
        productionWithdraw(inputs, levelEligible = recipe.isAvailable(bot), requestMissing = false)

    override fun bankWithdraw(): List<Item> {
        val affordable = bot.bank.computeAmountForId(COINS) / recipe.coins
        val batch = if (affordable > 0) bot.productionBatch(recipe.materials, setOf(COINS)) else emptyList()
        if (batch.isEmpty()) {
            stop()
            return emptyList()
        }
        val first = recipe.materials.first()
        val operations = minOf(affordable, batch.first { it.id == first.id }.amount / first.amount)
        return recipe.materials.map { Item(it.id, it.amount * operations) } + Item(COINS, recipe.coins * operations)
    }

    private fun hasInputs() = bot.inventory.containsAll(inputs)

    override suspend fun onInventoryBankRequested() = requestProductionBank(hasInputs())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasInputs(), levelEligible = recipe.isAvailable(bot))) return true
        val area = recipe.zone.area
        val npc = bot.world.locator.findNearestNpc(area.centerPosition, area.tileRadius) {
            it.id == recipe.npcId && it.state == EntityState.ACTIVE
        }
        val approached = npc != null && handler.interactions.approach(npc, timeout = 60.seconds,
            allowOutOfView = true, canContinue = { isActionSafe() && hasInputs() && recipe.isAvailable(bot) })
        val input = recipe.materials.first().id
        val before = bot.inventory.computeAmountForId(recipe.product)
        attemptProduction(input, available = approached, outputId = recipe.product) {
            npc != null && BotNpcDialogues.respond(bot, npc, listOf(recipe.optionText()),
                allowed = { isActionSafe() && recipe.isAvailable(bot) },
                completed = { bot.inventory.computeAmountForId(recipe.product) > before }) {
                handler.inventory.useItem(input).onNpc(npc)
            }
        }
        return true
    }

    override fun snapshot() = CommissionData().also {
        saveInventoryState(it)
        it.product = recipe.product
    }
}
