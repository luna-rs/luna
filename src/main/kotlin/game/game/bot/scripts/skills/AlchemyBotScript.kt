package game.bot.scripts.skills

import api.bot.action.BotCombatActionHandler
import api.bot.action.BotBankSpace
import api.bot.action.sourcesForStock
import api.bot.action.stockIdFor
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.AlchemyPolicy
import api.bot.script.AlchemyShopOpportunities
import api.bot.script.ProductionPlan
import api.bot.script.ProductionEconomics
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.social.BotConversations
import api.bot.social.BotHomeGathering
import api.bot.social.SocialBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.totalItemAmount
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.speech.BotConversationPool.ConversationSpeech
import game.skill.fletching.stringBow.Bow
import game.skill.magic.CombinationRune
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.jewelleryMaking.GoldJewelleryTable
import game.skill.smithing.BarType
import io.luna.game.model.def.ItemDefinition
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.Staff
import game.skill.magic.SpellCost
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Trains Magic by casting low or high alchemy on sensible banked items.
 *
 * Alchable items are deliberately whitelisted so bots never decide to destroy arbitrary valuables. Bows may be alched
 * in either noted or unnoted form. Other targets are withdrawn and alched as notes.
 * The whitelist covers ordinary production items, including unenchanted gold jewellery and basic gear.
 *
 * Intelligent bots usually select the item with the best current alchemy margin using Luna's economy guide price.
 * Less intelligent bots are increasingly likely to pick a random valid item instead.
 *
 * The script consumes only surplus targets and immediately acquires affordable rune batches when supplies run out.
 * Magic profit sessions require a positive margin after target and native rune costs. All sessions protect reserves.
 * Noted target withdrawals always restore normal bank mode, including failed or cancelled withdrawals.
 * Missing runes request total stock targets of at least 1,000, scaled to all owned eligible materials,
 * preserving higher wanted targets and their metadata. Stock and rune arithmetic use Long before clamping
 * targets to the supported Int range. Owned unnoted armour and bars are counted for initial bank preparation.
 * Fire-rune readiness also counts base and fire-providing combination stocks with Long, including stock
 * split across inventory and bank, so large valid supplies cannot appear insufficient after overflow.
 *
 * @property type Whether this script casts low or high alchemy.
 * @author lare96
 */
class AlchemyBotScript(
    bot: Bot,
    val type: AlchemyType,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.zonesForSkill(SKILL_MAGIC),
    val profit: Boolean = false
) : ZonedBotScript(bot, duration, (listOf(SubZone.VARROCK_HOME_GATHERING) + zones).distinct().toMutableList()),
    SocialBotScript {

    companion object {

        private const val MAX_CASTS_PER_VISIT = 100

        /**
         * Ordinary metal equipment names. Exact matches exclude dragon, trimmed and other variants.
         */
        private val METAL_EQUIPMENT_NAMES = buildSet {
            val tiers = listOf("Bronze", "Iron", "Steel", "Black", "White", "Mithril", "Adamant", "Rune")
            val equipment = listOf("dagger", "sword", "longsword", "2h sword", "scimitar", "mace", "warhammer",
                "battleaxe", "axe", "pickaxe", "spear", "claws", "halberd", "throwing knife", "knife", "thrownaxe",
                "javelin", "dart", "arrow", "med helm", "full helm", "chainbody", "platebody", "platelegs",
                "plateskirt", "sq shield", "kiteshield", "boots", "gloves", "defender")
            for (tier in tiers) for (piece in equipment) add("$tier $piece")
        }

        /**
         * Ordinary production targets using native recipes and equipped item definitions.
         */
        private val ALCHABLE_IDS: Set<Int> by lazy {
            buildSet {
                addAll(BOW_IDS)
                addAll(BarType.ID_TO_BAR.keys)
                addAll(GoldJewelleryTable.ID_TO_JEWELLERY.keys)
                addAll(1692..1702 step 2)
                addAll(listOf(6577, 6581)) // Unenchanted onyx necklace and strung amulet.
                addAll(HideArmor.entries.filterNot { it.name.startsWith("SNAKESKIN_") }.map { it.id })
                addAll(listOf(1379, 1389, 1381, 1383, 1385, 1387, Battlestaff.BATTLESTAFF, 3053, 6562, 6603))
                addAll(Battlestaff.entries.map { it.staff })
                ItemDefinition.ALL.stream().filter { definition ->
                    definition.isTradeable && !definition.isNoted && definition.name in METAL_EQUIPMENT_NAMES &&
                        EquipmentDefinition.ALL.get(definition.id()).isPresent
                }.forEach { add(it.id()) }
            }
        }

        private fun ownedTargets(bot: Bot): Set<Int> =
            (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).mapNotNullTo(linkedSetOf()) {
                it.itemDef.unnotedId.orElse(it.id).takeIf(ALCHABLE_IDS::contains)
            }

        /**
         * Exposes the native disposal whitelist to production valuation without creating an alchemy session.
         * Availability and retained stock still require separate validation.
         */
        fun supportsLiquidation(id: Int): Boolean = itemDef(id).unnotedId.orElse(id) in ALCHABLE_IDS

        /**
         * Unstrung and strung bow ids that may be alched.
         */
        private val BOW_IDS: Set<Int> by lazy {
            buildSet {
                Bow.entries
                    .filter { it != Bow.ARROW_SHAFT }
                    .forEach { bow ->
                        add(bow.unstrung)

                        if (bow.strung != -1) {
                            add(bow.strung)
                        }
                    }
            }
        }

        /**
         * Rune ids that can satisfy a fire-rune requirement.
         *
         * Staff substitution is handled separately through [Staff].
         */
        private val FIRE_RUNE_IDS: Set<Int> by lazy {
            buildSet {
                add(Rune.FIRE.id)

                CombinationRune.entries
                    .filter { Rune.FIRE in it.represents }
                    .forEach { add(it.id) }
            }
        }

        /**
         * Serializable state for [AlchemyBotScript].
         */
        class AlchemyData : ZonedBotScriptData() {

            /**
             * The type of alchemy being performed.
             */
            var type: AlchemyType = AlchemyType.LOW

            /**
             * Legacy snapshots describe Magic training rather than a guaranteed profit activity.
             */
            var profit = false
            var castsRemaining = MAX_CASTS_PER_VISIT
            var batchItem = -1
            var batchRemaining = 0
            var marketPlan: ProductionPlan? = null

            /**
             * One absolute recovery deadline survives targeted shopping and restart snapshots.
             */
            var supplyDeadline = 0L

            /**
             * Rune ids whose immediate marketplace fallback has already been attempted for this shortage.
             */
            var tradedRunes: Set<Int> = emptySet()

            /**
             * Conservative paid or quoted rune unit costs remain attached to saved acquired stock.
             */
            var runePrices: Map<Int, Long> = emptyMap()

            /**
             * The current shortage's normal shopping allowance and verified spending.
             */
            var supplyBudget = -1L
            var supplySpent = 0L

            /**
             * Ownership and currency before a targeted child visit, used to verify its actual paid rune cost.
             */
            var pendingRune = -1
            var pendingRuneCount = 0L
            var pendingCoins = 0L

            override fun load(data: JsonObject) {
                super.load(data)
                type = AlchemyType.valueOf(data.get("type").asString)
                profit = data.get("profit")?.asBoolean ?: false
                castsRemaining = (data.get("casts_remaining")?.asInt ?: MAX_CASTS_PER_VISIT)
                    .coerceIn(0, MAX_CASTS_PER_VISIT)
                batchItem = data.get("batch_item")?.asInt ?: -1
                batchRemaining = (data.get("batch_remaining")?.asInt ?: 0).coerceIn(0, castsRemaining)
                marketPlan = data.getAsJsonObject("market_plan")?.let(ProductionPlan::load)
                supplyDeadline = data.get("supply_deadline")?.asLong ?: 0L
                tradedRunes = data.getAsJsonArray("traded_runes")?.map { it.asInt }?.toSet() ?: emptySet()
                runePrices = data.getAsJsonObject("rune_prices")?.entrySet()?.associate {
                    it.key.toInt() to it.value.asLong.coerceAtLeast(0)
                } ?: emptyMap()
                supplyBudget = data.get("supply_budget")?.asLong ?: -1L
                supplySpent = data.get("supply_spent")?.asLong?.coerceAtLeast(0) ?: 0L
                pendingRune = data.get("pending_rune")?.asInt ?: -1
                pendingRuneCount = data.get("pending_rune_count")?.asLong?.coerceAtLeast(0) ?: 0L
                pendingCoins = data.get("pending_coins")?.asLong?.coerceAtLeast(0) ?: 0L
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("type", type.name)
                data.addProperty("profit", profit)
                data.addProperty("casts_remaining", castsRemaining)
                data.addProperty("batch_item", batchItem)
                data.addProperty("batch_remaining", batchRemaining)
                marketPlan?.let { data.add("market_plan", it.save()) }
                data.addProperty("supply_deadline", supplyDeadline)
                data.add("traded_runes", JsonArray().also { array -> tradedRunes.forEach { array.add(it) } })
                data.add("rune_prices", JsonObject().also { prices ->
                    runePrices.forEach { (id, price) -> prices.addProperty(id.toString(), price) }
                })
                data.addProperty("supply_budget", supplyBudget)
                data.addProperty("supply_spent", supplySpent)
                data.addProperty("pending_rune", pendingRune)
                data.addProperty("pending_rune_count", pendingRuneCount)
                data.addProperty("pending_coins", pendingCoins)
            }
        }

        /**
         * Returns whether [bot] currently owns at least one item that can be used by an alchemy script.
         *
         * Both the inventory and bank are checked. Unnoted armour is considered valid because the script can bank it and
         * withdraw it again as a note before alching.
         *
         * @param bot The bot to check.
         * @return `true` if at least one valid alchemy target is available.
         */
        fun hasAlchableItems(bot: Bot): Boolean {
            val retained = BotBankSpace.retainedStock(bot)
            return ownedTargets(bot).any { AlchemyPolicy.surplus(bot, it, retained) > 0 }
        }

        /**
         * Checks usable surplus against the selected activity's economic policy before supplying it.
         */
        fun hasAlchableItems(bot: Bot, type: AlchemyType, profit: Boolean): Boolean {
            val targets = ownedTargets(bot)
            if (targets.isEmpty()) return false
            val staff = AlchemyPolicy.resolveFireStaff(bot)
            val retained = BotBankSpace.retainedStock(bot).toMutableMap()
            if (staff != null) retained[staff] = maxOf(retained[staff] ?: 0, 1)
            return targets.any { eligibleTarget(bot, it, type, profit, staff, retained = retained) }
        }

        /**
         * Applies the same target policy to every whitelisted item.
         * The native surplus policy still preserves equipped, dynamic, explicit and active production stock.
         */
        private fun eligibleTarget(bot: Bot, id: Int, type: AlchemyType, profit: Boolean,
                                   staffId: Int? = null, minimumPrices: Map<Int, Long> = emptyMap(),
                                   retained: Map<Int, Int>? = null, acquiredPrices: Map<Int, Long> = emptyMap()): Boolean =
            AlchemyPolicy.eligible(bot, id, type,
                profit, staffId, minimumPrices, retained, acquiredPrices)

        /**
         * Saved acquisition metadata must still describe this script's native whitelist, spell and payout.
         * A malformed or obsolete plan cannot exempt arbitrary recent purchases or enlarge a resumed budget.
         */
        internal fun validMarketPlan(plan: ProductionPlan, type: AlchemyType): Boolean {
            val recipe = plan.recipe
            val id = recipe.key.removePrefix("alchemy:${type.name}:").toIntOrNull() ?: return false
            if (recipe.key != "alchemy:${type.name}:$id" || id !in ALCHABLE_IDS ||
                plan.recipes.size != 1 || plan.training || recipe.skill != SKILL_MAGIC || recipe.level != type.level ||
                recipe.experience != type.xp || recipe.coinCost != 0L || recipe.productProbability != 1.0 ||
                recipe.tools.isNotEmpty() || recipe.materials[id] != 1 ||
                recipe.products != mapOf(995 to (itemDef(id).value * type.valueMultiplier).toInt().coerceAtLeast(1)) ||
                plan.maximumUnitPrices.keys != recipe.materials.keys) return false
            val runes = recipe.materials.filterKeys { it != id }
            val nature = type.requirements.filterIsInstance<RuneRequirement>().first { it.rune == Rune.NATURE }.amount
            val fire = type.requirements.filterIsInstance<RuneRequirement>().first { it.rune == Rune.FIRE }.amount
            if (runes[Rune.NATURE.id] != nature || runes.keys.any { it != Rune.NATURE.id && it !in FIRE_RUNE_IDS } ||
                runes.filterKeys { it in FIRE_RUNE_IDS }.values.sumOf { it.toLong() } !in setOf(0L, fire.toLong())) return false
            val complete = recipe.materials.entries.fold(0L) { total, (input, amount) ->
                ProductionEconomics.saturatingAdd(total,
                    ProductionEconomics.saturatingMultiply(plan.maximumUnitPrices.getValue(input), amount.toLong()))
            }
            return plan.maximumCoins <= ProductionEconomics.saturatingMultiply(complete, plan.operations.toLong())
        }
    }

    /**
     * Recreates an alchemy script from persisted data.
     */
    constructor(bot: Bot, data: AlchemyData) : this(bot, data.type, data.duration, data.zones, data.profit) {
        supplyDeadline = data.supplyDeadline
        tradedRunes.addAll(data.tradedRunes)
        runePrices.putAll(data.runePrices)
        supplyBudget = data.supplyBudget
        supplySpent = data.supplySpent
        pendingRune = data.pendingRune
        pendingRuneCount = data.pendingRuneCount
        pendingCoins = data.pendingCoins
        castsRemaining = data.castsRemaining
        batchItem = data.batchItem.takeIf { it in ALCHABLE_IDS } ?: -1
        batchRemaining = data.batchRemaining.takeIf { batchItem >= 0 } ?: 0
        marketPlan = data.marketPlan
    }

    /**
     * The item currently being alched in the inventory.
     */
    private var marketPlan: ProductionPlan? = null
    private var targetId: Int? = null
    private var castsRemaining = MAX_CASTS_PER_VISIT
    private var batchItem = -1
    private var batchRemaining = 0

    /**
     * Keeps this session's allocated targets in the common disposal reserve while shopping or paused.
     */
    internal fun allocatedStock(): Item? =
        if (batchItem >= 0 && batchRemaining > 0) Item(batchItem, batchRemaining) else null

    /**
     * Records one actual native cast synchronously with consuming the item.
     */
    internal fun recordCast(id: Int) {
        check(batchItem == baseId(id) && batchRemaining > 0 && castsRemaining > 0)
        batchRemaining--
        castsRemaining--
        marketPlan?.recordProduction(1)
    }

    private fun allocateTarget(id: Int, available: Long): Int {
        val base = baseId(id)
        val limit = minOf(castsRemaining.toLong(), marketPlan?.remainingOperations?.toLong() ?: Long.MAX_VALUE, available)
            .coerceAtLeast(0L).toInt()
        batchRemaining = if (batchItem == base && batchRemaining > 0) minOf(batchRemaining, limit) else limit
        batchItem = base
        return batchRemaining
    }

    /**
     * The fire-providing staff selected for this script, if one is available.
     */
    private var fireStaffId: Int? = null

    private val failedFireStaffs = hashSetOf<Int>()
    private var supplyDeadline = 0L
    private val tradedRunes = linkedSetOf<Int>()
    private var startupDeadline = 0L
    private val runePrices = hashMapOf<Int, Long>()
    private var supplyBudget = -1L
    private var supplySpent = 0L
    private var pendingRune = -1
    private var pendingRuneCount = 0L
    private var pendingCoins = 0L

    /**
     * Becomes true only after supplies and the actual work location have been prepared for a cast.
     * Public speech then advances independently while the native alchemy action and coroutine delays continue.
     */
    private var castingReady = false

    override fun selectWorkZone(): SubZone? =
        SubZone.VARROCK_HOME_GATHERING.takeIf { it in zones } ?: super.selectWorkZone()

    override fun ambientConversationTopic(): ConversationSpeech = ConversationSpeech.ALCHEMY

    override fun canTalkWhileWorking(): Boolean = true

    override fun canSocialize(): Boolean = castingReady && activeZone != null && !forceBanking &&
        isActionSafe() && bot.combat.target == null && bot.walking.isEmpty && !bot.navigator.isActive &&
        !bot.overlays.hasWindow() &&
        (if (activeZone == SubZone.VARROCK_HOME_GATHERING) BotHomeGathering.settled(this)
        else bot.position in activeZone!!.area)

    override suspend fun prepareMarketplace(marketplace: BotMarketplace?): Boolean {
        if (activeZone != SubZone.VARROCK_HOME_GATHERING) return super.prepareMarketplace(marketplace)
        if (BotHomeGathering.settled(this)) return true
        castingReady = false
        BotConversations.cancel(bot)
        if (bot.isLocked || bot.actions.size(ActionType.STRONG) > 0 || bot.actions.size(ActionType.WEAK) > 0) {
            return true
        }
        if (bot.position !in SubZone.VARROCK_HOME_GATHERING.area &&
            !handler.webWalkTo(SubZone.VARROCK_HOME_GATHERING.inside)) return false
        if (!isActionSafe()) return true
        val tile = BotHomeGathering.claim(this) ?: return false
        return handler.webWalkTo(tile) && isActionSafe() && bot.position == tile && BotHomeGathering.settled(this)
    }

    override fun onPaused() {
        castingReady = false
        BotConversations.cancel(bot)
        BotHomeGathering.release(this)
    }

    override suspend fun finish() {
        onPaused()
        if (!isPaused()) marketPlan?.let { BotTradeIntentions.clearProductionPlan(bot, it, this) }
    }

    override suspend fun beforeLeavingZone(): Boolean {
        onPaused()
        return true
    }

    override suspend fun equipment(): BotGearLocator? {
        // Do not let generic skilling gear replace a useful elemental staff.
        return null
    }

    /**
     * Leaves missing rune acquisition to initialization while checking the real owned target and account build.
     */
    fun isEligible(): Boolean {
        if (!BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) || bot.magic.staticLevel < type.level) return false
        if (hasAlchableItems(bot, type, profit)) return true
        if (marketPlan == null) marketPlan = AlchemyShopOpportunities.select(bot, type, ALCHABLE_IDS, profit)
        return marketPlan != null
    }

    /**
     * Revalidates queued native conversion at execution time, after all packet and cooldown delays.
     */
    fun canCastNative(id: Int, nativeType: AlchemyType): Boolean = type == nativeType && isRunning() &&
        bot.scriptStack.current() === this && supplySafe() && bot.spellbook == Spellbook.REGULAR &&
        batchItem == baseId(id) && batchRemaining > 0 &&
        id in bot.inventory && baseId(id) in ALCHABLE_IDS && isValidInventoryTarget(id) && eligible(id) &&
        Magic.spellCost(bot, type.level, type.requirements) is SpellCost.Castable

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (startupDeadline > 0L) chargeStartupTime()
        startupDeadline = System.currentTimeMillis() + duration.inWholeMilliseconds.coerceAtLeast(0)
        if (duration <= Duration.ZERO || castsRemaining == 0) return false
        if (marketPlan?.let { !validMarketPlan(it, type) } == true) return false
        if (pendingRune >= 0) {
            recordPurchase(pendingRune, pendingRuneCount, pendingCoins)
            pendingRune = -1
        }
        if (!BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC)) return false
        if (!handler.combat.switchSpellbook(Spellbook.REGULAR)) return false

        if (bot.magic.level < type.level) {
            bot.log("Magic level ${bot.magic.level} is too low for ${type.name.lowercase()} alchemy.")
            return false
        }

        fireStaffId = resolveFireStaff()
        if (marketPlan != null && !prepareTargetPurchases()) return false
        if (remainingAlchableCount() == 0L) {
            bot.log("No alchable items available. Ending script.")
            return false
        }
        val prepared = prepareSupplies(fireStaffId)
        chargeStartupTime()
        if (!prepared || !supplySafe() || remainingAlchableCount() == 0L) return false
        forceBanking = true
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        castingReady = false
        BotConversations.cancel(bot)
        BotHomeGathering.release(this)
        targetId = null

        /*
         * Make sure rune and staff withdrawals use normal withdrawal mode. The mode is switched to notes only when
         * withdrawing the actual alchemy target.
         */
        handler.banking.clickBankingMode(false)
        prepareFireStaff()
        if (!supplySafe()) return
        if (!prepareSupplies(bot.equipment.weapon?.id)) {
            if (!isPaused()) stop()
            return
        }
        if (!supplySafe()) return
        if (!bot.bank.isOpen && !handler.banking.travelToBankOpen()) { stop(); return }
        if (!supplySafe()) return
        handler.banking.clickBankingMode(false)

        if (Rune.NATURE.id in bot.bank) {
            handler.banking.withdrawAll(Rune.NATURE.id)
        }

        if (!hasEquippedFireStaff()) {
            for (id in FIRE_RUNE_IDS) {
                if (id in bot.bank) {
                    handler.banking.withdrawAll(id)
                }
            }
        }

        val baseId = selectBankTarget()
        if (baseId == null) {
            bot.log("No alchable items remain in the bank.")
            stop()
            return
        }

        val notedId = notedId(baseId)
        val room = if (notedId != null || itemDef(baseId).isStackable) Int.MAX_VALUE
            else bot.inventory.computeRemainingSize()

        handler.banking.clickBankingMode(notedId != null)
        val withdrawn = try {
            val amount = allocateTarget(baseId, minOf(bot.bank.computeAmountForId(baseId).toLong(), room.toLong(),
                AlchemyPolicy.surplus(bot, baseId, retainedStock())))
            amount > 0 && handler.banking.withdraw(Item(baseId, amount))
        } finally {
            handler.banking.clickBankingMode(false)
        }
        if (!withdrawn) {
            bot.log("Could not withdraw ${itemName(baseId)} for alchemy.")
            stop()
            return
        }

        targetId = (notedId ?: baseId).takeIf { it in bot.inventory }

        if (targetId == null) {
            bot.log("Alchemy target was not found after withdrawal.")
            stop()
            return
        }

        if (Magic.spellCost(bot, type.level, type.requirements) !is SpellCost.Castable) {
            bot.log("Could not carry the prepared alchemy rune supplies.")
            stop()
        }
    }

    override suspend fun executeInZone(): Boolean {
        castingReady = false
        if (!BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC)) { stop(); return false }
        if (activeZone == SubZone.VARROCK_HOME_GATHERING && !BotHomeGathering.settled(this)) return true
        val target = currentInventoryTarget()

        if (target == null) {
            if (hasBankedAlchableItems()) {
                forceBanking = true
            } else {
                bot.log("No alchable items remain. Ending script.")
                stop()
            }
            return true
        }

        targetId = target

        if (!prepareRunesForCast()) {
            return true
        }

        // Rune acquisition and staff preparation can suspend or deposit this target.
        if (!supplySafe() || target !in bot.inventory || !eligible(target)) return true

        if (Magic.checkRequirements(bot, type.level, type.requirements) == null) {
            bot.log("Alchemy requirements unexpectedly failed after rune preparation.")
            return true
        }

        if (allocateTarget(target, AlchemyPolicy.surplus(bot, target, retainedStock())) < 1) return true
        castingReady = true
        val before = bot.inventory.computeAmountForId(target)

        if (!handler.inventory.useSpellOnItem(type.spellId, target)) {
            bot.log("Could not cast ${type.name.lowercase()} alchemy on ${itemName(target)}.")
            return true
        }

        if (!waitFor(5.seconds) { bot.inventory.computeAmountForId(target) < before }) {
            bot.log("Alchemy cast did not consume ${itemName(target)}.")
            return true
        }

        if (castsRemaining == 0 || marketPlan?.remainingOperations == 0) {
            stop()
            return true
        }

        supplyDeadline = 0L
        tradedRunes.clear()
        supplyBudget = -1L
        supplySpent = 0L

        bot.naturalDexterityDelay()

        if (target !in bot.inventory) {
            targetId = null

            if (currentInventoryTarget() == null) {
                if (hasBankedAlchableItems()) {
                    forceBanking = true
                } else {
                    bot.log("Finished all available alchable items.")
                    stop()
                }
            }
        }

        return true
    }

    override fun snapshot(): BotScriptData {
        chargeStartupTime()
        val data = AlchemyData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.type = type
        data.profit = profit
        data.castsRemaining = castsRemaining
        data.batchItem = batchItem
        data.batchRemaining = batchRemaining
        data.marketPlan = marketPlan
        data.supplyDeadline = supplyDeadline
        data.tradedRunes = tradedRunes.toSet()
        data.runePrices = runePrices.toMap()
        data.supplyBudget = supplyBudget
        data.supplySpent = supplySpent
        data.pendingRune = pendingRune
        data.pendingRuneCount = pendingRuneCount
        data.pendingCoins = pendingCoins
        return data
    }

    /**
     * Selects an alchable item from the bank.
     *
     * Intelligent bots usually choose the item with the best alchemy margin. Lower-intelligence bots increasingly choose
     * randomly instead.
     */
    private fun selectBankTarget(): Int? {
        val candidates = eligibleTargets().filter { id ->
            bot.bank.computeAmountForId(id) > 0 &&
                bot.bank.none { it != null && it.id == id && it.isDynamic }
        }

        if (candidates.isEmpty()) {
            return null
        }

        if (!profit || !rand(bot.personality.intelligence)) {
            return candidates.random()
        }

        return candidates.maxByOrNull { AlchemyPolicy.margin(bot, it, type, minimumPrices = runePrices, acquiredPrices = acquisitionPrices()) }
    }

    /**
     * Returns the current inventory alchemy target.
     *
     * Bows may be alched noted or unnoted. Armour must be noted.
     */
    private fun currentInventoryTarget(): Int? {
        val staff = resolveFireStaff()
        targetId?.let {
            if (it in bot.inventory && isValidInventoryTarget(it) && eligible(it, staff)) {
                return it
            }
        }

        val candidates = bot.inventory.filterNotNull().map { it.id }.distinct()
            .filter(::isValidInventoryTarget).toMutableList()

        val retained = retainedStock()
        candidates.removeAll { !marketTargetAllowed(it) ||
            !eligibleTarget(bot, it, type, profit, staff, runePrices, retained, acquisitionPrices()) }
        if (candidates.isEmpty()) {
            return null
        }

        return if (profit && rand(bot.personality.intelligence)) {
            candidates.maxByOrNull { AlchemyPolicy.margin(bot, it, type, staff, runePrices, acquisitionPrices()) }
        } else {
            candidates.random()
        }
    }

    /**
     * Ensures enough runes are in the inventory for one cast.
     */
    private suspend fun prepareRunesForCast(): Boolean {
        if (!hasEquippedFireStaff() && resolveFireStaff() != null) {
            forceBanking = true
            return false
        }
        if (Magic.spellCost(bot, type.level, type.requirements) is SpellCost.Castable) return true
        castingReady = false
        BotConversations.cancel(bot)
        BotHomeGathering.release(this)
        fireStaffId = resolveFireStaff()
        if (!prepareSupplies(fireStaffId)) {
            if (!isPaused()) stop()
            return false
        }
        // Acquisition may have travelled or deposited targets. Always complete normal banking before another cast.
        forceBanking = true
        return false
    }

    /**
     * Equips an owned staff that supplies fire runes when one is available.
     */
    private suspend fun prepareFireStaff() {
        val staffId = resolveFireStaff() ?: return
        fireStaffId = staffId

        if (bot.equipment.weapon?.id == staffId) {
            return
        }

        if (staffId !in bot.inventory && !handler.banking.withdraw(Item(staffId))) {
            bot.log("Could not withdraw ${itemName(staffId)}.")
            fireStaffId = null
            return
        }

        if (!handler.equipment.equip(staffId)) {
            bot.log("Could not equip ${itemName(staffId)}.")
            fireStaffId = null
            failedFireStaffs.add(staffId)
        }
    }

    /**
     * Finds a fire-providing staff the bot owns and can equip.
     */
    private fun resolveFireStaff(): Int? {
        val equipped = bot.equipment.weapon?.id
        if (equipped != null && providesFireRunes(equipped)) {
            return equipped
        }

        return Staff.entries
            .asSequence()
            .filter { Rune.FIRE in it.represents }
            .flatMap { it.ids.asSequence() }
            .filter { it !in failedFireStaffs }
            .filter { it in bot.itemTracker }
            .firstOrNull { canEquip(it) }
    }

    private fun canEquip(id: Int): Boolean {
        return EquipmentDefinition.ALL.get(id)
            .map { it.meetsAllRequirements(bot) }
            .orElse(false)
    }

    private fun providesFireRunes(id: Int): Boolean {
        return Staff.ID_TO_STAFF[id]?.let { Rune.FIRE in it.represents } == true
    }

    private fun hasEquippedFireStaff(): Boolean {
        val weapon = bot.equipment.weapon?.id ?: return false
        return providesFireRunes(weapon)
    }

    private fun inventoryFireRunes(): Long {
        return FIRE_RUNE_IDS.sumOf { bot.inventory.computeAmountForId(it).toLong() }
    }

    private fun bankedFireRunes(): Long {
        return FIRE_RUNE_IDS.sumOf { bot.bank.computeAmountForId(it).toLong() }
    }

    private fun natureRunesPerCast(): Int {
        return type.requirements
            .filterIsInstance<RuneRequirement>()
            .first { it.rune == Rune.NATURE }
            .amount
    }

    private fun fireRunesPerCast(): Int {
        return type.requirements
            .filterIsInstance<RuneRequirement>()
            .first { it.rune == Rune.FIRE }
            .amount
    }

    /**
     * Charges initialization and child-shopping time as well as ordinary zoned work.
     */
    private fun chargeStartupTime() {
        val remaining = remainingDuration()
        if (startupDeadline > 0L) {
            duration = minOf(remaining, (startupDeadline - System.currentTimeMillis()).milliseconds)
        }
    }

    private fun supplySafe(): Boolean {
        chargeStartupTime()
        return duration > Duration.ZERO && castsRemaining > 0 && !isPaused() && !isTerminated() &&
            bot.state == EntityState.ACTIVE && isActionSafe() &&
            BotCombatActionHandler.mayGainExperience(bot, SKILL_MAGIC) && bot.magic.level >= type.level
    }

    /**
     * Tests the native combined stock calculation, including fragmented combination runes and an owned staff.
     */
    private fun ownsCast(staffId: Int?): Boolean = Magic.spellCost(bot.magic.level,
        bot.inventory.filterNotNull() + bot.bank.filterNotNull(), { bot.equipment.contains(it) },
        staffId, type.level, type.requirements) is SpellCost.Castable

    /**
     * Total owned base-rune targets for a useful batch. Existing combination stocks satisfy fire first.
     */
    private fun purchaseTargets(casts: Int, staffId: Int?): Map<Int, Int> = buildMap {
        put(Rune.NATURE.id, natureRunesPerCast() * casts)
        if (staffId == null || !providesFireRunes(staffId)) {
            val baseFire = bot.inventory.computeAmountForId(Rune.FIRE.id).toLong() +
                bot.bank.computeAmountForId(Rune.FIRE.id)
            val missing = (fireRunesPerCast().toLong() * casts - inventoryFireRunes() - bankedFireRunes())
                .coerceAtLeast(0)
            if (baseFire + missing > 0L) {
                put(Rune.FIRE.id, (baseFire + missing).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
        }
    }

    /**
     * Quotes the whole missing basket against current native shop stock and each real currency balance.
     * Prepared marketplace sellers may cover unavailable shop stock; their maximum buying premium is budgeted.
     */
    private fun purchasePrices(targets: Map<Int, Int>): Map<Int, Long>? {
        fun owned(id: Int) = bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)
        fun budget() = (supplyBudget - supplySpent).coerceAtLeast(0)
        val costs = hashMapOf<Int, Long>()
        val prices = hashMapOf<Int, Long>()
        for ((id, amount) in targets) {
            var missing = (amount - owned(id)).coerceAtLeast(0)
            if (missing == 0L) continue
            val shops = bot.world.shops.sourcesForStock(id).asSequence()
                .filter { it.currency.id == 995 && it.npcSellers.isNotEmpty() }
                .map { shop -> shop to shop.stockIdFor(id) }
                .sortedBy { (shop, stockId) -> shop.computeBuyValue(stockId, 1).orElse(Int.MAX_VALUE) }
            for ((shop, stockId) in shops) {
                val quantity = minOf(missing, shop.items.computeAmountForId(stockId).toLong()).toInt()
                if (quantity < 1) continue
                val quote = shop.computeBuyValue(stockId, quantity).orElse(-1)
                if (quote < 0) continue
                val cost = quote.toLong()
                val currency = shop.currency.id
                val total = (costs[currency] ?: 0L) + cost
                if (total > minOf(owned(currency), budget())) return null
                costs[currency] = total
                val currencyPrice = if (currency == 995) 1L else world.economy.getPrice(currency).coerceAtLeast(1)
                if (cost > 0 && currencyPrice > Long.MAX_VALUE / cost) return null
                val coinCost = cost * currencyPrice
                val unit = coinCost / quantity + if (coinCost % quantity > 0) 1 else 0
                prices[id] = maxOf(prices[id] ?: 0L, unit, 1L)
                missing -= quantity
                if (missing == 0L) break
            }
            if (missing > 0L) {
                val guide = world.economy.getPrice(id)
                if (guide <= 0L) return null
                val premium = BotTradePrice.computePriceStrategy(bot).buy.endInclusive
                val price = (guide.toDouble() * (1.0 + premium)).toLong().coerceAtLeast(1)
                if (price > (minOf(owned(995), budget()) - (costs[995] ?: 0L)) / missing) return null
                costs[995] = (costs[995] ?: 0L) + price * missing
                prices[id] = maxOf(prices[id] ?: 0L, price)
            }
        }
        return prices
    }

    /**
     * Immediately obtains a bounded affordable batch before startup rejection or a mid-session stop.
     * One absolute deadline covers a targeted marketplace visit before shop travel for each missing rune.
     * Partial purchases remain useful, while every resumption and completed suspension refreshes target reserves.
     */
    internal suspend fun prepareSupplies(staffId: Int? = fireStaffId): Boolean {
        if (!supplySafe() || eligibleTargets(staffId).isEmpty()) return false
        if (ownsCast(staffId)) return true
        val now = System.currentTimeMillis()
        if (supplyDeadline == 0L) supplyDeadline = now + minOf(120_000L, duration.inWholeMilliseconds)
        if (now >= supplyDeadline) return false
        if (supplyBudget < 0L) {
            supplyBudget = (ownedCoins() * BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong()
        }
        if (bot.inventory.computeAmountForId(Rune.NATURE.id).toLong() + bot.bank.computeAmountForId(Rune.NATURE.id) <
            natureRunesPerCast()) requestNatureRunes()
        if ((staffId == null || !providesFireRunes(staffId)) &&
            inventoryFireRunes() + bankedFireRunes() < fireRunesPerCast()) requestFireRunes()

        val limit = minOf(castsRemaining.toLong(), remainingAlchableCount(), (duration.inWholeSeconds / 3).coerceAtLeast(1)).toInt()
        var casts = 0
        var upper = limit
        while (casts < upper) {
            val candidate = casts + (upper - casts + 1) / 2
            val prices = purchasePrices(purchaseTargets(candidate, staffId))
            if (prices != null && eligibleTargets(staffId, prices).isNotEmpty()) casts = candidate
            else upper = candidate - 1
        }
        if (casts == 0) {
            queueMinimumTrade(staffId)
            return false
        }
        val targets = purchaseTargets(casts, staffId)
        for ((id, target) in targets) {
            if (id in tradedRunes || bot.inventory.computeAmountForId(id).toLong() +
                bot.bank.computeAmountForId(id) >= target) continue
            tradedRunes.add(id)
            val trade = handler.shop.wantedItemTrade(id, target, supplyDeadline) ?: continue
            if (!supplySafe() || eligibleTargets(staffId).isEmpty()) return false
            val prices = purchasePrices(targets) ?: return false
            if (eligibleTargets(staffId, prices).isEmpty()) return false
            runePrices[id] = maxOf(runePrices[id] ?: 0L, prices[id] ?: 0L)
            val visit = minOf(duration, 30.seconds, (supplyDeadline - System.currentTimeMillis()).milliseconds)
            if (visit <= Duration.ZERO) return false
            pendingRune = id
            pendingRuneCount = ownedRune(id)
            pendingCoins = ownedCoins()
            handler.shop.obtainWantedItem(trade, visit)
            return false
        }
        withTimeoutOrNull((supplyDeadline - System.currentTimeMillis()).coerceAtLeast(1)) {
            for ((id, target) in targets) {
                val before = ownedRune(id)
                if (before >= target) continue
                val prices = purchasePrices(targets) ?: break
                if (!supplySafe() || eligibleTargets(staffId, prices).isEmpty()) break
                runePrices[id] = maxOf(runePrices[id] ?: 0L, prices[id] ?: 0L)
                val coins = ownedCoins()
                try {
                    val budget = (supplyBudget - supplySpent).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    handler.shop.obtainWantedItems(mapOf(id to target), budget, prices)
                } finally {
                    recordPurchase(id, before, coins)
                }
            }
        }
        if (!supplySafe() || eligibleTargets(staffId).isEmpty()) return false
        if (ownsCast(staffId)) return true
        return false
    }

    /**
     * Tries one useful cast from actual prepared sellers when stocked shops cannot fund an acceptable batch.
     * Every missing rune must have a ready match and fit the same remaining budget before the first child is queued.
     */
    private fun queueMinimumTrade(staffId: Int?): Boolean {
        if (!supplySafe() || System.currentTimeMillis() >= supplyDeadline) return false
        val missing = purchaseTargets(1, staffId).filter { (id, target) -> ownedRune(id) < target }
        if (missing.isEmpty()) return false
        val prices = hashMapOf<Int, Long>()
        var budget = minOf(ownedCoins(), (supplyBudget - supplySpent).coerceAtLeast(0))
        for ((id, target) in missing) {
            val guide = world.economy.getPrice(id)
            if (guide <= 0L) return false
            val premium = (guide * BotTradePrice.computePriceStrategy(bot).buy.endInclusive).toLong()
            if (premium > Long.MAX_VALUE - guide) return false
            val price = guide + premium
            val quantity = target - ownedRune(id)
            if (price > budget / quantity) return false
            budget -= price * quantity
            prices[id] = price
        }
        if (eligibleTargets(staffId, prices).isEmpty()) return false
        val ready = missing.map { (id, target) ->
            id to (handler.shop.wantedItemTrade(id, target, supplyDeadline) ?: return false)
        }
        val (id, trade) = ready.firstOrNull { it.first !in tradedRunes } ?: return false
        if (!supplySafe() || eligibleTargets(staffId, prices).isEmpty()) return false
        val visit = minOf(duration, (supplyDeadline - System.currentTimeMillis()).milliseconds)
        if (visit <= Duration.ZERO) return false
        tradedRunes.add(id)
        runePrices[id] = maxOf(runePrices[id] ?: 0L, prices.getValue(id))
        pendingRune = id
        pendingRuneCount = ownedRune(id)
        pendingCoins = ownedCoins()
        return handler.shop.obtainWantedItem(trade, visit)
    }

    /**
     * Requests enough nature runes to alch the bot's remaining valid items.
     */
    private fun requestNatureRunes() {
        val amount = runeStockTarget(natureRunesPerCast())

        bot.log("Not enough nature runes. Adding $amount to wanted items.")
        bot.preferences.raiseWantedItemTarget(Rune.NATURE.id, amount)
        BotTradeIntentions.recordSupplies(bot,
            listOf(Item(Rune.NATURE.id, natureRunesPerCast())), stockTarget = minOf(amount, 1_000))
    }

    /**
     * Requests enough fire runes to alch the bot's remaining valid items.
     *
     * This is only used when no usable fire-providing staff is available.
     */
    private fun requestFireRunes() {
        val amount = runeStockTarget(fireRunesPerCast())

        bot.log("Not enough fire runes. Adding $amount to wanted items.")
        bot.preferences.raiseWantedItemTarget(Rune.FIRE.id, amount)
        BotTradeIntentions.recordSupplies(bot,
            listOf(Item(Rune.FIRE.id, fireRunesPerCast())), stockTarget = minOf(amount, 1_000))
    }

    /**
     * Total rune stock needed for owned targets, with a useful bulk minimum and overflow-safe clamping.
     */
    private fun runeStockTarget(perCast: Int): Int =
        (remainingAlchableCount() * perCast).coerceIn(1_000L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Counts banked items and carried noted/unnoted items, including materials that need banking first.
     */
    private fun remainingAlchableCount(): Long {
        val retained = retainedStock()
        return eligibleTargets(fireStaffId, retained = retained).sumOf { AlchemyPolicy.surplus(bot, it, retained) }
    }

    private fun hasBankedAlchableItems(): Boolean {
        return eligibleTargets(resolveFireStaff()).any { bot.bank.computeAmountForId(it) > 0 }
    }

    private fun isValidInventoryTarget(id: Int): Boolean {
        val baseId = baseId(id)

        return eligible(id, resolveFireStaff()) && (baseId in BOW_IDS ||
            baseId in ALCHABLE_IDS && (itemDef(id).isNoted || notedId(baseId) == null))
    }

    private fun baseId(id: Int): Int {
        return itemDef(id).unnotedId.orElse(id)
    }

    private fun notedId(id: Int): Int? {
        val noted = itemDef(id).notedId
        return if (noted.isPresent) noted.asInt else null
    }

    private fun eligible(baseId: Int, staffId: Int? = null, prices: Map<Int, Long> = emptyMap()): Boolean =
        marketTargetAllowed(baseId) &&
            eligibleTarget(bot, baseId, type, profit, staffId, priceFloors(prices), retainedStock(), acquisitionPrices())

    private fun ownedCoins(): Long = bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)

    private fun ownedRune(id: Int): Long = bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)

    private fun recordPurchase(id: Int, before: Long, coins: Long) {
        val spent = (coins - ownedCoins()).coerceAtLeast(0)
        supplySpent += spent
        val acquired = (ownedRune(id) - before).coerceAtLeast(0)
        if (acquired > 0) {
            val unit = spent / acquired + if (spent % acquired > 0) 1 else 0
            runePrices[id] = maxOf(runePrices[id] ?: 0L, unit)
        }
    }

    private fun acquisitionPrices(): Map<Int, Long> = marketPlan?.maximumUnitPrices ?: emptyMap()

    private fun marketTargetAllowed(id: Int): Boolean = marketPlan?.let { plan ->
        plan.remainingOperations > 0 && System.currentTimeMillis() < plan.deadline &&
            plan.recipe.key.substringAfterLast(':').toIntOrNull() == baseId(id)
    } ?: true

    private fun retainedStock(): Map<Int, Int> = BotBankSpace.retainedStock(bot, this, marketPlan).withWorkingStaff()

    private fun Map<Int, Int>.withWorkingStaff(): Map<Int, Int> {
        val staff = resolveFireStaff() ?: return this
        return if ((this[staff] ?: 0) > 0) this else this + (staff to 1)
    }

    private fun priceFloors(prices: Map<Int, Long>): Map<Int, Long> = (runePrices.keys + prices.keys).associateWith {
        maxOf(runePrices[it] ?: 0L, prices[it] ?: 0L)
    }

    private fun eligibleTargets(staffId: Int? = null, prices: Map<Int, Long> = emptyMap(),
                                retained: Map<Int, Int> = retainedStock()): List<Int> =
        ownedTargets(bot).filter { marketTargetAllowed(it) &&
            eligibleTarget(bot, it, type, profit, staffId, priceFloors(prices), retained, acquisitionPrices()) }

    /**
     * Buys the selected target and its rune basket through the existing trade and quoted shop tiers.
     */
    private suspend fun prepareTargetPurchases(): Boolean {
        val plan = marketPlan ?: return true
        val now = System.currentTimeMillis()
        if (!supplySafe() || now >= plan.deadline || plan.remainingOperations < 1) return false
        AlchemyShopOpportunities.attempted(bot, now)
        if (plan.supplyDeadline == 0L) plan.supplyDeadline = minOf(plan.deadline, now + 120_000L)
        BotTradeIntentions.recordProductionPlan(bot, plan, this)
        val target = plan.recipe.materials.keys.first()
        val note = itemDef(target).notedId.orElse(-1)
        val remainingAtStart = plan.supplyDeadline - System.currentTimeMillis()
        if (note >= 0 && bot.inventory.contains(note) &&
            (remainingAtStart <= 0L || withTimeoutOrNull(remainingAtStart) {
                handler.banking.travelToBankDepositAll()
            } != true)) return false
        if (remainingAlchableCount() > 0L && ownsCast(fireStaffId)) return true
        val targets = plan.targets()
        for ((id, amount) in targets) {
            if (bot.totalItemAmount(id) >= amount || !plan.attemptedMarketItems.add(id)) continue
            if (handler.shop.obtainWantedItem(plan, id, amount, duration)) return false
        }
        val remaining = plan.supplyDeadline - System.currentTimeMillis()
        if (!plan.shopAttempted && remaining > 0L && plan.remainingCoins > 0L) {
            plan.shopAttempted = true
            withTimeoutOrNull(remaining) {
                handler.shop.obtainWantedItems(targets, plan.remainingCoins
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), plan.maximumUnitPrices)
            }
        }
        return supplySafe() && remainingAlchableCount() > 0L && ownsCast(fireStaffId)
    }
}
