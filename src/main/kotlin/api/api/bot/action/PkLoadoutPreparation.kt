package api.bot.action

import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript.ActionSafety
import api.combat.death.PlayerDeathItems
import engine.bot.coordinator.EquipmentDropSources
import engine.bot.gear.BotGearPurpose
import engine.controllers.Controllers.inWilderness
import game.item.degradable.jewellery.TeleportJewellery
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.magic.Rune
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainer.StackPolicy
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import java.math.BigInteger

/**
 * Packs compact, owned PK switches through ordinary banking and equipment actions.
 * One supplied saved discipline is enough to depart; additional weapons and body/leg switches are optional.
 * Other slots share gear. Food is filled towards sixteen before optional armour and longer supply stacks.
 * Two inventory slots remain free in every modeled outfit for native equipment displacement.
 *
 * @author lare96
 */
internal class PkLoadoutPreparation(private val bot: Bot, private val handler: BotActionHandler) {
    private val clan = PkClanPolicy(bot)
    private val selection = PkLoadoutSelection(bot)

    companion object {
        const val FOOD_TARGET = 16

        private val boosts by lazy {
            listOf(listOf(Potion.SUPER_STRENGTH, Potion.STRENGTH_POTION),
                listOf(Potion.SUPER_ATTACK, Potion.ATTACK_POTION), listOf(Potion.SUPER_DEFENCE, Potion.DEFENCE_POTION))
        }
        private val optionalPotionIds by lazy {
            (boosts.flatten() + PkPotionUse.supportPotions).flatMapTo(HashSet()) { it.doses }
        }
    }

    /**
     * A concrete departure outfit and inventory, checked against physical ownership and prospective death loss.
     *
     * @author lare96
     */
    data class Plan(val equipment: List<Item>, val inventory: List<Item>, val potionSlots: Int,
                    val melee: Boolean = false, val prayerBonus: Int = 0)

    /**
     * Tests for any permanently assigned discipline with a carried weapon and its actual ammunition or spell cast.
     * Food exhaustion triggers a return to bank; no saved identity is changed because stock ran out.
     */
    fun ready(allowMagic: Boolean = true): Boolean {
        val purposes = purposes()
        return clan.wearingCape() && purposes.isNotEmpty() && bot.inventory.any {
            it != null && Food.ID_TO_FOOD[it.id]?.heal?.let { heal -> heal > 0 } == true
        } && purposes.any { (allowMagic || it != BotGearPurpose.MAGIC) &&
            selection.select(it, carriedOnly = true)?.gear?.get(Equipment.WEAPON) != null }
    }

    /**
     * Captures whether the prepared carried outfit has fifty actual Magic casts before an outing starts.
     * Ongoing readiness continues to accept one remaining cast after the departure supplies have been consumed.
     */
    fun hasCarriedMagicForDeparture(): Boolean = selection.select(BotGearPurpose.MAGIC, carriedOnly = true,
        minimumCasts = PkLoadoutSelection.MIN_CASTS) != null

    /**
     * Resolves saved disciplines without treating a build's incidental training skills as additional PK styles.
     */
    private fun purposes(): List<BotGearPurpose> {
        return selection.purposes()
    }

    /**
     * Hybrids short of casts prepare a legal melee or ranged kit. Mage-only profiles can procure a Magic kit.
     */
    private fun departurePurposes(spellbook: Spellbook): List<BotGearPurpose> {
        val purposes = purposes()
        return if (purposes.size > 1 && BotGearPurpose.MAGIC in purposes &&
            selection.select(BotGearPurpose.MAGIC, spellbook = spellbook) == null)
            purposes.filterNot { it == BotGearPurpose.MAGIC } else purposes
    }

    /**
     * Eligible spellbooks are inspected without changing the bot's live book or its permanent profile.
     */
    private fun magicSpellbooks(): List<Spellbook> =
        listOf(bot.spellbook, Spellbook.REGULAR, Spellbook.ANCIENT).distinct().filter {
            BotCombatActionHandler.canUseSpellbook(bot, it)
        }

    /**
     * Returns full rune stock targets for fifty casts, preserving an eligible physically owned staff first.
     * Staffless accounts request a legitimate staff and its runes through shared wanted-item acquisition.
     * An empty map means an eligible spellbook is already supplied; null means no eligible procurement target exists.
     */
    fun magicSupplyRequirements(): Map<Int, Int>? {
        if (BotGearPurpose.MAGIC !in purposes()) return null
        val books = magicSpellbooks()
        if (books.any { selection.select(BotGearPurpose.MAGIC, spellbook = it) != null }) return emptyMap()
        val acquisition = PkSupplyAcquisition(bot, handler)
        val quote = acquisition.purchaseQuotes()
        books.mapNotNull { book ->
            quotedMagicSupplies(book, quote)?.let { required -> quote(required)?.let { required to it } }
        }.minByOrNull { it.second }?.let { return it.first }
        val owned = books.mapNotNull { spellbook ->
            selection.select(BotGearPurpose.MAGIC, desired = true, spellbook = spellbook, preferOwned = true)
                ?.takeIf { outfit -> outfit.gear[Equipment.WEAPON]?.let { selection.owned(it) > 0 } == true }
                ?.runes?.mapValues { (_, amount) -> amount * PkLoadoutSelection.MIN_CASTS }
        }
        val candidates = owned.ifEmpty {
            books.mapNotNull { spellbook ->
                selection.select(BotGearPurpose.MAGIC, desired = true, spellbook = spellbook,
                    preferCheapest = true, purchasePlanning = true)?.let { outfit ->
                    linkedMapOf((outfit.gear[Equipment.WEAPON] ?: return@let null) to 1).also { required ->
                        outfit.runes.forEach { (id, amount) -> required[id] = amount * PkLoadoutSelection.MIN_CASTS }
                    }
                }
            }
        }
        return candidates.minByOrNull { required -> required.entries.sumOf { (id, amount) ->
            bot.world.economy.getPrice(id).coerceAtLeast(0).toDouble() *
                (amount - selection.owned(id)).coerceAtLeast(0)
        } }
    }

    /**
     * Only Magic-exclusive profiles need rune restocking before another attempt. Stock in any eligible
     * spellbook satisfies their fifty-cast departure requirement.
     */
    fun magicRestockRequired(): Boolean = purposes().singleOrNull() == BotGearPurpose.MAGIC &&
        magicSpellbooks().none { selection.select(BotGearPurpose.MAGIC, spellbook = it) != null }

    /**
     * Keeps a supplied owned discipline before considering purchases for one essential weapon and its supplies.
     * Meals are sourced separately so the exact weapon and rune basket can be chosen from executable quotes.
     * Supplies covered by the selected staff are omitted.
     */
    internal fun supplyRequirements(spellbook: Spellbook = bot.spellbook): Map<Int, Int>? {
        val purposes = departurePurposes(spellbook).takeIf { it.isNotEmpty() } ?: return null
        fun requirements(outfit: CombatLoadout): LinkedHashMap<Int, Int>? {
            val required = linkedMapOf<Int, Int>()
            required[outfit.gear[Equipment.WEAPON] ?: return null] = 1
            outfit.gear[Equipment.AMMUNITION]?.let { required[it] = PkLoadoutSelection.MIN_AMMUNITION }
            for ((id, amount) in outfit.runes) {
                required[id] = maxOf(required[id] ?: 0, amount * PkLoadoutSelection.MIN_CASTS)
            }
            return required
        }
        purposes.firstNotNullOfOrNull { selection.select(it, spellbook = spellbook) }?.let { return requirements(it) }
        if (purposes.singleOrNull() == BotGearPurpose.MAGIC) {
            quotedMagicSupplies(spellbook, PkSupplyAcquisition(bot, handler).purchaseQuotes())?.let { return it }
        }
        return purposes.mapNotNull {
            selection.select(it, desired = true, spellbook = spellbook, preferOwned = true,
                preferCheapest = it == BotGearPurpose.MAGIC)?.let(::requirements)
        }.minByOrNull { candidate -> candidate.entries.sumOf { (id, amount) ->
            bot.world.economy.getPrice(id).coerceAtLeast(0).toDouble() *
                (amount - selection.owned(id)).coerceAtLeast(0)
        } }
    }

    private fun quotedMagicSupplies(spellbook: Spellbook, quote: (Map<Int, Int>) -> Long?): Map<Int, Int>? {
        val outfit = selection.select(BotGearPurpose.MAGIC, desired = true, spellbook = spellbook,
            preferOwned = true, preferCheapest = true, purchasePlanning = true, purchaseCost = quote) ?: return null
        return linkedMapOf((outfit.gear[Equipment.WEAPON] ?: return null) to 1).apply {
            outfit.runes.forEach { (id, amount) -> put(id, amount * PkLoadoutSelection.MIN_CASTS) }
        }
    }

    /**
     * Separates absent physical supplies from a rejected risk budget or another departure restriction.
     * Legal unarmed training does not satisfy the PK session's owned-weapon requirement.
     * Scavenging detours may address stock shortages, but must not bypass unrelated PK policies.
     */
    fun missingSupplies(): Boolean {
        if (purposes().isEmpty()) return false
        val noFood = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
            .none { Food.ID_TO_FOOD[it.unnotedItemDef.id()]?.heal?.let { it > 0 } == true }
        val suppliedBook = magicSpellbooks().any { spellbook ->
            departurePurposes(spellbook).any {
                selection.select(it, spellbook = spellbook)?.gear?.get(Equipment.WEAPON) != null
            }
        }
        return noFood || !suppliedBook
    }

    /**
     * Plans synchronously from owned stock. A coordinator may project the one authorized missing clan cape;
     * the actual cape is only replenished when the script runs. Other supplies always require physical stock.
     * A supplied weapon and one food suffice, with fifty casts before a Magic departure. Optional disciplines cannot
     * block a usable outing, and all selected switches must fit both space and the account's risk policy.
     * Initiating PKers budget for losing every carried item.
     */
    fun plan(allowMissingClanCape: Boolean = false, spellbook: Spellbook = bot.spellbook,
             escapeDepth: Int? = null): Plan? {
        val purposes = departurePurposes(spellbook).takeIf { it.isNotEmpty() } ?: return null
        val stock = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
            .groupBy { it.unnotedItemDef.id() }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            .toMutableMap()
        if (allowMissingClanCape) clan.cape?.let { stock.putIfAbsent(it, 1) }
        val foods = stock.keys.filter { Food.ID_TO_FOOD[it]?.heal?.let { heal -> heal > 0 } == true }
            .sortedByDescending { Food.ID_TO_FOOD.getValue(it).heal }
        if (foods.isEmpty()) return null
        val losesItems = PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())
        val assess = PkRiskBudget.assessor(bot, losesItems, keepCountOverride = 0)
        val escapeItems = if (escapeDepth == null) emptyList() else TeleportJewellery.entries
            .filter { escapeDepth in 1..it.maxWildernessLevel }
            .flatMap { it.items }
            .filter { it in stock }
            .sortedBy { bot.world.economy.getPrice(it) }
        val available = purposes.mapNotNull { purpose ->
            selection.select(purpose, spellbook = spellbook)?.let { purpose to it }
        }.toMap()
        fun build(selected: Map<BotGearPurpose, CombatLoadout>): Plan? {
            val purposes = selected.keys.toList()
            val quantities = linkedMapOf<Int, Int>()
            clan.cape?.let { quantities[it] = 1 }
            selected.values.forEach { quantities[it.gear[Equipment.WEAPON] ?: return null] = 1 }
            val ranged = selected[BotGearPurpose.RANGED]
            val ammunition = ranged?.gear?.get(Equipment.AMMUNITION) ?: ranged?.gear?.get(Equipment.WEAPON)
                ?.takeIf { WeaponDefinition.ALL.retrieve(it).type in CombatLoadout.thrown }
            ammunition?.let { quantities[it] = PkLoadoutSelection.MIN_AMMUNITION }
            val magic = selected[BotGearPurpose.MAGIC]
            magic?.runes?.forEach { (id, amount) ->
                quantities[id] = maxOf(quantities[id] ?: 0, amount * PkLoadoutSelection.MIN_CASTS)
            }
            if (quantities.any { (id, amount) -> (stock[id] ?: 0) < amount }) return null
            val starting = arrayOfNulls<Int>(14)
            starting[Equipment.CAPE] = clan.cape
            starting[Equipment.WEAPON] = selected.values.first().gear[Equipment.WEAPON]
            starting[Equipment.AMMUNITION] = ranged?.gear?.get(Equipment.AMMUNITION)

            fun layout(purpose: BotGearPurpose): Array<Int?> = starting.copyOf().also { layout ->
                val outfit = selected.getValue(purpose)
                layout[Equipment.WEAPON] = outfit.gear[Equipment.WEAPON]
                for (slot in listOf(Equipment.CHEST, Equipment.LEGS)) {
                    outfit.gear[slot]?.takeIf { it in quantities }?.let { layout[slot] = it }
                }
                if (purpose == BotGearPurpose.MAGIC) {
                    for (slot in layout.indices.filter { it != Equipment.WEAPON && it != Equipment.AMMUNITION }) {
                        val id = layout[slot] ?: continue
                        if (EquipmentDefinition.ALL.retrieve(id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) < 0) layout[slot] = null
                    }
                }
                if (EquipmentDefinition.ALL.retrieve(layout[Equipment.WEAPON]!!).isTwoHanded) layout[Equipment.SHIELD] = null
            }
            fun snapshot(layout: Array<Int?>): Plan? {
                if (quantities.any { (id, amount) -> amount < 0 || amount > (stock[id] ?: 0) }) return null
                val remaining = quantities.toMutableMap()
                val worn = layout.mapIndexedNotNull { slot, id -> id?.let {
                    val amount = if (slot == Equipment.AMMUNITION || it == ammunition) quantities.getValue(it) else 1
                    remaining[it] = remaining.getValue(it) - amount
                    Item(it, amount)
                } }
                val inventory = ItemContainer(28, StackPolicy.STANDARD, -1)
                for ((id, amount) in remaining) if (amount > 0) {
                    val item = Item(id, amount)
                    if (!inventory.hasSpaceFor(item) || !inventory.add(item)) return null
                }
                val spare = inventory.computeRemainingSize() - 2
                if (spare < 0) return null
                val carried = inventory.filterNotNull()
                if (!assess(carried, worn, true).withinBudget) return null
                return Plan(worn, carried, spare)
            }
            fun fits(): Boolean = quantities.all { (id, amount) -> amount <= (stock[id] ?: 0) } &&
                purposes.all { snapshot(layout(it)) != null }
            fun add(id: Int, amount: Int = 1): Boolean {
                val old = quantities[id] ?: 0
                quantities[id] = old + amount
                if (fits()) return true
                if (old == 0) quantities.remove(id) else quantities[id] = old
                return false
            }

            val firstFood = foods.firstOrNull { add(it) }
            if (firstFood == null) return null
            var foodCount = 1
            for (id in foods) {
                while (foodCount < FOOD_TARGET && (quantities[id] ?: 0) < stock.getValue(id) && add(id)) foodCount++
            }

            escapeItems.firstOrNull { it in quantities || add(it) }

            // Shared slots use the primary outfit, with neutral magic-compatible alternatives where possible.
            for (slot in starting.indices.filter { it !in setOf(Equipment.WEAPON, Equipment.AMMUNITION) &&
                    !(it == Equipment.CAPE && clan.enabled) }) {
                val id = selected.values.mapNotNull { it.gear[slot] }.firstOrNull { id ->
                    BotGearPurpose.MAGIC !in purposes || slot in setOf(Equipment.CHEST, Equipment.LEGS) ||
                        EquipmentDefinition.ALL.retrieve(id).getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index) >= 0
                } ?: continue
                starting[slot] = id
                if (id !in quantities && !add(id)) starting[slot] = null
                else if (!fits()) starting[slot] = null
            }
            for (outfit in selected.values.drop(1)) for (slot in listOf(Equipment.CHEST, Equipment.LEGS)) {
                outfit.gear[slot]?.takeIf { it !in quantities }?.let { add(it) }
            }
            if (BotGearPurpose.MELEE in purposes) {
                for (weapon in listOf(PkFinisher.WHIP, PkFinisher.DRAGON_DAGGER_S)) {
                    if (weapon !in quantities && (stock[weapon] ?: 0) > 0 &&
                        EquipmentDefinition.ALL.retrieve(weapon).meetsAllRequirements(bot) &&
                        BotCombatActionHandler.allowedStyles(bot, WeaponDefinition.ALL.retrieve(weapon).typeDef.styles)
                            .any { style -> style.exp.none { it == io.luna.game.model.mob.Skill.MAGIC ||
                                it == io.luna.game.model.mob.Skill.RANGED } }) {
                        add(weapon)
                    }
                }
            }
            fun extend(costs: Map<Int, Int>, limit: Int) {
                if (costs.isEmpty()) return
                val baseline = quantities.toMap()
                var low = costs.minOf { (id, cost) -> (baseline[id] ?: 0) / cost }
                var high = minOf(limit, costs.minOf { (id, cost) -> stock.getValue(id) / cost })
                while (low < high) {
                    val middle = low + (high - low + 1) / 2
                    val before = quantities.toMap()
                    costs.forEach { (id, cost) -> quantities[id] = maxOf(baseline[id] ?: 0, cost * middle) }
                    val accepted = fits()
                    quantities.clear()
                    quantities.putAll(before)
                    if (accepted) low = middle else high = middle - 1
                }
                costs.forEach { (id, cost) -> quantities[id] = maxOf(baseline[id] ?: 0, cost * low) }
            }
            ammunition?.let { extend(mapOf(it to 1), 500) }
            magic?.let { outfit ->
                for ((id, amount) in BotCombatMagic.optionalRuneStock(bot, outfit.gear[Equipment.WEAPON], outfit.gear)) {
                    val missing = amount - (quantities[id] ?: 0)
                    if (missing > 0) add(id, missing)
                }
            }
            magic?.let { extend(it.runes, 500) }
            val layouts = purposes.map { snapshot(layout(it)) ?: return null }
            val prayerBonus = layouts.minOf { outfit -> outfit.equipment.sumOf {
                EquipmentDefinition.ALL.retrieve(it.id).getBonus(Equipment.EquipmentBonus.PRAYER.index)
            } }
            return layouts.first().copy(potionSlots = layouts.minOf { it.potionSlots },
                melee = BotGearPurpose.MELEE in purposes, prayerBonus = prayerBonus)
        }
        // There are at most three assigned disciplines. Try supplied switches together, then smaller usable kits.
        val choices = available.entries.toList()
        val combinations = (1 until (1 shl choices.size)).sortedByDescending { Integer.bitCount(it) }
        for (mask in combinations) {
            val selected = choices.filterIndexed { index, _ -> mask and (1 shl index) != 0 }
                .associate { it.key to it.value }
            build(selected)?.let { return it }
        }
        // A strongest weapon can consume the remaining allowance before food or runes. One cheapest essential kit
        // per assigned style bounds fallback work while preserving an affordable owned outing.
        for (purpose in available.keys) {
            val fallback = selection.select(purpose, spellbook = spellbook, preferCheapest = true) ?: continue
            build(mapOf(purpose to fallback))?.let { return it }
        }
        return null
    }

    /**
     * Tries a supplied Ancient staff kit first, then other supplied kits before any procurement.
     * Projects each kit before visiting the altar or banking.
     */
    private fun preparationSpellbooks(): List<Spellbook> {
        val books = listOf(bot.spellbook, Spellbook.REGULAR, Spellbook.ANCIENT).distinct().filter {
            BotCombatActionHandler.canUseSpellbook(bot, it)
        }
        return books.sortedBy { book ->
            val kit = plan(allowMissingClanCape = true, spellbook = book)
            when {
                kit == null -> 2
                book == Spellbook.ANCIENT && (kit.equipment + kit.inventory).any {
                    it.id == EquipmentDropSources.ANCIENT_STAFF
                } -> 0
                else -> 1
            }
        }
    }

    /**
     * Keeps future food gathering enabled when preparation cannot reach the later owned-kit planning step.
     */
    private fun requestMissingFood() {
        if ((bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull()).none {
                Food.ID_TO_FOOD[it.unnotedItemDef.id()]?.heal?.let { it > 0 } == true
            }) BotTradeIntentions.requestFood(bot, FOOD_TARGET, minimumHeal = 1)
    }

    /**
     * Deposits unrelated belongings, builds a fresh plan and applies it with native banking/equipment helpers.
     * Every suspension is followed by safety checks. Partial withdrawals and changed prices cannot authorize
     * Wilderness entry; a failed preparation finishes at the bank rather than retrying an impossible kit forever.
     */
    suspend fun prepare(optionalPotions: Boolean = false, escapeDepth: Int? = null,
                        potionShopUntil: Long = Long.MAX_VALUE, allowed: () -> Boolean = { true }): Boolean {
        fun safe() = allowed() && bot.state == EntityState.ACTIVE && !bot.client.isPendingLogout &&
            ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness()
        val acquisition = PkSupplyAcquisition(bot, handler)
        if (!safe() || !acquisition.obtainFood(::safe)) return false
        for (spellbook in preparationSpellbooks()) {
            if (!safe()) return false
            if (!handler.combat.switchSpellbook(spellbook)) continue
            if (prepareCurrentSpellbook(optionalPotions, escapeDepth, potionShopUntil, ::safe)) return true
        }
        return false
    }

    /**
     * Rechecks stock and risk after selecting the spellbook, including after an unsuccessful preferred-book attempt.
     */
    private suspend fun prepareCurrentSpellbook(optionalPotions: Boolean, escapeDepth: Int?,
                                                potionShopUntil: Long, safe: () -> Boolean): Boolean {
        if (!safe()) return false
        try {
            if (!handler.banking.travelToBankOpen() || !safe()) return false
            if (!handler.banking.depositInventory() || !safe() || !handler.equipment.unequipAll() || !safe() ||
                !handler.banking.depositInventory() || !safe()) return false
            if (!clan.ensureCape()) return false
            if (plan() == null) {
                val required = supplyRequirements() ?: run {
                    requestMissingFood()
                    return false
                }
                val minimum = required.map { (id, amount) -> Item(id, amount) }
                val losesItems = PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())
                if (!PkRiskBudget.assessor(bot, losesItems, keepCountOverride = 0)(minimum, emptyList(), true).withinBudget) return false
                PkSupplyAcquisition(bot, handler).obtain(required, safe)
                if (!safe() ||
                    !handler.banking.travelToBankOpen() || !safe() || !handler.banking.depositInventory() || !safe()) return false
            }
            val plan = plan(escapeDepth = escapeDepth) ?: run {
                requestMissingFood()
                return false
            }
            if (optionalPotions) {
                // Reserve the planned essentials, then shop before withdrawing anything travel could consume.
                if (!preparePotions(plan, potionShopUntil, safe) || !safe() ||
                    !handler.banking.travelToBankOpen() || !safe() ||
                    !handler.banking.depositInventory(optionalPotionIds) || !safe()) return false
            }
            for ((id, items) in (plan.inventory + plan.equipment).groupBy { it.id }) {
                val amount = items.sumOf { it.amount }
                when {
                    id in Rune.ID_TO_RUNE || EquipmentDefinition.ALL.get(id).map { it.index == Equipment.AMMUNITION }.orElse(false) ||
                        WeaponDefinition.ALL.get(id).map { it.type in CombatLoadout.thrown }.orElse(false) ->
                        bot.preferences.raiseWantedItemTarget(id, maxOf(1000, amount * 2))
                }
            }
            val foodCount = (plan.inventory + plan.equipment).filter { it.id in Food.ID_TO_FOOD }.sumOf { it.amount }
            if (foodCount > 0) BotTradeIntentions.requestFood(bot, foodCount, minimumHeal = 1)
            handler.banking.clickBankingMode(noted = false)
            for (item in plan.equipment) {
                if (!safe() || !bot.bank.isOpen || !handler.banking.withdraw(item) || !safe() ||
                    bot.inventory.computeAmountForId(item.id) < item.amount) {
                    bot.log("PK preparation failed while withdrawing equipment ${item.id}.")
                    return false
                }
            }
            // Native equip clicks close the bank. Withdraw the whole outfit before equipping any of it.
            for (item in plan.equipment) {
                if (!safe() || !handler.equipment.equip(item.id) || !safe() ||
                    bot.equipment.computeAmountForId(item.id) != item.amount) {
                    bot.log("PK preparation failed while equipping ${item.id}.")
                    return false
                }
            }
            if (!safe() || !handler.banking.travelToBankOpen() || !safe()) {
                bot.log("PK preparation failed while reopening the bank for supplies.")
                return false
            }
            handler.banking.clickBankingMode(noted = false)
            val withdraw = plan.inventory.groupBy { it.id }.map { (id, items) -> Item(id, items.sumOf { it.amount }) }
            for (item in withdraw) {
                if (!safe() || !bot.bank.isOpen || !handler.banking.withdraw(item) || !safe() ||
                    bot.inventory.computeAmountForId(item.id) < item.amount) {
                    bot.log("PK preparation failed while withdrawing supplies ${item.id}.")
                    return false
                }
            }
            if (optionalPotions && !fitPotionRisk(plan, safe)) return false
            fun counts(items: List<Item>) = items.groupBy { it.id }.mapValues { (_, stacks) -> stacks.sumOf { it.amount.toLong() } }
            val carried = bot.inventory.filterNotNull()
            val supplies = if (optionalPotions) carried.filterNot { it.id in optionalPotionIds } else carried
            return safe() && ready() && bot.equipment.filterNotNull() == plan.equipment &&
                counts(supplies) == counts(plan.inventory) && handler.combat.pkRiskBudget().withinBudget
        } finally {
            bot.overlays.closeWindows()
        }
    }

    /**
     * Fills spare slots after the essential loadout has been planned. Purchases spend only a small share of cash and
     * must leave the whole pack within the same conservative risk allowance used for planning equipment switches.
     */
    private suspend fun preparePotions(plan: Plan, shopUntil: Long, safe: () -> Boolean): Boolean {
        val before = potionRisk(plan, emptyList())
        val coins = bot.bank.computeAmountForId(995).toLong() + bot.inventory.computeAmountForId(995)
        val allowance = (before.maximumLoss - before.deathLoss).min(BigInteger.valueOf(coins / 10))
            .coerceAtLeast(BigInteger.ZERO).toLong()
        val needs = buildList {
            if (plan.potionSlots > 0) {
                addAll(PkPotionUse.needs(bot, plan.prayerBonus, allowance))
                if (plan.melee) boosts.forEach {
                    add(BotPotionTrip.Need(it, doses = 4, maxCoinsPerDose = (allowance + 3) / 4))
                }
            }
        }
        return BotPotionTrip.prepare(bot, needs, allowance, minOf(plan.potionSlots, needs.size), safe, shopUntil) {
            potionRisk(plan, it).withinBudget
        } && fitPotionRisk(plan, safe)
    }

    private fun potionRisk(plan: Plan, potions: List<Item>): PkRiskBudget {
        val losesItems = PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())
        return PkRiskBudget.assessor(bot, losesItems, keepCountOverride = 0)(
            plan.inventory + potions, plan.equipment, true)
    }

    private suspend fun fitPotionRisk(plan: Plan, safe: () -> Boolean): Boolean {
        val potions = bot.inventory.filterNotNull().filter { it.id in optionalPotionIds }
        if (potions.sumOf { it.amount } <= plan.potionSlots && potionRisk(plan, potions).withinBudget) return safe()

        if (!safe() || !handler.banking.travelToBankOpen() || !safe()) return false
        for ((id, items) in potions.groupBy { it.id }) {
            if (!handler.banking.deposit(Item(id, items.sumOf { it.amount })) || !safe()) return false
        }
        return potionRisk(plan, emptyList()).withinBudget
    }
}
