package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Checks player sale advertisements without loading the world, item cache, or nickname definitions.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementParserTest {

    private val names = mapOf(
        "flax" to intArrayOf(1779),
        "flaxes" to intArrayOf(1779),
        "coal" to intArrayOf(453),
        "iron" to intArrayOf(440),
        "lobs" to intArrayOf(379),
        "lobsters" to intArrayOf(379),
        "phat" to intArrayOf(1038, 1040, 1042),
        "prayer potion(4)" to intArrayOf(2434),
        "ppot 4" to intArrayOf(2434),
        "2h sword" to intArrayOf(1319),
        "tea" to intArrayOf(1978),
        "pestle and mortar" to intArrayOf(233),
        "ugthanki & tomato" to intArrayOf(1879),
        "egg and tomato" to intArrayOf(7064),
        "mushroom & onion" to intArrayOf(7066)
    )

    private val parser = PlayerTradeAdvertisementParser { phrase ->
        names[phrase.lowercase().trim().replace(Regex("\\s+"), " ")]
    }

    @Test fun readsTheIssuesExplicitUnitPriceExample() {
        val advertisement = parser.parse("selling flax 25 gp ea").single()

        assertEquals(setOf(1779), advertisement.itemIds)
        assertNull(advertisement.quantity)
        assertEquals(25L, advertisement.price?.coins)
        assertEquals(PriceBasis.EACH, advertisement.price?.basis)
        assertFalse(advertisement.requiresInspection)
    }

    @Test fun acceptsSaleShorthandAndNormalizesCaseAndSpacing() {
        for (message in listOf("sell flax 25gp each", "  WTS   FLAX   25  GP   EA  ")) {
            val advertisement = parser.parse(message).single()

            assertEquals(setOf(1779), advertisement.itemIds, message)
            assertEquals(25L, advertisement.price?.coins, message)
            assertEquals(PriceBasis.EACH, advertisement.price?.basis, message)
        }
    }

    @Test fun usesTheSuppliedResolverForPluralsAndNicknames() {
        for ((name, id) in listOf("flaxes" to 1779, "lobsters" to 379, "lobs" to 379)) {
            val advertisement = parser.parse("selling $name 100 gp ea").single()

            assertEquals(setOf(id), advertisement.itemIds, name)
        }
    }

    @Test fun keepsNoPriceAdvertisementsWithoutInventingAPriceOrQuantity() {
        val advertisement = parser.parse("selling flax").single()

        assertEquals(setOf(1779), advertisement.itemIds)
        assertNull(advertisement.quantity)
        assertNull(advertisement.price)
    }

    @Test fun keepsTheQuantityWhenAnAdvertisementHasNoPrice() {
        val advertisement = parser.parse("wts 100 flax").single()

        assertEquals(setOf(1779), advertisement.itemIds)
        assertEquals(100, advertisement.quantity)
        assertNull(advertisement.price)
    }

    @Test fun separatesCommaDelimitedOffersWithoutRepeatingTheSaleWord() {
        val advertisements = parser.parse("selling coal 150 ea, iron 100 ea")

        assertEquals(listOf(setOf(453), setOf(440)), advertisements.map { it.itemIds })
        assertEquals(listOf(150L, 100L), advertisements.map { it.price?.coins })
        assertTrue(advertisements.all { it.price?.basis == PriceBasis.EACH })
    }

    @Test fun separatesOffersJoinedWithAndWhenEachDescribesItsOwnPrice() {
        val advertisements = parser.parse("selling coal 150 ea and iron 100 ea")

        assertEquals(listOf(setOf(453), setOf(440)), advertisements.map { it.itemIds })
        assertEquals(listOf(150L, 100L), advertisements.map { it.price?.coins })
    }

    @Test fun expandsWholeItemQuantitiesExactlyFromDecimalAndSuffixedNumbers() {
        for ((quantity, expected) in listOf("1k" to 1_000, "1.5k" to 1_500,
            "0.5k" to 500, "2m" to 2_000_000, "1.5m" to 1_500_000)) {
            val advertisement = parser.parse("selling $quantity flax 25 gp ea").single()

            assertEquals(expected, advertisement.quantity, quantity)
            assertEquals(setOf(1779), advertisement.itemIds, quantity)
        }
    }

    @Test fun preservesThousandsSeparatorsInsideQuantityAndMoney() {
        val advertisement = parser.parse("selling 1,000 flax 1,500 gp ea").single()

        assertEquals(1_000, advertisement.quantity)
        assertEquals(1_500L, advertisement.price?.coins)
        assertEquals(PriceBasis.EACH, advertisement.price?.basis)
    }

    @Test fun expandsMoneySuffixesWithoutRoundingOrLosingPrecision() {
        for ((price, expected) in listOf("1.5k" to 1_500L, "2m" to 2_000_000L,
            "0.5k" to 500L)) {
            val advertisement = parser.parse("selling flax $price ea").single()

            assertEquals(expected, advertisement.price?.coins, price)
            assertEquals(PriceBasis.EACH, advertisement.price?.basis, price)
        }
    }

    @Test fun explicitTotalsKeepTheAdvertisedTotalAndQuantity() {
        val advertisement = parser.parse("selling 100 flax 2,500 gp total").single()

        assertEquals(100, advertisement.quantity)
        assertEquals(2_500L, advertisement.price?.coins)
        assertEquals(PriceBasis.TOTAL, advertisement.price?.basis)
        assertFalse(advertisement.requiresInspection)
    }

    @Test fun acceptsForBeforeAnExplicitTotal() {
        val advertisement = parser.parse("selling 100 flax for 2.5k total").single()

        assertEquals(100, advertisement.quantity)
        assertEquals(2_500L, advertisement.price?.coins)
        assertEquals(PriceBasis.TOTAL, advertisement.price?.basis)
    }

    @Test fun aTotalWhoseImpliedUnitPriceIsFractionalRemainsAnExactTotal() {
        val advertisement = parser.parse("selling 100 flax 25 gp total").single()

        assertEquals(100, advertisement.quantity)
        assertEquals(25L, advertisement.price?.coins)
        assertEquals(PriceBasis.TOTAL, advertisement.price?.basis)
    }

    @Test fun aTotalWithoutAQuantityRequiresInspection() {
        val advertisement = parser.parse("selling flax 250 gp total").single()

        assertNull(advertisement.quantity)
        assertEquals(250L, advertisement.price?.coins)
        assertEquals(PriceBasis.TOTAL, advertisement.price?.basis)
        assertTrue(advertisement.requiresInspection)
    }

    @Test fun barePricesDoNotBecomeGuessedUnitPricesOrTotals() {
        for (message in listOf("selling flax 25 gp", "selling 100 flax 25gp", "selling flax 25")) {
            val advertisement = parser.parse(message).single()

            assertEquals(25L, advertisement.price?.coins, message)
            assertEquals(PriceBasis.UNCLEAR, advertisement.price?.basis, message)
            assertTrue(advertisement.requiresInspection, message)
        }
    }

    @Test fun ambiguousAliasesKeepEveryCandidateForInspection() {
        val advertisement = parser.parse("selling phat 1m ea").single()

        assertEquals(setOf(1038, 1040, 1042), advertisement.itemIds)
        assertEquals(1_000_000L, advertisement.price?.coins)
        assertTrue(advertisement.requiresInspection)
    }

    @Test fun unknownNamesRemainInspectionOpportunitiesWithoutAnInventedItemId() {
        val advertisement = parser.parse("selling mysterious widget 1k ea").single()

        assertTrue(advertisement.itemIds.isEmpty())
        assertEquals(1_000L, advertisement.price?.coins)
        assertTrue(advertisement.requiresInspection)
    }

    @Test fun preservesDoseNumbersAndNumbersInsideItemNames() {
        for ((name, expected) in listOf("prayer potion(4)" to 2434, "ppot 4" to 2434, "2h sword" to 1319)) {
            val advertisement = parser.parse("selling $name 1k ea").single()

            assertEquals(setOf(expected), advertisement.itemIds, name)
            assertNull(advertisement.quantity, name)
            assertEquals(1_000L, advertisement.price?.coins, name)
        }
    }

    @Test fun anExplicitQuantityDoesNotConsumeAPotionDose() {
        val advertisement = parser.parse("selling 100 ppot 4 1k ea").single()

        assertEquals(setOf(2434), advertisement.itemIds)
        assertEquals(100, advertisement.quantity)
        assertEquals(1_000L, advertisement.price?.coins)
    }

    @Test fun acceptsTheLargestSupportedWholeItemQuantityWithoutClamping() {
        val advertisement = parser.parse("selling 2,147,483,647 flax 1 gp ea").single()

        assertEquals(Int.MAX_VALUE, advertisement.quantity)
    }

    @Test fun rejectsZeroNegativeFractionalAndOverflowingQuantities() {
        for (quantity in listOf("0", "-1", "1.5", "0.0001k", "2,147,483,648", "999999999999999999999999m")) {
            assertTrue(parser.parse("selling $quantity flax 25 gp ea").isEmpty(), quantity)
        }
    }

    @Test fun rejectsInvalidMoneyInsteadOfDowngradingItToAnUnpricedOffer() {
        for (price in listOf("0gp", "-1gp", "1.5gp", "0.0001k", "9223372036854775808gp",
            "999999999999999999999999m")) {
            assertTrue(parser.parse("selling flax $price ea").isEmpty(), price)
        }
    }

    @Test fun malformedPricedOffersDoNotDiscardValidNeighboringOffers() {
        for (message in listOf("selling coal 1.5gp ea, iron 100 ea",
            "selling coal 0gp ea and iron 100 ea", "selling coal 150 ea, iron -1gp ea")) {
            val advertisement = parser.parse(message).single()
            val expected = if (message.endsWith("iron -1gp ea")) 453 to 150L else 440 to 100L

            assertEquals(setOf(expected.first), advertisement.itemIds, message)
            assertEquals(expected.second, advertisement.price?.coins, message)
            assertEquals(PriceBasis.EACH, advertisement.price?.basis, message)
        }
    }

    @Test fun ignoresOrdinaryConversation() {
        for (message in listOf("price check flax", "who is selling flax?", "I saw someone selling flax 25 gp ea",
            "not selling flax", "hello", "selling", "")) {
            assertTrue(parser.parse(message).isEmpty(), message)
        }
    }

    @Test fun buyingShorthandRetainsThePlayersDirectionAndExactCommodity() {
        for (message in listOf("buying flax 25 gp ea", "wtb flax 25 gp ea", "b: flax 25 gp ea")) {
            val offer = parser.parse(message).single()
            assertEquals(PlayerTradeAdvertisement.Direction.BUY, offer.direction)
            assertEquals(setOf(1779), offer.itemIds)
            assertEquals(25L, offer.price?.coins)
        }
    }

    @Test fun preservesNamesContainingOfferConjunctions() {
        for ((name, id) in listOf("pestle and mortar" to 233, "ugthanki & tomato" to 1879,
            "egg and tomato" to 7064, "mushroom & onion" to 7066)) {
            val advertisement = parser.parse("selling $name 100 ea").single()

            assertEquals(setOf(id), advertisement.itemIds, name)
            assertEquals(100L, advertisement.price?.coins, name)
        }
    }

    @Test fun preservesAConjoinedItemNameWithinAMultiItemAdvertisement() {
        val advertisements = parser.parse("selling pestle and mortar 100 ea and iron 50 ea")

        assertEquals(listOf(setOf(233), setOf(440)), advertisements.map { it.itemIds })
        assertEquals(listOf(100L, 50L), advertisements.map { it.price?.coins })
    }

    @Test fun namesEndingInPriceMarkerLettersAreNotMistakenForPrices() {
        val advertisement = parser.parse("selling tea").single()

        assertEquals(setOf(1978), advertisement.itemIds)
        assertNull(advertisement.price)
    }

    @Test fun anUnpricedDoseNumberIsPartOfTheItemName() {
        val advertisement = parser.parse("selling 100 ppot 4").single()

        assertEquals(setOf(2434), advertisement.itemIds)
        assertEquals(100, advertisement.quantity)
        assertNull(advertisement.price)
    }

    @Test fun mixedBuyingClausesNeverBecomeSales() {
        for (message in listOf("selling coal 150 ea, buying iron 100 ea",
            "selling coal 150 ea and buying iron 100 ea")) {
            assertEquals(setOf(453), parser.parse(message).single().itemIds, message)
        }
    }

    @Test fun rejectsMalformedExplicitPriceWordsAndMissingItemNames() {
        for (message in listOf("selling flax NaN gp ea", "selling flax NaN gp", "selling 25 gp ea")) {
            assertTrue(parser.parse(message).isEmpty(), message)
        }
    }

    @Test fun rejectsUnsupportedAmountsWithAttachedCurrency() {
        for (message in listOf("selling flax 1e3gp", "selling flax NaNgp", "selling flax 1e3g")) {
            assertTrue(parser.parse(message).isEmpty(), message)
        }
    }

    @Test fun anUnknownItemEndingInGIsNotAnInvalidCoinAmount() {
        val advertisement = parser.parse("selling strange ring").single()

        assertEquals("strange ring", advertisement.itemName)
        assertTrue(advertisement.requiresInspection)
        assertNull(advertisement.price)
    }
}
