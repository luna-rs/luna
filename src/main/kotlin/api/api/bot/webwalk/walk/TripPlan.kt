package api.bot.webwalk.walk

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.TripNeeds
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.Withdrawal
import io.luna.game.model.item.Item

/**
 * The trip that [TripPlanner] chose for a bot: straight to the destination, by its bank, or none.
 *
 * @author Hydrozoa
 */
sealed interface TripPlan {

    /**
     * A trip straight to the destination, with what the bot carries.
     *
     * @property plan The trip.
     * @property turnedDown The trip by the bank that was looked at and cost more, or `null` if there was none.
     */
    data class Direct(val plan: WebWalkPlan, val turnedDown: ViaBank? = null) : TripPlan

    /**
     * A trip by the nearest bank, which withdraws what the rest of the trip spends.
     *
     * @property access How much of the bank the trip from it counts.
     * @property bank The bank.
     * @property toBank The trip to the bank, with what the bot carries, refined to be walked.
     * @property fromBank The trip from the stand of the bank to the destination, as planned before the bot set off and not
     * refined. It is planned again at the bank.
     * @property withdrawal What would be withdrawn for [fromBank], which is nothing if it needs nothing from the bank.
     * @property cost What the whole trip costs: both trips as the search found them, and
     * [api.bot.webwalk.plan.PlannerCosts.bankTicks]. This is what was compared with [comparedWith].
     * @property comparedWith The trip with what the bot carries that this was compared with, as the search found it, or `null`
     * if there is none.
     */
    data class ViaBank(val access: BankAccess,
                       val bank: BankDefinition,
                       val toBank: WebWalkPlan,
                       val fromBank: WebWalkPlan,
                       val withdrawal: List<Item>,
                       val cost: Double,
                       val comparedWith: WebWalkPlan?) : TripPlan

    /**
     * No trip.
     *
     * @property reason Why, in a sentence.
     */
    data class NoTrip(val reason: String) : TripPlan
}

/**
 * A trip from the stand of a bank, and what it would take from the bank.
 *
 * @property plan The trip.
 * @property needs What it spends.
 * @property withdrawal What it would have to withdraw, or `null` if the bot carries all that it spends.
 * @property items The items that would be withdrawn, or `null` if there is no [withdrawal] or it doesn't fit.
 *
 * @author Hydrozoa
 */
data class BankStandPlan(val plan: WebWalkPlan,
                         val needs: TripNeeds,
                         val withdrawal: Withdrawal?,
                         val items: List<Item>?)
