package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisement.Direction
import java.math.BigDecimal
import java.util.Locale

/**
 * Reads public buying and selling advertisements once, leaving live trade validation to the marketplace.
 *
 * Each separated clause carries its own quantity and price. Only explicit unit or total wording establishes a price
 * basis; a bare amount remains unclear. Name lookup is injected so parsing does not require a running world or cache.
 * Invalid amounts are rejected rather than clamped, rounded or reinterpreted as an offer without a price.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementParser(private val resolveItems: (String) -> IntArray?,
                                    private val resolveEquipmentRequests: (String) -> Map<Int, String>?) {

    constructor(resolveItems: (String) -> IntArray?) : this(resolveItems, PlayerTradeEquipmentRequests.live::resolve)

    /**
     * Bounded sale syntax and exact numeric tokens used by the parser.
     *
     * @author lare96
     */
    companion object {

        private const val MAX_MESSAGE_LENGTH = 256
        private const val MAX_ADVERTISEMENT_OFFERS = 64
        private const val MAX_EQUIPMENT_REQUEST_ITEMS = 32
        private const val NUMBER_TOKEN = "[+-]?(?:\\d[\\d,.]*|\\.\\d+)[km]?"
        private const val CURRENCY = "(?:gp|g|coins?)"
        private const val EACH = "(?:ea\\.?|each|per\\s+(?:item|unit|piece)|/\\s*(?:ea|each|item))"
        private const val TOTAL = "(?:total|for\\s+(?:the\\s+)?(?:lot|stack|bundle|all)|for\\s+all)"
        private val SELLING = Regex("""^(?:selling|sell|wts|s)(?:\s*[:>]\s*|\s*-(?=\s*[a-z])\s*|\s+)""")
        private val BUYING = Regex("""^(?:buying|buy|wtb|b)(?:\s*[:>]\s*|\s*-(?=\s*[a-z])\s*|\s+)""")
        private val SPACES = Regex("""\s+""")
        private val SEPARATORS = Regex("""(?<!\d),|,(?!\d)|\s*;\s*""")
        private val CONJUNCTIONS = Regex("""\s+and\s+|\s*&\s*""")
        private val NUMBER = Regex("""(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?[km]?""")
        private val LEADING_QUANTITY = Regex("^($NUMBER_TOKEN)(?:\\s*x\\s*|\\s+)(.+)$")
        private val EXPLICIT_PRICE = Regex("^(.+?)\\s+($NUMBER_TOKEN)\\s*(?:$CURRENCY\\s*)?($EACH|$TOTAL)[.!]*$")
        private val BARE_PRICE = Regex("^(.+?)\\s+($NUMBER_TOKEN)\\s*(?:$CURRENCY)?[.!]*$")
        private val PRICE_ENDING = Regex("(?:^|\\s)(?:$EACH|$TOTAL)[.!]*$")
        private val CURRENCY_ENDING = Regex("(?:^|\\s)(?:\\S*(?:gp|coins?)|[+-]?(?:\\d\\S*|nan|infinity)g|g)[.!]*$")
        private val EACH_MARKER = Regex("^$EACH$")
    }

    /**
     * Parses independently described sales, preserving valid clauses when another clause has an invalid amount.
     * Non-sale chat and messages beyond the parser's bounded input size produce no advertisements.
     *
     * @param text The public message as typed.
     * @return Sale clauses in their original order, without choosing a buyer or interpreting unclear prices.
     */
    fun parse(text: String): List<PlayerTradeAdvertisement> = parseAdvertisement(text).orEmpty()

    /**
     * Distinguishes ordinary conversation from a new sales advertisement whose invalid clauses withdraw old terms.
     * A recognized but empty sale returns an empty list; unrelated chat returns `null`.
     */
    fun parseAdvertisement(text: String): List<PlayerTradeAdvertisement>? {
        if (text.length > MAX_MESSAGE_LENGTH || text.any { it == '\n' || it == '\r' }) return null
        val normalized = text.lowercase(Locale.ROOT).trim().replace(SPACES, " ")
        val direction = if (SELLING.containsMatchIn(normalized)) Direction.SELL else Direction.BUY
        val prefix = (if (direction == Direction.SELL) SELLING else BUYING).find(normalized) ?: return null
        val result = ArrayList<PlayerTradeAdvertisement>()
        for (clause in normalized.substring(prefix.range.last + 1).split(SEPARATORS)) {
            for (offer in parseOffers(clause, direction)) {
                val expanded = expandEquipmentRequest(offer)
                if (result.size + expanded.size > MAX_ADVERTISEMENT_OFFERS) return emptyList()
                result += expanded
            }
        }
        return result
    }

    /**
     * Makes a material category an explicit list of independently priced items rather than guessing a full set.
     * A quantity applies to each named commodity. A category total or unclear price cannot be copied into several
     * independent purchases. Sales and already resolved exact names or nicknames retain their existing semantics.
     */
    private fun expandEquipmentRequest(offer: PlayerTradeAdvertisement): List<PlayerTradeAdvertisement> {
        if (offer.direction != Direction.BUY || offer.itemIds.isNotEmpty()) return listOf(offer)
        val members = resolveEquipmentRequests(offer.itemName) ?: return listOf(offer)
        if (members.size > MAX_EQUIPMENT_REQUEST_ITEMS) return emptyList()
        if (offer.price != null && offer.price.basis != PriceBasis.EACH) return emptyList()
        return members.map { (id, name) ->
            offer.copy(itemName = name, itemIds = setOf(id), quantity = offer.quantity ?: 1)
        }
    }

    /**
     * Prefers complete resolved names before interpreting conjunctions as separate offers. A long name such as
     * "pestle and mortar" stays intact even when another sale follows it in the same clause.
     */
    private fun parseOffers(clause: String, direction: Direction): List<PlayerTradeAdvertisement> {
        val text = clause.trim()
        val opposite = if (direction == Direction.SELL) BUYING else SELLING
        if (opposite.containsMatchIn(text)) return emptyList()
        val whole = parseClause(text, direction)
        if (whole?.itemIds?.isNotEmpty() == true) return listOf(whole)
        val separators = CONJUNCTIONS.findAll(text).toList()
        for (separator in separators.asReversed()) {
            val first = parseClause(text.substring(0, separator.range.first), direction)
            if (first?.itemIds?.isNotEmpty() == true) {
                return listOf(first) + parseOffers(text.substring(separator.range.last + 1), direction)
            }
        }
        val first = separators.firstOrNull() ?: return listOfNotNull(whole)
        return listOfNotNull(parseClause(text.substring(0, first.range.first), direction)) +
                parseOffers(text.substring(first.range.last + 1), direction)
    }

    /**
     * Extracts one item's terms without taking dose, charge or equipment numbers out of a resolved item name.
     */
    private fun parseClause(clause: String, direction: Direction): PlayerTradeAdvertisement? {
        var name = clause.trim()
        val opposite = if (direction == Direction.SELL) BUYING else SELLING
        val prefix = if (direction == Direction.SELL) SELLING else BUYING
        if (name.isEmpty() || opposite.containsMatchIn(name)) return null
        prefix.find(name)?.let { name = name.substring(it.range.last + 1).trim() }

        var price: Price? = null
        val explicit = EXPLICIT_PRICE.matchEntire(name)
        if (explicit != null) {
            val coins = wholeNumber(explicit.groupValues[2], Long.MAX_VALUE) ?: return null
            val basis = if (EACH_MARKER.matches(explicit.groupValues[3])) PriceBasis.EACH else PriceBasis.TOTAL
            price = Price(coins, basis)
            name = explicit.groupValues[1].removeSuffix(" for").trim()
        } else if (PRICE_ENDING.containsMatchIn(name)) {
            // A malformed explicit price must not turn into an unpriced, unknown-item advertisement.
            return null
        }

        var quantity: Int? = null
        LEADING_QUANTITY.matchEntire(name)?.let {
            quantity = (wholeNumber(it.groupValues[1], Int.MAX_VALUE.toLong()) ?: return null).toInt()
            name = it.groupValues[2].trim()
        }

        // Resolve the complete name first: "ppot 4" names a dose, not an unspecified price of four coins.
        var ids = resolveItems(name)
        if (price == null && ids?.isNotEmpty() != true) {
            val bare = BARE_PRICE.matchEntire(name)
            if (bare == null && CURRENCY_ENDING.containsMatchIn(name)) return null
            bare?.let {
                val coins = wholeNumber(it.groupValues[2], Long.MAX_VALUE) ?: return null
                price = Price(coins, PriceBasis.UNCLEAR)
                name = it.groupValues[1].removeSuffix(" for").trim()
                ids = resolveItems(name)
            }
        }
        if (name.none { it.isLetter() } || NUMBER.matches(name)) return null
        return PlayerTradeAdvertisement(name, ids?.toSet().orEmpty(), quantity, price, direction)
    }

    /**
     * Expands k/m abbreviations exactly, accepting only positive whole amounts within the requested bound.
     */
    private fun wholeNumber(text: String, maximum: Long): Long? {
        if (!NUMBER.matches(text)) return null
        val multiplier = when (text.last()) {
            'k' -> 1_000L
            'm' -> 1_000_000L
            else -> 1L
        }
        val digits = if (multiplier == 1L) text else text.dropLast(1)
        val amount = digits.replace(",", "").toBigDecimalOrNull()?.multiply(BigDecimal.valueOf(multiplier)) ?: return null
        return try {
            amount.longValueExact().takeIf { it in 1..maximum }
        } catch (_: ArithmeticException) {
            null
        }
    }
}
