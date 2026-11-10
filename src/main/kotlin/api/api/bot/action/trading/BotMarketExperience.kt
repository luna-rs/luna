package api.bot.action.trading

import api.attr.Attr
import io.luna.game.model.mob.bot.Bot

/**
 * Remembers a small amount of personal market experience without changing the world's guide prices.
 *
 * Only completed, sufficiently exposed visits record disappointment. Quotes are captured by their caller for
 * the next prepared basket; declined trades, advertisements and travel failures never update this history.
 *
 * @author lare96
 */
object BotMarketExperience {

    private val experience = Attr.obj { History() }

    /**
     * Applies an unsuccessful buyer's bounded premium within the caller's actual per-unit spending ceiling.
     */
    fun buy(bot: Bot, id: Int, price: Long, maxUnitPrice: Long = Long.MAX_VALUE): Long =
        history(bot)?.quote(id, true, price, System.currentTimeMillis(), maximum = maxUnitPrice)
            ?: price.coerceIn(1L, maxUnitPrice.coerceAtLeast(1L))

    /**
     * Discounts unsold stock while respecting any independently established liquidation floor.
     */
    fun sell(bot: Bot, id: Int, price: Long, minimum: Long = 1L): Long =
        history(bot)?.quote(id, false, price, System.currentTimeMillis(), minimum = minimum)
            ?: maxOf(price, minimum, 1L)

    /**
     * Records one qualified unsuccessful visit, with a cooldown shared by that bot and commodity direction.
     */
    fun unsuccessful(bot: Bot, failure: Failure) {
        val history = bot.attributes()[experience.attr]
        history.unsuccessful(failure.id, failure.buying, System.currentTimeMillis())
    }

    /**
     * Clears only the direction that actually completed through native trade confirmation.
     */
    fun completed(bot: Bot, id: Int, buying: Boolean) {
        history(bot)?.completed(id, buying)
    }

    private fun history(bot: Bot): History? = bot.attributes().let {
        if (it.has(experience.attr)) it[experience.attr] else null
    }

    /**
     * One advertised commodity whose prepared visit ended without an exchange.
     *
     * @author lare96
     */
    data class Failure(val id: Int, val buying: Boolean)

    /**
     * Bounded, transient account history. Reads do not extend the six-hour lifetime of unsuccessful experience.
     *
     * @author lare96
     */
    internal class History {

        /**
         * One commodity direction's recent unsuccessful visits.
         *
         * @author lare96
         */
        private data class Entry(val steps: Int, val updated: Long)

        private val entries = linkedMapOf<Pair<Int, Boolean>, Entry>()

        internal val size: Int
            get() = entries.size

        fun unsuccessful(id: Int, buying: Boolean, now: Long): Boolean {
            entries.entries.removeIf { expired(it.value, now) }
            val key = id to buying
            val previous = entries[key]
            if (previous != null && now - previous.updated < 600_000L) return false
            entries.remove(key)
            entries[key] = Entry(minOf(4, (previous?.steps ?: 0) + 1), now)
            if (entries.size > 32) entries.remove(entries.keys.first())
            return true
        }

        fun completed(id: Int, buying: Boolean) {
            entries.remove(id to buying)
        }

        fun quote(id: Int, buying: Boolean, price: Long, now: Long,
                  minimum: Long = 1L, maximum: Long = Long.MAX_VALUE): Long {
            val key = id to buying
            val entry = entries[key]?.takeUnless { expired(it, now) }
            if (entry == null) entries.remove(key)
            val value = price.coerceAtLeast(1L)
            val percent = (entry?.steps ?: 0) * 5
            val adjustment = value / 100 * percent + value % 100 * percent / 100
            val adjusted = if (!buying) value - adjustment
                else if (adjustment > Long.MAX_VALUE - value) Long.MAX_VALUE else value + adjustment
            return if (buying) adjusted.coerceIn(1L, maximum.coerceAtLeast(1L))
                else maxOf(adjusted, minimum, 1L)
        }

        private fun expired(entry: Entry, now: Long): Boolean =
            now < entry.updated || now - entry.updated >= 21_600_000L
    }

    /**
     * Counts only sampled time during a prepared advertisement, excluding suspending negotiations and travel.
     * Long gaps earn at most two seconds and cannot turn an interrupted visit into hours of exposure.
     *
     * @author lare96
     */
    internal class Visit {
        private var advertised: Failure? = null
        private var unitPrice = 0L
        private var elapsed = 0L
        private var sampledAt: Long? = null

        fun advertise(id: Int, buying: Boolean, price: Long, now: Long) {
            val next = Failure(id, buying)
            if (advertised != next || unitPrice != price) {
                clear()
                advertised = next
                unitPrice = price
            }
            sample(now)
        }

        fun sample(now: Long) {
            if (advertised == null) return
            sampledAt?.let { elapsed += (now - it).coerceIn(0L, 2_000L) }
            sampledAt = now
        }

        fun pause() {
            sampledAt = null
        }

        fun clear() {
            advertised = null
            sampledAt = null
            elapsed = 0L
            unitPrice = 0L
        }

        fun unsuccessful(id: Int, buying: Boolean, price: Long): Failure? =
            advertised?.takeIf { it == Failure(id, buying) && unitPrice == price && elapsed >= 120_000L }
    }
}
