package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.bot.scripts.FillWaterBotScript
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.cookFood.MakeWineActionItem
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Assembles validated food recipes using owned input pairs and the existing player make dialogue.
 *
 * Supports plain-pizza assembly and toppings, chocolate cakes, milky nettle tea, pie shells, and all implemented
 * pie assembly stages, initial stew and nettle-water preparation, tea pouring, both stew completion paths,
 * and curry preparation. Cooking
 * requirements range from level 1 to 95. Meat, anchovy, and pineapple toppings award 26, 39, and 45 Cooking XP
 * respectively; chocolate cakes award 30 XP at level 50, while plain-pizza and pie assembly award none.
 * Chocolate bars and chocolate dust are interchangeable secondary inputs. Each step uses two inputs and creates
 * a product, plus an empty container when water or milk is used. Milky nettle tea requires level 20 and combines
 * an existing bowl of tea with one bucket of milk, returning the bucket and awarding no XP.
 * [PrepareFoodActionItem] owns ordinary conversions and returns. Wine uses [MakeWineActionItem], which
 * combines grapes and a jug of water at level 35. Fourteen pairs produce fourteen dynamic unfermented wines;
 * the existing fermentation task later awards 200 XP per jug. The jug remains in the wine product.
 * Ending this script interrupts mixing while allowing already-made wine to continue fermenting.
 * Stew completion requires level 25 and curry requires level 60; both award no XP. Curry consumes one spice
 * or three curry leaves per uncooked stew. Stews already containing potato accept either cooked meat alternative,
 * while stews already containing meat accept a potato. Starting a stew uses a bowl of water and potato or cooked
 * meat at level 25 for 2 XP. Adding nettles to a bowl of water requires level 20 and awards no XP. These products
 * retain the source bowl rather than returning an additional empty bowl.
 * Pouring a bowl of nettle tea into an empty cup requires level 20, returns the bowl, and awards the 52 XP
 * defined by the player preparation recipe. A full batch uses fourteen cups and fourteen bowls of tea.
 * Adding milk to an existing cup of nettle tea also requires level 20, returns the empty bucket, and awards no XP.
 * Empty cups cannot substitute for tea in this milk recipe.
 * Alternative meat, compost, and water inputs are selected explicitly. No recipe expands its inventory footprint.
 *
 * [InventoryBotScript] handles banking, travel, session expiry, and weak-action gating. Each bank batch contains
 * up to fourteen balanced pairs, or seven stews and twenty-one curry leaves. Missing startup ingredients request
 * total stock targets of 1,000. Three failed
 * interactions or unresolved bank requests stop the script; snapshots preserve the recipe and retry budgets.
 * Missing water can queue the reusable filling prerequisite from owned containers. Non-training Cooking selects
 * owned supplies or an available refill; training selects only recipes that award experience. Dough making and
 * baking remain separate activities, and higher-tier pie baking still requires entries in the cooking table.
 *
 * @param bot The bot assembling food.
 * @property food A supported preparation recipe.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Existing processing zones with banking support.
 * @property secondary The selected ingredient from the recipe alternatives; curry requires three leaves per operation.
 * @author lare96
 */
class AssembleFoodBotScript(
    bot: Bot,
    val food: IncompleteFood,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList(),
    val secondary: Int = food.otherIngredients.first()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Validated two-input recipes whose outputs fit within their input inventory footprint. */
        val RECIPES = setOf(
            IncompleteFood.INCOMPLETE_PIZZA, IncompleteFood.UNCOOKED_PLAIN_PIZZA,
            IncompleteFood.MEAT_PIZZA, IncompleteFood.ANCHOVY_PIZZA, IncompleteFood.PINEAPPLE_PIZZA,
            IncompleteFood.CHOCOLATE_CAKE, IncompleteFood.UNFERMENTED_WINE,
            IncompleteFood.MILKY_NETTLE_TEA,
            IncompleteFood.CUP_OF_NETTLE_TEA,
            IncompleteFood.CUP_OF_MILKY_NETTLE_TEA,
            IncompleteFood.NETTLE_WATER, IncompleteFood.INCOMPLETE_STEW_WITH_POTATO,
            IncompleteFood.INCOMPLETE_STEW_WITH_MEAT,
            IncompleteFood.UNCOOKED_STEW_FROM_MEAT, IncompleteFood.UNCOOKED_STEW_FROM_POTATO,
            IncompleteFood.UNCOOKED_CURRY,
            IncompleteFood.PIE_SHELL, IncompleteFood.UNCOOKED_BERRY_PIE, IncompleteFood.UNCOOKED_MEAT_PIE,
            IncompleteFood.PART_MUD_PIE_1, IncompleteFood.PART_MUD_PIE_2, IncompleteFood.RAW_MUD_PIE,
            IncompleteFood.UNCOOKED_APPLE_PIE,
            IncompleteFood.PART_GARDEN_PIE_1, IncompleteFood.PART_GARDEN_PIE_2, IncompleteFood.RAW_GARDEN_PIE,
            IncompleteFood.PART_FISH_PIE_1, IncompleteFood.PART_FISH_PIE_2, IncompleteFood.RAW_FISH_PIE,
            IncompleteFood.PART_ADMIRAL_PIE_1, IncompleteFood.PART_ADMIRAL_PIE_2, IncompleteFood.RAW_ADMIRAL_PIE,
            IncompleteFood.PART_SUMMER_PIE_1, IncompleteFood.PART_SUMMER_PIE_2, IncompleteFood.RAW_SUMMER_PIE
        )

        /**
         * Saved recipe and retry counters alongside the inherited duration and candidate zones.
         * Counters survive resumption so restoring a script does not reset an exhausted retry budget.
         *
         * @author lare96
         */
        class AssemblyData : InventoryScriptData() {
            /** [IncompleteFood] enum name used to reconstruct the configured recipe. */
            var recipe = ""
            /** Selected alternate ingredient or filled water container. */
            var secondary = 0

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                secondary = data.get("secondary")?.asInt ?: IncompleteFood.valueOf(recipe).otherIngredients.first()
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("secondary", secondary)
            }
        }
    }

    init {
        require(food in RECIPES)
        require(secondary in food.otherIngredients)
    }

    /** Permanent Cooking level required for selection and startup validation. */
    val requiredLevel = food.lvl
    /** Secondary quantity consumed by the player action: three curry leaves, or one of any other ingredient. */
    val secondaryAmount = if (food == IncompleteFood.UNCOOKED_CURRY && secondary == 5970) 3 else 1
    /** Minimum supplies for one conversion, including the player action's three-leaf curry requirement. */
    private val materials = listOf(Item(food.baseIngredient), Item(secondary, secondaryAmount))
    /** Filled water ingredient, whether the recipe uses it as its base or secondary input. */
    private val waterIngredient = materials.firstOrNull { it.id in WaterResource.FILLED_IDS }?.id

    /**
     * Restores the recipe, session configuration, and retry budgets from a snapshot.
     * Current supplies and bot safety are checked again by the normal initialization hooks.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized food-assembly state.
     */
    constructor(bot: Bot, data: AssemblyData) :
        this(bot, IncompleteFood.valueOf(data.recipe), data.duration, data.zones, data.secondary) {
        restoreInventoryState(data)
    }

    /**
     * Checks permanent level eligibility and one complete input pair across inventory and bank.
     * Temporary current-level, combat, lock, and strong-action restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_COOKING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Whether all other ingredients and an empty container are owned for a missing water input. */
    fun canPrepareWater(): Boolean {
        val water = waterIngredient ?: return false
        val supplies = materials.filter { it.id != water } + Item(WaterResource.FILLABLES.inverse().getValue(water))
        return bot.cooking.staticLevel >= requiredLevel && !bot.ownsProductionSupplies(listOf(Item(water))) &&
            bot.ownsProductionSupplies(supplies)
    }

    /** Queues one bounded refill using owned containers, then returns selection to the activity coordinator. */
    private fun queueWaterPreparation(): Boolean {
        if (!isInventoryActionSafe() || !canPrepareWater()) return false
        val water = waterIngredient ?: return false
        val empty = WaterResource.FILLABLES.inverse().getValue(water)
        val ingredientStock = materials.filter { it.id != water }.minOf {
            (bot.bank.computeAmountForId(it.id).toLong() + bot.inventory.computeAmountForId(it.id)) / it.amount
        }
        val emptyStock = bot.bank.computeAmountForId(empty).toLong() + bot.inventory.computeAmountForId(empty)
        bot.scriptStack.softPushHead(FillWaterBotScript(bot, empty, minOf(14L, ingredientStock, emptyStock).toInt(), duration))
        stop()
        return true
    }
    /** Returns balanced bank inputs limited by stock and capacity, including three leaves per curry. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, levelEligible = bot.skill(SKILL_COOKING).staticLevel >= requiredLevel, prepareMissing = { queueWaterPreparation() })

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty() && !queueWaterPreparation()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains enough of each ingredient for one operation of the configured recipe. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_COOKING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses the recipe ingredients, waits for its single-item dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(food.baseIngredient).onItem(secondary)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        if (food == IncompleteFood.UNFERMENTED_WINE) {
            bot.actions.first(MakeWineActionItem::class.java)?.interrupt()
        } else {
            bot.actions.first(PrepareFoodActionItem::class.java)?.takeIf { it.food == food }?.interrupt()
        }
    }

    override fun snapshot(): AssemblyData = AssemblyData().also {
        it.recipe = food.name
        it.secondary = secondary
        saveInventoryState(it)
    }
}
