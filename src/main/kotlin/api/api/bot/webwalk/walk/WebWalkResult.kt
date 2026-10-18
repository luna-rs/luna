package api.bot.webwalk.walk

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.plan.WebWalkPlan
import io.luna.game.model.item.Item

/**
 * How a web walk ended.
 *
 * @property success `true` if the bot is where it was told to go.
 * @property message What happened, in a sentence, which is why it failed if it did.
 * @property plan The trip that was planned, or `null` if none could be.
 * @property legsDone How many of the legs of the plan were finished.
 * @property bank The bank that the bot was sent to, for a walk to the nearest bank. It is `null` for other walks, and for a
 * walk to the nearest bank that couldn't be planned.
 * @property withdrawn The items that were taken out of the bank for the trip, on a trip that went by the bank.
 *
 * @author Hydrozoa
 */
data class WebWalkResult(val success: Boolean,
                         val message: String,
                         val plan: WebWalkPlan? = null,
                         val legsDone: Int = 0,
                         val bank: BankDefinition? = null,
                         val withdrawn: List<Item> = emptyList())
