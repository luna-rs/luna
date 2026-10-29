package api.bot.webwalk.plan

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportKind
import game.obj.webs.Web

/**
 * What a trip spends, as the planner counted it, and the buffer that a bot withdraws on top of it when it goes to its bank
 * for the trip, so that it has a little to spare.
 *
 * It is worked out from the legs of a plan, so it doesn't matter whether the planner counted what it spent along the way
 * (see [PlannerCosts.abundantUses]).
 *
 * @property spend What the trip spends: the coins of its fares and tolls, the runes of its teleport spells, and the charges
 * of its teleport jewellery.
 * @property buffer What is withdrawn on top of [spend]: the coins of the largest fare or toll, what each teleport spell
 * takes once, and a charge of each jewellery.
 * @property blade Whether the trip crosses a web, or ends where one is expected, so that a bot that carries nothing
 * that cuts webs withdraws a knife (see [BotConditions.SLASH_WEB]). A knife isn't spent, so it's not in [spend].
 *
 * @author Hydrozoa
 */
data class TripNeeds(val spend: Spend, val buffer: Spend, val blade: Boolean = false) {

    companion object {

        /**
         * Works out what a plan spends.
         *
         * The items that a leg asks the bot to carry, such as a key, add nothing: they must be carried, and are never
         * withdrawn (see [BotCapabilities.snapshot]).
         *
         * @param plan The plan.
         * @param snapshot The snapshot that the plan was made with, which says what each of its teleports spends.
         * @param expected The conditions that the bot should meet when it arrives (see
         * [api.bot.webwalk.walk.crossing.WebCrossing.expected]).
         * @return What the plan spends.
         */
        fun of(plan: WebWalkPlan, snapshot: CapabilitySnapshot, expected: Set<String> = emptySet()): TripNeeds {
            val items = HashMap<Int, Int>()
            val charges = HashMap<String, Int>()
            val bufferItems = HashMap<Int, Int>()
            val bufferCharges = HashMap<String, Int>()
            val spells = HashSet<String>()
            for (leg in plan.legs) {
                val coins = leg.requirements.coins
                if (coins > 0) {
                    add(items, CapabilitySnapshot.COINS, coins)
                    bufferItems.merge(CapabilitySnapshot.COINS, coins, ::maxOf)
                }
                val teleport = leg.teleport
                if (leg.type != EdgeType.TELEPORT || teleport == null) {
                    continue
                }
                val spend = snapshot.teleportSpends[teleport.id] ?: continue
                spend.items.forEach { (id, amount) -> add(items, id, amount) }
                spend.charges.forEach { (name, amount) -> add(charges, name, amount) }
                if (teleport.kind == TeleportKind.SPELL && spells.add(teleport.id)) {
                    spend.items.forEach { (id, amount) -> add(bufferItems, id, amount) }
                }
                spend.charges.keys.forEach { bufferCharges[it] = 1 }
            }
            val blade = BotConditions.SLASH_WEB in expected ||
                    plan.legs.any { BotConditions.SLASH_WEB in it.requirements.flags }
            return TripNeeds(Spend(items, charges), Spend(bufferItems, bufferCharges), blade)
        }

        /**
         * Adds an amount to a total, which stops at [Int.MAX_VALUE] instead of overflowing.
         */
        private fun <K> add(totals: MutableMap<K, Int>, key: K, amount: Int) {
            totals.merge(key, amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }

        /**
         * What is left of [needed] after what is [carried], and nothing if that is less than nothing.
         */
        private fun short(needed: Long, carried: Int?): Int =
            (needed - (carried ?: 0)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Works out what a bot would have to withdraw from its bank for the trip.
     *
     * For each item and jewellery, what is required is what the trip spends less what the bot carries, and what is wanted
     * is that with the [buffer] added. A knife is required too if the trip needs a [blade] and the bot carries none.
     * The buffer never makes a bot go to its bank: there is no withdrawal unless something is required.
     *
     * @param carried What the bot carries, as a snapshot that doesn't count its bank.
     * @return What to withdraw, or `null` if the bot carries everything that the trip spends.
     */
    fun withdrawal(carried: CapabilitySnapshot): Withdrawal? {
        val knife = if (blade && BotConditions.SLASH_WEB !in carried.flags) mapOf(Web.KNIFE to 1) else emptyMap()
        val requiredItems = spend.items.mapValues { (id, amount) -> short(amount.toLong(), carried.items[id]) }
            .filterValues { it > 0 } + knife
        val requiredCharges = spend.charges.mapValues { (name, amount) -> short(amount.toLong(), carried.charges[name]) }
            .filterValues { it > 0 }
        if (requiredItems.isEmpty() && requiredCharges.isEmpty()) {
            return null
        }
        val wantedItems = (spend.items.keys + buffer.items.keys).associateWith { id ->
            short((spend.items[id] ?: 0).toLong() + (buffer.items[id] ?: 0), carried.items[id])
        }.filterValues { it > 0 } + knife
        val wantedCharges = (spend.charges.keys + buffer.charges.keys).associateWith { name ->
            short((spend.charges[name] ?: 0).toLong() + (buffer.charges[name] ?: 0), carried.charges[name])
        }.filterValues { it > 0 }
        return Withdrawal(Spend(requiredItems, requiredCharges), Spend(wantedItems, wantedCharges))
    }
}

/**
 * What a bot would have to withdraw from its bank for a trip, see [TripNeeds.withdrawal].
 *
 * @property required What the trip can't be taken without.
 * @property wanted What is withdrawn if it is there and fits: [required] and the buffer on top of it.
 *
 * @author Hydrozoa
 */
data class Withdrawal(val required: Spend, val wanted: Spend)
