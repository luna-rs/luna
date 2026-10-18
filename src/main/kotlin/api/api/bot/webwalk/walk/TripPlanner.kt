package api.bot.webwalk.walk

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.TripNeeds
import api.bot.webwalk.plan.WebWalkPlanner
import api.predef.*
import io.luna.game.model.Position
import java.util.Random

/**
 * Chooses between a trip straight to a destination with what a bot carries, and a trip by its nearest bank that withdraws
 * what the rest of the trip spends. It only reads what it is given, so it runs on any thread, and can be tested without a
 * bot.
 *
 * - The trip is first planned with the highest tier of the bank that the bot may use (see [BankAccess]). If that spends
 *   nothing that the bot doesn't carry, it is taken, and nothing more is planned.
 * - Otherwise the trip with what the bot carries, the trip to the nearest bank, and the trip from the stand of that bank are
 *   planned. The trip from the bank is planned with each tier in turn, until one needs nothing from the bank or its
 *   withdrawal fits (see [BankWithdrawal.choose]). A lower tier is only tried when a higher one doesn't fit, never when it
 *   costs more, because a lower tier counts less of the bank and can't make a cheaper trip.
 * - The trip by the bank is taken if there is no trip without it, or if it costs less: both trips and
 *   [api.bot.webwalk.plan.PlannerCosts.bankTicks]. The free home teleport goes to Varrock west bank, so for most bots that is
 *   the nearest bank and the walk there is short, and the question is whether the trip from Varrock is cheaper.
 *
 * The costs that are compared vary a little with the intelligence of the bot, which may be given a trip that is not the best
 * (see [WebWalkPlanner]), so a less intelligent bot's choice is a little noisy.
 *
 * @param planner The planner.
 * @param banks The banks of the web.
 *
 * @author Hydrozoa
 */
class TripPlanner(private val planner: WebWalkPlanner, private val banks: List<BankDefinition>) {

    /**
     * Chooses a trip.
     *
     * @param start Where the trip starts.
     * @param destination Where the trip ends.
     * @param carried What the bot can do with what it carries, a snapshot of [BankAccess.NONE].
     * @param tiers The snapshots to try with the bank, from the highest tier that the trip may use down. Empty plans with
     * [carried] only. Tiers that couldn't fit in the inventory at all are left out (see [BankWithdrawal.highestFitting]).
     * @param view What the bot has in its inventory and bank.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`.
     * @param random Where the variation of the route comes from.
     * @return The trip.
     */
    fun plan(start: Position,
             destination: Position,
             carried: CapabilitySnapshot,
             tiers: Map<BankAccess, CapabilitySnapshot>,
             view: BankView,
             intelligence: Double = 1.0,
             random: Random = Random()): TripPlan {
        val route = "No trip from $start to $destination"
        val fitting = BankWithdrawal.highestFitting(view, tiers.keys.firstOrNull() ?: BankAccess.NONE)
        val usable = tiers.filterKeys { it <= fitting }
        val top = usable.values.firstOrNull()
        if (top == null) {
            planner.plan(start, destination, carried, intelligence, random)?.let { return TripPlan.Direct(it) }
            // Say what the bank would have had to give, if a trip with it exists.
            val wanted = tiers.values.firstOrNull()?.let { snapshot ->
                planner.plan(start, destination, snapshot, intelligence, random)?.let { TripNeeds.of(it, snapshot).withdrawal(carried) }
            }
            return TripPlan.NoTrip(wanted?.let {
                "$route with what the bot carries, and ${describe(it.required)} from the bank doesn't fit in the inventory."
            } ?: "$route.")
        }
        val first = planner.plan(start, destination, top, intelligence, random) ?: return TripPlan.NoTrip("$route.")
        if (TripNeeds.of(first, top).withdrawal(carried) == null) {
            return TripPlan.Direct(first)
        }

        val direct = planner.plan(start, destination, carried, intelligence, random)
        fun direct(why: String): TripPlan = direct?.let { TripPlan.Direct(it) } ?: TripPlan.NoTrip("$route $why.")

        val toBank = planner.planToNearestBank(start, carried, intelligence, random)
            ?: return direct("with what the bot carries, and no bank to reach")
        val stand = toBank.destination ?: start
        val bank = banks.firstOrNull { it.stand == stand } ?: return direct("with what the bot carries")

        var candidate: TripPlan.ViaBank? = null
        var tooBig: Spend? = null
        for ((access, snapshot) in usable) {
            val fromBank = fromBank(stand, destination, snapshot, carried, view, intelligence, random) ?: continue
            val items = if (fromBank.withdrawal == null) emptyList() else fromBank.items
            if (items == null) {
                if (tooBig == null) {
                    tooBig = fromBank.withdrawal?.required
                }
                continue
            }
            if (items.isEmpty() && direct != null) {
                // From the bank the bot needs nothing from it, so going there is no help.
                break
            }
            val cost = toBank.cost + planner.costs.bankTicks + fromBank.plan.cost
            candidate = TripPlan.ViaBank(access, bank, toBank, fromBank.plan, items, cost, direct)
            break
        }
        if (candidate == null) {
            return direct(tooBig?.let { "with what the bot carries, and ${describe(it)} from the bank doesn't fit in the inventory" }
                              ?: "with what the bot carries")
        }
        if (direct != null && candidate.cost >= direct.cost) {
            return TripPlan.Direct(direct, candidate)
        }
        return candidate
    }

    /**
     * Plans a trip from the stand of a bank, and works out what it would withdraw.
     *
     * @param stand The tile in front of the booth.
     * @param destination Where the trip ends.
     * @param snapshot What the bot can do, with as much of the bank as it may use.
     * @param carried What the bot can do with what it carries.
     * @param view What the bot has in its inventory and bank.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`.
     * @param random Where the variation of the route comes from.
     * @return The trip and what it would withdraw, or `null` if there is no trip from the bank.
     */
    fun fromBank(stand: Position,
                 destination: Position,
                 snapshot: CapabilitySnapshot,
                 carried: CapabilitySnapshot,
                 view: BankView,
                 intelligence: Double = 1.0,
                 random: Random = Random()): BankStandPlan? {
        val plan = planner.plan(stand, destination, snapshot, intelligence, random) ?: return null
        val needs = TripNeeds.of(plan, snapshot)
        val withdrawal = needs.withdrawal(carried) ?: return BankStandPlan(plan, needs, null, null)
        // Worn jewellery is taken off into the inventory to be rubbed, so a slot is kept for it.
        val keepFree = if (needs.spend.charges.keys.any { it in view.wornJewellery }) 1 else 0
        return BankStandPlan(plan, needs, withdrawal, BankWithdrawal.choose(view, withdrawal, keepFree))
    }

    companion object {

        /**
         * Describes what a trip needs, such as `30 coins` or `1 x Law rune, 3 x Air rune`.
         *
         * @param spend What it needs.
         * @return The description.
         */
        fun describe(spend: Spend): String {
            val items = spend.items.map { (id, amount) ->
                if (id == CapabilitySnapshot.COINS) "$amount coins" else "$amount x ${nameOf(id)}"
            }
            val charges = spend.charges.map { (name, amount) -> "$amount ${name.lowercase().replace('_', ' ')} charges" }
            return (items + charges).joinToString()
        }

        /**
         * The name of an item, or its id if the item definitions aren't loaded, as in a test.
         */
        private fun nameOf(id: Int): String = runCatching { itemName(id) }.getOrNull() ?: "item $id"
    }
}
