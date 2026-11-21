package game.bot.scripts.skills

import api.bot.action.BotSupplySources
import api.bot.zone.SubZone
import api.bot.zone.Zone
import game.content.ectofuntus.Ectofuntus
import game.content.ectofuntus.EctofuntusAction.Step
import game.content.ectofuntus.EctofuntusBone
import game.item.teleport.EctophialTeleport
import game.skill.prayer.buryBone.BuryBoneAction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil

/**
 * Compares one finishable temple load with the cost of earning the same XP by burial. Quotes never add bones to a
 * temple load. The same calculation lets combat retain only the raw portion that the bank cannot already supply.
 *
 * @author lare96
 */
internal object PrayerOuting {
    const val MAX_WORSHIPS = 8

    // Use walking pace on the surface to allow for run energy and bends. Local crossing estimates include stairs,
    // trapdoor and the full cavern circuit. Each native click also incurs packet/polling time and the script's delay.
    private const val GRINDER_ROUND_TRIP = 22
    private const val SLIME_ROUND_TRIP = 180
    private const val BETWEEN_GRINDER_STEPS = 9
    private const val CLICK_TICKS = 3
    private const val CLAIM_TICKS = 8
    private const val BANK_TICKS = 10
    // Banked burial uses the stationary production startup and bank-work area before the first click.
    private const val BURIAL_BANK_TICKS = 45
    private const val SHOP_TICKS = 65
    private const val COINS_PER_TICK = 25.0
    private const val MAX_BONE_VALUE_TICKS = 60.0
    private val atrium = SubZone.ECTOFUNTUS_ATRIUM.inside
    private val banks = Zone.entries.flatMap { it.bankAnchors }.distinct()
    private val supplies = Ectofuntus.GHOSTSPEAK_IDS +
        setOf(995, Ectofuntus.POT, Ectofuntus.BUCKET, Ectofuntus.SLIME, EctophialTeleport.FULL)

    /**
     * The exact unnoted basket and the time comparison that justified it. A zero-worship basket can recover the
     * grinder or collect credit from an earlier visit.
     *
     * @author lare96
     */
    data class Batch(val recipe: EctofuntusBone, val raw: Int, val meals: Int, val loaded: Int, val slime: Int,
                     val buckets: Int, val supplies: List<Item>, val purchases: Map<Int, Int>,
                     val purchaseCoins: Long, val purchaseCeilings: Map<Int, Long>, val ticks: Int,
                     val burialTicks: Double, val rawValueTicks: Double) {
        val worships get() = slime + buckets
        val experience get() = worships * recipe.baseXp * 4
        val saving get() = burialTicks - ticks - rawValueTicks - purchaseCoins / COINS_PER_TICK
    }

    fun select(bot: Bot, maximumTicks: Long, maxWorships: Int = MAX_WORSHIPS, shop: Boolean = true,
               onlyRecipe: EctofuntusBone? = null, recover: Boolean = true): Batch? {
        val position = bot.position
        val loaded = Ectofuntus.loadedBone(bot)
        val ground = loaded != null && Ectofuntus.isGround(bot)
        val pending = Ectofuntus.pendingTokens(bot)
        val recipes = when {
            loaded != null -> listOf(loaded)
            onlyRecipe != null -> listOf(onlyRecipe)
            else -> EctofuntusBone.entries
        }
        if (onlyRecipe != null && loaded != null && loaded != onlyRecipe) return null
        val carriedStock = stock(bot.inventory)
        val bankedStock = stock(bot.bank)
        fun carried(id: Int) = carriedStock[id] ?: 0
        fun owned(id: Int) = (carried(id).toLong() + (bankedStock[id] ?: 0)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val amulet = if (Ectofuntus.requiresGhostspeak && Ectofuntus.GHOSTSPEAK_IDS.none { bot.equipment.contains(it) })
            Ectofuntus.GHOSTSPEAK_IDS.firstOrNull { owned(it) > 0 } ?: return null else null
        val coins by lazy { owned(995).toLong() }
        val bank by lazy { banks.filter { it.z == position.z }.minByOrNull { it.computeLongestDistance(position) } }
        val pot = owned(Ectofuntus.POT)
        val bucket = owned(Ectofuntus.BUCKET)
        val slime = owned(Ectofuntus.SLIME)
        val quotes = HashMap<Pair<Int, Int>, BotSupplySources.Quote?>()
        val values = HashMap<Int, Double>()
        fun value(recipe: EctofuntusBone) = values.getOrPut(recipe.boneId) {
            // Common drops have a cheap gathering alternative even if a thin market lists them much higher.
            val cap = if (recipe.baseXp <= 5.3) 4.0 else MAX_BONE_VALUE_TICKS
            (bot.world.economy.getPrice(recipe.boneId).coerceAtLeast(0) / COINS_PER_TICK).coerceAtMost(cap)
        }
        val buryTicks = maxOf(BuryBoneAction.DELAY, if (bot.personality.isDextrous) 4 else 6)
        fun quote(id: Int, quantity: Int): BotSupplySources.Quote? {
            val key = id to quantity
            if (key !in quotes) quotes[key] = BotSupplySources.quote(bot, id, quantity)
            return quotes[key]?.takeIf { it.quantity >= quantity }
        }

        fun basket(recipe: EctofuntusBone, worships: Int, recoverLoad: Boolean): Batch? {
            val load = if (recoverLoad) 1 else 0
            val meals = minOf((worships - load).coerceAtLeast(0), owned(recipe.bonemealId))
            val raw = (worships - load - meals).coerceAtLeast(0)
            val filled = minOf(worships, slime)
            val empty = worships - filled
            val supplies = buildList {
                if (raw > 0) add(Item(recipe.boneId, raw))
                if (meals > 0) add(Item(recipe.bonemealId, meals))
                if (raw + load > 0) add(Item(Ectofuntus.POT, raw + load))
                if (filled > 0) add(Item(Ectofuntus.SLIME, filled))
                if (empty > 0) add(Item(Ectofuntus.BUCKET, empty))
                if (amulet != null) add(Item(amulet))
            }
            if (supplies.sumOf { it.amount } + 1 > bot.inventory.capacity()) return null
            val purchases = buildMap {
                if (raw + load > pot) put(Ectofuntus.POT, raw + load - pot)
                if (empty > bucket) put(Ectofuntus.BUCKET, empty - bucket)
            }
            if (purchases.isNotEmpty() && (!shop || coins == 0L)) return null
            var cost = 0L
            val ceilings = mutableMapOf<Int, Long>()
            for ((id, needed) in purchases) {
                val supply = quote(id, needed) ?: return null
                cost += supply.totalCoins
                ceilings[id] = supply.maximumUnitPrice
            }
            if (purchases.isNotEmpty() && cost > coins) return null

            val bankNeeded = needsBank(bot, recipe, supplies, raw, meals, worships > 0)
            val departure = if (bankNeeded) bank ?: position else position
            val toBank = if (bankNeeded) position.computeLongestDistance(departure) + BANK_TICKS else 0
            val travel = if (!bankNeeded && carried(EctophialTeleport.FULL) > 0 && !atTemple(position)) 8
                         else surfaceTicks(departure, recoverLoad || worships == 0) ?: return null
            if (travel > 190 && !recoverLoad && worships > 0) return null
            val atGrinder = !bankNeeded && position.z == 1 && atTemple(position)
            val atPool = !bankNeeded && position.y > 9800 && atTemple(position)
            val grind = raw * (step(Step.LOAD) + step(Step.GRIND) + step(Step.COLLECT) + BETWEEN_GRINDER_STEPS) +
                if (load == 0) 0 else step(Step.COLLECT) + if (ground) 0 else step(Step.GRIND)
            val tokens = pending + worships * Ectofuntus.TOKEN_REWARD
            val claims = if (tokens == 0) 0 else if (tokens > Ectofuntus.TOKEN_CAP) 2 else 1
            val ticks = toBank + travel + (if (purchases.isEmpty()) 0 else SHOP_TICKS) +
                grind + (if (grind == 0 || atGrinder) 0 else GRINDER_ROUND_TRIP) +
                empty * step(Step.FILL) + (if (empty == 0 || atPool && grind == 0) 0 else SLIME_ROUND_TRIP) +
                worships * step(Step.WORSHIP) + claims * CLAIM_TICKS
            if (ticks > maximumTicks) return null

            // Burial can instead spend any one owned recipe that reaches the same target. If it exhausts this
            // recipe first, price the remaining bones and the trip or gathering work to replace them.
            fun burialTime(candidate: EctofuntusBone, quantity: Int, missing: Int): Double {
                val id = candidate.boneId
                val bankTime = if (carried(id) >= quantity) 0 else
                    (bank?.computeLongestDistance(position) ?: 0) + BURIAL_BANK_TICKS +
                        (quantity - 1) / bot.inventory.capacity() * BANK_TICKS
                val available = if (missing == 0 || coins == 0L) null
                                else quote(id, missing)?.takeIf { it.totalCoins <= coins }
                val gathering = missing * ((if (candidate.baseXp <= 5.3) 4 else 12) + value(candidate))
                val shopping = available?.let { SHOP_TICKS + missing + it.totalCoins / COINS_PER_TICK }
                val replacement = if (shopping == null) gathering else minOf(gathering, shopping)
                return bankTime + quantity * buryTicks + (quantity - missing) * value(candidate) + replacement
            }
            val equivalent = worships * 4
            var burial = if (equivalent == 0) 0.0 else
                burialTime(recipe, equivalent, (equivalent - owned(recipe.boneId)).coerceAtLeast(0))
            if (worships > 0 && load == 0) {
                for (other in EctofuntusBone.entries) {
                    if (other == recipe) continue
                    val needed = ceil(worships * recipe.baseXp * 4 / other.baseXp).toInt()
                    if (needed <= owned(other.boneId))
                        burial = minOf(burial, burialTime(other, needed, 0))
                }
            }
            return Batch(recipe, raw, meals, load, filled, empty, supplies, purchases, cost, ceilings, ticks, burial,
                if (raw == 0) 0.0 else raw * value(recipe))
        }

        var best: Batch? = null
        for (recipe in recipes) {
            val limit = (owned(recipe.boneId).toLong() + owned(recipe.bonemealId) + if (loaded != null) 1 else 0)
                .coerceAtMost(maxWorships.toLong()).toInt()
            for (quantity in limit downTo 1) {
                val batch = basket(recipe, quantity, loaded != null) ?: continue
                if (batch.saving > (best?.saving ?: 0.0)) best = batch
            }
        }
        if (best != null || !recover) return best
        if (loaded != null) basket(loaded, 0, true)?.let { return it }
        return if (pending > 0) basket(onlyRecipe ?: loaded ?: EctofuntusBone.REGULAR, 0, false) else null
    }

    fun needsBank(bot: Bot, recipe: EctofuntusBone, supplies: List<Item>, raw: Int, meals: Int,
                  worship: Boolean): Boolean {
        val inventory = bot.inventory
        if (!inventory.containsAll(supplies) || inventory.isFull && !inventory.contains(Ectofuntus.TOKEN)) return true
        return worship && (inventory.computeAmountForId(recipe.boneId) != raw ||
            inventory.computeAmountForId(recipe.bonemealId) != meals ||
            inventory.any { it != null && EctofuntusBone.forMeal(it.id)?.let { meal -> meal != recipe } == true })
    }

    private fun stock(items: Iterable<Item?>): Map<Int, Int> {
        val stock = HashMap<Int, Int>()
        for (item in items) {
            if (item == null || item.isDynamic) continue
            val id = item.id
            if (id in supplies || EctofuntusBone.forBone(id) != null || EctofuntusBone.forMeal(id) != null) {
                stock.merge(id, item.amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            }
        }
        return stock
    }

    private fun step(step: Step) = step.ticks + CLICK_TICKS

    private fun atTemple(position: Position) = when {
        position.y > 9800 -> position.computeLongestDistance(Ectofuntus.POOL) <= 34
        else -> position.computeLongestDistance(atrium) <= 15 && position.z in 0..1
    }

    private fun surfaceTicks(position: Position, recovery: Boolean): Int? = when {
        atTemple(position) -> if (position.y > 9800) SLIME_ROUND_TRIP / 2 else 0
        position.z == 0 && position.y < 6000 -> position.computeLongestDistance(atrium) + 4
        recovery -> 220
        else -> null
    }
}
