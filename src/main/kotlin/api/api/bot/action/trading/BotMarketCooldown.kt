package api.bot.action.trading

import api.attr.Attr
import api.predef.rand
import io.luna.game.model.mob.bot.Bot

/**
 * Defers elective marketplace buying and selling independently while ordinary activities remain available.
 *
 * Each bot owns at most two transient deadlines. Ordinary and merchant purposes share their direction, so changing
 * the session purpose cannot bypass a saturated or unproductive visit. Targeted supplies and reactive human trades
 * decide their own admission and keep their existing absolute deadlines.
 *
 * @author lare96
 */
internal object BotMarketCooldown {

    private val buying = Attr.long()
    private val selling = Attr.long()

    /**
     * Checks admission without allocating a stored attribute for bots that have never deferred this direction.
     */
    fun ready(bot: Bot, purpose: BotTradePurpose, now: Long = System.currentTimeMillis()): Boolean {
        val attributes = bot.attributes()
        val attribute = deadline(purpose)
        return !attributes.has(attribute) || now >= attributes[attribute]
    }

    /**
     * Starts one jittered two-to-five-minute retry delay. Repeated checks preserve an already active deadline.
     */
    fun defer(bot: Bot, purpose: BotTradePurpose, now: Long = System.currentTimeMillis()): Long {
        val attributes = bot.attributes()
        val attribute = deadline(purpose)
        if (attributes.has(attribute) && attributes[attribute] > now) return attributes[attribute]
        val retryAt = now + rand(2, 5) * 60_000L
        attributes[attribute] = retryAt
        return retryAt
    }

    private fun deadline(purpose: BotTradePurpose) = when (purpose) {
        BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY -> buying.attr
        BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> selling.attr
    }
}
