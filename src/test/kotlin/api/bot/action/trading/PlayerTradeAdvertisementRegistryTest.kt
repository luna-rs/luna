package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import io.luna.LunaContext
import io.luna.game.model.def.CombatStyleDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerCredentials
import io.luna.game.model.mob.bot.Bot
import io.luna.util.parser.impl.WeaponTypeDefinitionFileParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant

/**
 * Checks lifetime, remaining stock, semantic refreshes, and exclusive response ownership with an injected clock.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementRegistryTest {

    private var now = Instant.EPOCH
    private val registry = PlayerTradeAdvertisementRegistry { now }
    private val seller = mock(Player::class.java)
    private val firstBuyer = mock(Bot::class.java)
    private val secondBuyer = mock(Bot::class.java)

    private fun offer(name: String = "flax", ids: Set<Int> = setOf(1779), quantity: Int? = 100,
                      coins: Long? = 25L, basis: PriceBasis = PriceBasis.EACH): PlayerTradeAdvertisement =
        PlayerTradeAdvertisement(name, ids, quantity, coins?.let { Price(it, basis) })

    /**
     * Uses real account equality while leaving the player unregistered and the world mocked.
     * Supplies the widget placeholders and unarmed metadata used during construction without loading a game cache.
     */
    private fun playerSession(username: String): Player {
        for (id in listOf(5292, 5063, 8880, 8866, 8899, 8938)) {
            if (WidgetDefinition.ALL.get(id).isEmpty) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
        if (CombatStyleDefinition.ALL.isEmpty()) WeaponTypeDefinitionFileParser().run()
        return Player(mock(LunaContext::class.java, RETURNS_DEEP_STUBS), PlayerCredentials(username, "test"))
    }

    @Test fun publishesOneLookupEntryForThePlayerWithASixtySecondLifetime() {
        val entry = registry.publish(seller, listOf(offer()))!!

        assertSame(seller, entry.player)
        assertEquals(listOf(offer()), entry.offers)
        assertEquals(Instant.EPOCH.plusSeconds(60), entry.expiresAt)
        assertSame(entry, registry.find(seller))
    }

    @Test fun availabilityLookupsDoNotConsumeOrClaimAdvertisements() {
        val entry = registry.publish(seller, listOf(offer()))!!

        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertEquals(listOf(entry), registry.availableFor(secondBuyer))
        assertNotNull(registry.claim(entry, firstBuyer))
    }

    @Test fun expiresAtExactlySixtySecondsRatherThanAfterTheBoundary() {
        val entry = registry.publish(seller, listOf(offer()))!!
        now = Instant.EPOCH.plusSeconds(59)
        assertSame(entry, registry.find(seller))

        now = Instant.EPOCH.plusSeconds(60)

        assertNull(registry.find(seller))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(entry, firstBuyer))
    }

    @Test fun identicalTermsRefreshTheExistingEntryAndExtendItsLifetime() {
        val entry = registry.publish(seller, listOf(offer()))!!
        now = Instant.EPOCH.plusSeconds(59)

        assertSame(entry, registry.publish(seller, listOf(offer())))
        assertEquals(Instant.EPOCH.plusSeconds(119), entry.expiresAt)
        now = Instant.EPOCH.plusSeconds(60)
        assertSame(entry, registry.find(seller))
        now = Instant.EPOCH.plusSeconds(119)
        assertNull(registry.find(seller))
    }

    @Test fun refreshingAnActiveEntryPreservesItsResponseOwnership() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(entry, firstBuyer)!!
        now = Instant.EPOCH.plusSeconds(59)

        assertSame(entry, registry.publish(seller, listOf(offer())))
        now = Instant.EPOCH.plusSeconds(60)

        assertTrue(registry.isCurrent(response))
        assertSame(entry, registry.find(seller))
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
    }

    @Test fun knownAliasesWithTheSameIdsAndTermsShareTheExistingEntry() {
        val entry = registry.publish(seller, listOf(offer(name = "lobsters", ids = setOf(379))))!!
        val response = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(response))

        assertSame(entry, registry.publish(seller, listOf(offer(name = "lobs", ids = setOf(379)))))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(entry, firstBuyer))
        assertEquals(listOf(entry), registry.availableFor(secondBuyer))
    }

    @Test fun reorderedClausesAndDuplicateOffersDoNotRestartResponses() {
        val flax = offer()
        val coal = offer(name = "coal", ids = setOf(453), coins = 150L)
        val entry = registry.publish(seller, listOf(flax, coal))!!
        val response = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(response))

        assertSame(entry, registry.publish(seller, listOf(coal, flax, coal, flax)))
        assertEquals(2, entry.offers.size)
        assertNull(registry.claim(entry, firstBuyer))
        assertEquals(listOf(entry), registry.availableFor(secondBuyer))
    }

    @Test fun deduplicatesAliasesRepresentingTheSameKnownOffer() {
        val fullName = offer(name = "lobsters", ids = setOf(379))
        val nickname = offer(name = "lobs", ids = setOf(379))
        val entry = registry.publish(seller, listOf(fullName, nickname, fullName))!!

        assertEquals(1, entry.offers.size)
        assertSame(entry, registry.publish(seller, listOf(nickname)))
    }

    @Test fun unknownNamesIgnoreCaseAndRepeatedWhitespaceForIdentity() {
        val entry = registry.publish(seller, listOf(offer(name = "Mystery Widget", ids = emptySet())))!!
        val response = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(response))

        assertSame(entry, registry.publish(seller,
            listOf(offer(name = "  mystery   WIDGET  ", ids = emptySet()))))
        assertNull(registry.claim(entry, firstBuyer))
    }

    @Test fun differentUnknownNamesAreDifferentTerms() {
        val first = registry.publish(seller, listOf(offer(name = "mystery widget", ids = emptySet())))!!
        val second = registry.publish(seller, listOf(offer(name = "mystery trinket", ids = emptySet())))!!

        assertNotSame(first, second)
        assertSame(second, registry.find(seller))
        assertNull(registry.claim(first, firstBuyer))
    }

    @Test fun changesToItemCandidatesQuantityMoneyOrPriceBasisReplaceTheEntry() {
        val changes = listOf(
            offer(ids = setOf(1779, 453)),
            offer(quantity = 101),
            offer(quantity = null),
            offer(coins = 26L),
            offer(coins = null),
            offer(basis = PriceBasis.TOTAL),
            offer(basis = PriceBasis.UNCLEAR)
        )
        for (changed in changes) {
            val localRegistry = PlayerTradeAdvertisementRegistry { now }
            val first = localRegistry.publish(seller, listOf(offer()))!!
            val second = localRegistry.publish(seller, listOf(changed))!!

            assertNotSame(first, second, changed.toString())
            assertSame(second, localRegistry.find(seller), changed.toString())
            assertNull(localRegistry.claim(first, firstBuyer), changed.toString())
        }
    }

    @Test fun onlyOneBuyerCanClaimASellersAdvertisementAtATime() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(entry, firstBuyer)!!

        assertSame(entry, response.advertisement)
        assertSame(firstBuyer, response.buyer)
        assertTrue(registry.isCurrent(response))
        assertSame(entry, registry.find(seller))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertNull(registry.claim(entry, firstBuyer))
        assertNull(registry.claim(entry, secondBuyer))
    }

    @Test fun releasingAllowsAnotherBuyerButDoesNotRepeatTheSameBuyersAttempt() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.release(response))
        assertFalse(registry.isCurrent(response))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(entry, firstBuyer))
        assertEquals(listOf(entry), registry.availableFor(secondBuyer))
        assertNotNull(registry.claim(entry, secondBuyer))
    }

    @Test fun genuinelyDifferentTermsPermitTheSameBuyerToRespondAgain() {
        val first = registry.publish(seller, listOf(offer()))!!
        assertTrue(registry.release(registry.claim(first, firstBuyer)!!))
        val second = registry.publish(seller, listOf(offer(coins = 20L)))!!

        assertEquals(listOf(second), registry.availableFor(firstBuyer))
        assertNotNull(registry.claim(second, firstBuyer))
    }

    @Test fun replacementInvalidatesTheOldResponseWithoutFreeingTheSellerSlot() {
        val first = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(first, firstBuyer)!!
        val second = registry.publish(seller, listOf(offer(coins = 20L)))!!

        assertFalse(registry.isCurrent(response))
        assertSame(second, registry.find(seller))
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertNull(registry.claim(second, secondBuyer))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(second, secondBuyer))
    }

    @Test fun removalInvalidatesTheResponseButRepublishingStillWaitsForItsRelease() {
        val first = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(first, firstBuyer)!!

        registry.remove(seller)

        assertNull(registry.find(seller))
        assertFalse(registry.isCurrent(response))
        assertNull(registry.claim(first, secondBuyer))
        val second = registry.publish(seller, listOf(offer()))!!
        assertNotSame(first, second)
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertNull(registry.claim(second, secondBuyer))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(second, secondBuyer))
    }

    @Test fun expiryInvalidatesTheResponseButNewAdvertisingWaitsForItsRelease() {
        val first = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(first, firstBuyer)!!
        now = Instant.EPOCH.plusSeconds(60)

        assertNull(registry.find(seller))
        assertFalse(registry.isCurrent(response))
        val second = registry.publish(seller, listOf(offer()))!!
        assertNotSame(first, second)
        assertNull(registry.claim(second, secondBuyer))
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(second, secondBuyer))
    }

    @Test fun aRepeatedStaleReleaseCannotFreeANewerBuyersClaim() {
        val first = registry.publish(seller, listOf(offer()))!!
        val oldResponse = registry.claim(first, firstBuyer)!!
        val second = registry.publish(seller, listOf(offer(coins = 20L)))!!
        assertTrue(registry.release(oldResponse))
        val newResponse = registry.claim(second, secondBuyer)!!

        assertFalse(registry.release(oldResponse))
        assertTrue(registry.isCurrent(newResponse))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(second, firstBuyer))
        assertTrue(registry.release(newResponse))
    }

    @Test fun publishingAnEmptyListRemovesTheAdvertisement() {
        val first = registry.publish(seller, listOf(offer()))!!

        assertNull(registry.publish(seller, emptyList()))
        assertNull(registry.find(seller))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(first, firstBuyer))
    }

    @Test fun publishingAnEmptyListDoesNotReleaseAnOutstandingResponse() {
        val first = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(first, firstBuyer)!!
        assertNull(registry.publish(seller, emptyList()))
        val second = registry.publish(seller, listOf(offer(coins = 20L)))!!

        assertFalse(registry.isCurrent(response))
        assertNull(registry.claim(second, secondBuyer))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(second, secondBuyer))
    }

    @Test fun rejectingAStaleSnapshotDoesNotConsumeTheBuyersNewOpportunity() {
        val first = registry.publish(seller, listOf(offer()))!!
        val second = registry.publish(seller, listOf(offer(coins = 20L)))!!

        assertNull(registry.claim(first, firstBuyer))
        assertEquals(listOf(second), registry.availableFor(firstBuyer))
        assertNotNull(registry.claim(second, firstBuyer))
    }

    @Test fun availabilityOmitsOnlyTheClaimedSellerAndTheBuyersAttemptedEntries() {
        val otherSeller = mock(Player::class.java)
        val first = registry.publish(seller, listOf(offer()))!!
        val second = registry.publish(otherSeller, listOf(offer(name = "coal", ids = setOf(453))))!!
        val response = registry.claim(first, firstBuyer)!!

        assertEquals(listOf(second), registry.availableFor(secondBuyer))
        assertTrue(registry.release(response))
        assertEquals(listOf(second), registry.availableFor(firstBuyer))
        assertEquals(setOf(first, second), registry.availableFor(secondBuyer).toSet())
    }

    @Test fun snapshotsThePublishedListAndItemIdSets() {
        val mutableIds = linkedSetOf(1779)
        val mutableOffers = mutableListOf(offer(ids = mutableIds))
        val entry = registry.publish(seller, mutableOffers)!!

        mutableIds.clear()
        mutableIds += 453
        mutableOffers.clear()
        mutableOffers += offer(name = "iron", ids = setOf(440))

        assertEquals(1, entry.offers.size)
        assertEquals(setOf(1779), entry.offers.single().itemIds)
        assertEquals("flax", entry.offers.single().itemName)
        assertSame(entry, registry.publish(seller, listOf(offer())))
        assertNotNull(registry.claim(entry, firstBuyer))
    }

    @Test fun reconnectingWithTheSameAccountCreatesANewSessionOwnedEntry() {
        val oldSession = playerSession("same seller")
        val newSession = playerSession("same seller")
        assertEquals(oldSession, newSession)
        assertEquals(oldSession.hashCode(), newSession.hashCode())
        assertNotSame(oldSession, newSession)
        val first = registry.publish(oldSession, listOf(offer()))!!
        assertNull(registry.find(newSession))

        val second = registry.publish(newSession, listOf(offer()))!!

        assertNotSame(first, second)
        assertSame(newSession, second.player)
        assertNull(registry.find(oldSession))
        assertSame(second, registry.find(newSession))
        assertEquals(listOf(second), registry.availableFor(firstBuyer))
        assertNull(registry.claim(first, firstBuyer))
        assertNotNull(registry.claim(second, firstBuyer))
    }

    @Test fun oldSessionRemovalCannotDeleteTheReconnectedSessionsAdvertisement() {
        val oldSession = playerSession("same seller")
        val newSession = playerSession("same seller")
        registry.publish(oldSession, listOf(offer()))
        val second = registry.publish(newSession, listOf(offer()))!!

        registry.remove(oldSession)

        assertSame(second, registry.find(newSession))
        assertNull(registry.find(oldSession))
        assertEquals(listOf(second), registry.availableFor(firstBuyer))
        registry.remove(newSession)
        assertNull(registry.find(newSession))
    }

    @Test fun oldSessionEmptyPublishingCannotDeleteTheReconnectedSessionsAdvertisement() {
        val oldSession = playerSession("same seller")
        val newSession = playerSession("same seller")
        registry.publish(oldSession, listOf(offer()))
        val second = registry.publish(newSession, listOf(offer()))!!

        assertNull(registry.publish(oldSession, emptyList()))

        assertSame(second, registry.find(newSession))
        assertNull(registry.find(oldSession))
        assertNotNull(registry.claim(second, firstBuyer))
    }

    @Test fun reconnectingCannotFreeTheAccountsOutstandingResponseSlot() {
        val oldSession = playerSession("same seller")
        val newSession = playerSession("same seller")
        val first = registry.publish(oldSession, listOf(offer()))!!
        val oldResponse = registry.claim(first, firstBuyer)!!
        val second = registry.publish(newSession, listOf(offer()))!!

        assertFalse(registry.isCurrent(oldResponse))
        assertNull(registry.find(oldSession))
        assertSame(second, registry.find(newSession))
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertNull(registry.claim(second, secondBuyer))
        registry.remove(oldSession)
        assertNull(registry.publish(oldSession, emptyList()))
        assertSame(second, registry.find(newSession))
        assertNull(registry.claim(second, secondBuyer))

        assertTrue(registry.release(oldResponse))
        val newResponse = registry.claim(second, secondBuyer)!!

        assertTrue(registry.isCurrent(newResponse))
        assertFalse(registry.release(oldResponse))
        assertTrue(registry.isCurrent(newResponse))
    }

    @Test fun aPartialSaleKeepsRemainingStockUntilTheExistingExpiry() {
        val entry = registry.publish(seller, listOf(offer(quantity = 1_000)))!!
        val advertised = entry.offers.single()
        val response = registry.claim(entry, firstBuyer)!!
        now = Instant.EPOCH.plusSeconds(10)

        assertTrue(registry.recordCompleted(response, mapOf(advertised to 100)))

        assertEquals(900, entry.remainingQuantity(advertised))
        assertEquals(1_000, advertised.quantity)
        assertEquals(Instant.EPOCH.plusSeconds(60), entry.expiresAt)
        assertSame(entry, registry.find(seller))
        assertEquals(listOf(advertised), entry.availableOffers)
        assertNull(registry.claim(entry, secondBuyer))
        assertTrue(registry.release(response))
        assertEquals(listOf(entry), registry.availableFor(secondBuyer))
        assertNotNull(registry.claim(entry, secondBuyer))
    }

    @Test fun aPartialTotalSalePreservesTheOriginalPriceDenominator() {
        val entry = registry.publish(seller,
            listOf(offer(quantity = 1_000, coins = 25_000L, basis = PriceBasis.TOTAL)))!!
        val advertised = entry.offers.single()
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(advertised to 100)))

        assertEquals(900, entry.remainingQuantity(advertised))
        assertEquals(1_000, advertised.quantity)
        assertEquals(25_000L, advertised.price?.coins)
        val quote = PlayerTradeAdvertisementPolicy.Quote(advertised.price!!, false)
        assertEquals(22_500L, PlayerTradeAdvertisementPayment.cost(advertised, quote, 900))
    }

    @Test fun identicalRefreshesRetainTheRemainingStockAndAttemptHistory() {
        val advertised = offer(quantity = 1_000)
        val entry = registry.publish(seller, listOf(advertised))!!
        val response = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.recordCompleted(response, mapOf(entry.offers.single() to 100)))
        assertTrue(registry.release(response))
        now = Instant.EPOCH.plusSeconds(59)

        assertSame(entry, registry.publish(seller, listOf(advertised)))

        assertEquals(900, entry.remainingQuantity(entry.offers.single()))
        assertEquals(Instant.EPOCH.plusSeconds(119), entry.expiresAt)
        assertNull(registry.claim(entry, firstBuyer))
        assertNotNull(registry.claim(entry, secondBuyer))
    }

    @Test fun completedDeliveriesUpdateOnlyTheirOwnOffers() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100),
            offer(name = "coal", ids = setOf(453), quantity = 60)))!!
        val flax = entry.offers[0]
        val coal = entry.offers[1]
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(flax to 20)))

        assertEquals(80, entry.remainingQuantity(flax))
        assertEquals(60, entry.remainingQuantity(coal))
        assertEquals(listOf(flax, coal), entry.availableOffers)
        assertTrue(registry.release(response))
        val secondResponse = registry.claim(entry, secondBuyer)!!
        assertTrue(registry.recordCompleted(secondResponse, mapOf(flax to 80, coal to 10)))
        assertEquals(0, entry.remainingQuantity(flax))
        assertEquals(50, entry.remainingQuantity(coal))
        assertEquals(listOf(coal), entry.availableOffers)
    }

    @Test fun fullConsumptionWithdrawsTheAdWithoutReleasingItsResponseSlot() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = entry.offers.single()
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(advertised to 100)))

        assertEquals(0, entry.remainingQuantity(advertised))
        assertTrue(entry.availableOffers.isEmpty())
        assertNull(registry.find(seller))
        assertFalse(registry.isCurrent(response))
        val replacement = registry.publish(seller, listOf(offer(quantity = 100)))!!
        assertNull(registry.claim(replacement, secondBuyer))
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(replacement, secondBuyer))
    }

    @Test fun anUnspecifiedStockQuantityRemainsUnspecifiedAfterDelivery() {
        val entry = registry.publish(seller, listOf(offer(quantity = null)))!!
        val advertised = entry.offers.single()
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(advertised to 100)))

        assertNull(entry.remainingQuantity(advertised))
        assertNull(advertised.quantity)
        assertSame(entry, registry.find(seller))
        assertEquals(listOf(advertised), entry.availableOffers)
        assertFalse(registry.recordCompleted(response, mapOf(advertised to 100)))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(entry, secondBuyer))
    }

    @Test fun unspecifiedOffersKeepAMixedAdvertisementAvailableAfterKnownStockRunsOut() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100),
            offer(name = "coal", ids = setOf(453), quantity = null)))!!
        val known = entry.offers[0]
        val unspecified = entry.offers[1]
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(known to 100)))

        assertEquals(0, entry.remainingQuantity(known))
        assertNull(entry.remainingQuantity(unspecified))
        assertEquals(listOf(unspecified), entry.availableOffers)
        assertSame(entry, registry.find(seller))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(entry, secondBuyer))
    }

    @Test fun repeatedCompletionCannotSubtractTheSameDeliveryTwice() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = entry.offers.single()
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(registry.recordCompleted(response, mapOf(advertised to 20)))
        assertFalse(registry.recordCompleted(response, mapOf(advertised to 20)))

        assertEquals(80, entry.remainingQuantity(advertised))
    }

    @Test fun invalidDeliveriesAreRejectedBeforeAnyStockChanges() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100),
            offer(name = "coal", ids = setOf(453), quantity = 60)))!!
        val flax = entry.offers[0]
        val coal = entry.offers[1]
        val response = registry.claim(entry, firstBuyer)!!
        val unrelated = offer(name = "iron", ids = setOf(440))
        val invalid = listOf(emptyMap(), mapOf(flax to 0), mapOf(flax to -1),
            mapOf(unrelated to 1), mapOf(flax to 101), mapOf(flax to 10, coal to 61))

        for (deliveries in invalid) {
            assertFalse(registry.recordCompleted(response, deliveries), deliveries.toString())
            assertEquals(100, entry.remainingQuantity(flax))
            assertEquals(60, entry.remainingQuantity(coal))
        }
        assertTrue(registry.recordCompleted(response, mapOf(flax to 10, coal to 10)))
        assertEquals(90, entry.remainingQuantity(flax))
        assertEquals(50, entry.remainingQuantity(coal))
    }

    @Test fun anOldResponseCannotAccountAgainstAReplacementAdvertisement() {
        val first = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = first.offers.single()
        val response = registry.claim(first, firstBuyer)!!
        val replacement = registry.publish(seller, listOf(offer(quantity = 200)))!!

        assertFalse(registry.recordCompleted(response, mapOf(advertised to 20)))

        assertEquals(100, first.remainingQuantity(advertised))
        assertEquals(200, replacement.remainingQuantity(replacement.offers.single()))
        assertSame(replacement, registry.find(seller))
        assertNull(registry.claim(replacement, secondBuyer))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(replacement, secondBuyer))
    }

    @Test fun completionAtExpiryCannotChangeAFreshAdvertisement() {
        val first = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = first.offers.single()
        val response = registry.claim(first, firstBuyer)!!
        now = Instant.EPOCH.plusSeconds(60)

        assertFalse(registry.recordCompleted(response, mapOf(advertised to 20)))
        val replacement = registry.publish(seller, listOf(offer(quantity = 100)))!!
        assertFalse(registry.recordCompleted(response, mapOf(advertised to 20)))

        assertEquals(100, replacement.remainingQuantity(replacement.offers.single()))
        assertEquals(100, first.remainingQuantity(advertised))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(replacement, secondBuyer))
    }

    @Test fun aReleasedResponseCannotAccountDuringAnotherBuyersAttempt() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = entry.offers.single()
        val firstResponse = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(firstResponse))
        val secondResponse = registry.claim(entry, secondBuyer)!!

        assertFalse(registry.recordCompleted(firstResponse, mapOf(advertised to 20)))

        assertEquals(100, entry.remainingQuantity(advertised))
        assertTrue(registry.recordCompleted(secondResponse, mapOf(advertised to 10)))
        assertEquals(90, entry.remainingQuantity(advertised))
    }

    @Test fun unrelatedStockLookupsCannotAppearToBeUnspecifiedOffers() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!

        assertThrows(IllegalArgumentException::class.java) {
            entry.remainingQuantity(offer(name = "iron", ids = setOf(440)))
        }
    }

    @Test fun resumeCannotCreateAFirstUnsolicitedAttempt() {
        val entry = registry.publish(seller, listOf(offer()))!!

        assertNull(registry.resume(entry, firstBuyer))

        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertNotNull(registry.claim(entry, firstBuyer))
    }

    @Test fun aReleasedPreviousAttemptCanResumeWithoutExtendingItsAdvertisement() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val previous = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(previous))
        now = Instant.EPOCH.plusSeconds(59)

        val resumed = registry.resume(entry, firstBuyer)!!

        assertNotSame(previous, resumed)
        assertSame(entry, resumed.advertisement)
        assertSame(firstBuyer, resumed.buyer)
        assertTrue(registry.isCurrent(resumed))
        assertEquals(Instant.EPOCH.plusSeconds(60), entry.expiresAt)
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertTrue(registry.release(resumed))
        assertNull(registry.claim(entry, firstBuyer))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
    }

    @Test fun resumeCannotReplaceAnAlreadyActiveResponseFromTheSameBuyer() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val response = registry.claim(entry, firstBuyer)!!

        assertNull(registry.resume(entry, firstBuyer))

        assertTrue(registry.isCurrent(response))
        assertTrue(registry.release(response))
        assertNotNull(registry.resume(entry, firstBuyer))
    }

    @Test fun resumeWaitsForAnotherBuyersActiveResponseToBeReleased() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val previous = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(previous))
        val other = registry.claim(entry, secondBuyer)!!

        assertNull(registry.resume(entry, firstBuyer))

        assertTrue(registry.isCurrent(other))
        assertTrue(registry.release(other))
        assertNotNull(registry.resume(entry, firstBuyer))
    }

    @Test fun removedAndReplacedAdvertisementsCannotResumeOldQuotes() {
        val first = registry.publish(seller, listOf(offer()))!!
        assertTrue(registry.release(registry.claim(first, firstBuyer)!!))
        registry.remove(seller)
        assertNull(registry.resume(first, firstBuyer))
        val replacement = registry.publish(seller, listOf(offer(coins = 20L)))!!

        assertNull(registry.resume(first, firstBuyer))
        assertNull(registry.resume(replacement, firstBuyer))
        assertSame(replacement, registry.find(seller))
        assertNotNull(registry.claim(replacement, firstBuyer))
    }

    @Test fun expiredQuotesCannotResumeOrTransferTheirAttemptHistoryToAFreshAd() {
        val first = registry.publish(seller, listOf(offer()))!!
        assertTrue(registry.release(registry.claim(first, firstBuyer)!!))
        now = Instant.EPOCH.plusSeconds(60)

        assertNull(registry.resume(first, firstBuyer))

        val replacement = registry.publish(seller, listOf(offer()))!!
        assertNull(registry.resume(first, firstBuyer))
        assertNull(registry.resume(replacement, firstBuyer))
        assertNotNull(registry.claim(replacement, firstBuyer))
    }

    @Test fun fullyConsumedAdvertisementsCannotResume() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val response = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.recordCompleted(response, mapOf(entry.offers.single() to 100)))
        assertTrue(registry.release(response))

        assertNull(registry.resume(entry, firstBuyer))

        assertTrue(entry.availableOffers.isEmpty())
        assertNull(registry.find(seller))
    }

    @Test fun eachResumedResponseAccountsItsDeliveryOnlyOnce() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100)))!!
        val advertised = entry.offers.single()
        val previous = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.recordCompleted(previous, mapOf(advertised to 20)))
        assertTrue(registry.release(previous))

        val resumed = registry.resume(entry, firstBuyer)!!
        assertTrue(registry.recordCompleted(resumed, mapOf(advertised to 30)))

        assertFalse(registry.recordCompleted(resumed, mapOf(advertised to 30)))
        assertFalse(registry.recordCompleted(previous, mapOf(advertised to 20)))
        assertEquals(50, entry.remainingQuantity(advertised))
        assertTrue(registry.release(resumed))
        assertNull(registry.claim(entry, firstBuyer))
        val again = registry.resume(entry, firstBuyer)!!
        assertTrue(registry.recordCompleted(again, mapOf(advertised to 10)))
        assertEquals(40, entry.remainingQuantity(advertised))
    }

    @Test fun oldResponseCleanupCannotReleaseAResumedAttempt() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val previous = registry.claim(entry, firstBuyer)!!
        assertTrue(registry.release(previous))
        val resumed = registry.resume(entry, firstBuyer)!!

        assertFalse(registry.release(previous))
        assertFalse(registry.recordCompleted(previous, mapOf(entry.offers.single() to 10)))

        assertTrue(registry.isCurrent(resumed))
        assertNull(registry.claim(entry, secondBuyer))
        assertTrue(registry.release(resumed))
    }

    @Test fun reconnectingCannotResumeAnOldSessionsAdvertisement() {
        val oldSession = playerSession("same seller")
        val newSession = playerSession("same seller")
        val first = registry.publish(oldSession, listOf(offer()))!!
        assertTrue(registry.release(registry.claim(first, firstBuyer)!!))
        val replacement = registry.publish(newSession, listOf(offer()))!!

        assertNull(registry.resume(first, firstBuyer))
        assertNull(registry.resume(replacement, firstBuyer))

        assertSame(newSession, replacement.player)
        assertNull(registry.find(oldSession))
        assertNotNull(registry.claim(replacement, firstBuyer))
    }

    @Test fun aBuyerMayApproachForEachDistinctOfferButCannotRepeatTheSameOffer() {
        val entry = registry.publish(seller, listOf(offer(), offer(name = "coal", ids = setOf(453))))!!
        val flax = entry.offers[0]
        val coal = entry.offers[1]
        val first = registry.claim(entry, firstBuyer, flax)!!

        assertTrue(entry.wasAttempted(firstBuyer, flax))
        assertFalse(entry.wasAttempted(firstBuyer, coal))
        assertTrue(registry.release(first))
        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertNull(registry.claim(entry, firstBuyer, flax))
        assertNull(registry.claim(entry, firstBuyer))
        val second = registry.claim(entry, firstBuyer, coal)!!

        assertTrue(entry.wasAttempted(firstBuyer, coal))
        assertTrue(registry.release(second))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertNull(registry.claim(entry, firstBuyer, coal))
    }

    @Test fun oneActiveResponseBlocksEveryOfferAndEveryOtherBuyer() {
        val entry = registry.publish(seller, listOf(offer(), offer(name = "coal", ids = setOf(453))))!!
        val flax = entry.offers[0]
        val coal = entry.offers[1]
        val response = registry.claim(entry, firstBuyer, flax)!!

        assertNull(registry.claim(entry, firstBuyer, coal))
        assertNull(registry.claim(entry, secondBuyer, flax))
        assertNull(registry.claim(entry, secondBuyer, coal))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        assertTrue(registry.availableFor(secondBuyer).isEmpty())
        assertFalse(entry.wasAttempted(firstBuyer, coal))
        assertFalse(entry.wasAttempted(secondBuyer, flax))
        assertFalse(entry.wasAttempted(secondBuyer, coal))
        assertTrue(registry.isCurrent(response))
        assertTrue(registry.release(response))
        assertNotNull(registry.claim(entry, secondBuyer, flax))
    }

    @Test fun legacyWholeAdvertisementClaimsMarkEveryOriginalOfferAttempted() {
        val entry = registry.publish(seller, listOf(offer(), offer(name = "coal", ids = setOf(453))))!!
        val response = registry.claim(entry, firstBuyer)!!

        assertTrue(entry.offers.all { entry.wasAttempted(firstBuyer, it) })
        assertTrue(registry.release(response))
        assertTrue(registry.availableFor(firstBuyer).isEmpty())
        for (advertised in entry.offers) assertNull(registry.claim(entry, firstBuyer, advertised))
        assertNotNull(registry.claim(entry, secondBuyer, entry.offers[1]))
    }

    @Test fun exhaustedOffersDoNotCreateWorkOrConsumeAnotherBuyersAttempt() {
        val entry = registry.publish(seller, listOf(offer(quantity = 100),
            offer(name = "coal", ids = setOf(453), quantity = 100)))!!
        val flax = entry.offers[0]
        val coal = entry.offers[1]
        val response = registry.claim(entry, firstBuyer, flax)!!
        assertTrue(registry.recordCompleted(response, mapOf(flax to 100)))
        assertTrue(registry.release(response))

        assertNull(registry.claim(entry, secondBuyer, flax))

        assertFalse(entry.wasAttempted(secondBuyer, flax))
        assertEquals(listOf(coal), entry.availableOffers)
        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertNotNull(registry.claim(entry, firstBuyer, coal))
    }

    @Test fun unrelatedSpecificOffersCannotClaimOrCreateResumeHistory() {
        val entry = registry.publish(seller, listOf(offer()))!!
        val unrelated = offer(name = "iron", ids = setOf(440))

        assertNull(registry.claim(entry, firstBuyer, unrelated))
        assertNull(registry.resume(entry, firstBuyer))

        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertFalse(entry.wasAttempted(firstBuyer, entry.offers.single()))
        assertThrows(IllegalArgumentException::class.java) { entry.wasAttempted(firstBuyer, unrelated) }
        assertNotNull(registry.claim(entry, firstBuyer, entry.offers.single()))
    }

    @Test fun semanticRefreshRetainsHistoryForEachOfferIndependently() {
        val lobsters = offer(name = "lobsters", ids = setOf(379))
        val coal = offer(name = "coal", ids = setOf(453))
        val entry = registry.publish(seller, listOf(lobsters, coal))!!
        val firstOffer = entry.offers[0]
        val secondOffer = entry.offers[1]
        assertTrue(registry.release(registry.claim(entry, firstBuyer, firstOffer)!!))

        assertSame(entry, registry.publish(seller, listOf(coal, lobsters.copy(itemName = "lobs"))))

        assertTrue(entry.wasAttempted(firstBuyer, firstOffer))
        assertFalse(entry.wasAttempted(firstBuyer, secondOffer))
        assertEquals(listOf(entry), registry.availableFor(firstBuyer))
        assertNull(registry.claim(entry, firstBuyer, firstOffer))
        assertNotNull(registry.claim(entry, firstBuyer, secondOffer))
    }

    @Test fun replacementStartsFreshOfferHistoryWithoutAllowingAStaleClaim() {
        val first = registry.publish(seller, listOf(offer(), offer(name = "coal", ids = setOf(453))))!!
        assertTrue(registry.release(registry.claim(first, firstBuyer, first.offers[0])!!))
        val replacement = registry.publish(seller,
            listOf(offer(coins = 20L), offer(name = "coal", ids = setOf(453))))!!

        assertNull(registry.claim(first, firstBuyer, first.offers[1]))

        assertTrue(replacement.offers.none { replacement.wasAttempted(firstBuyer, it) })
        assertEquals(listOf(replacement), registry.availableFor(firstBuyer))
        assertNotNull(registry.claim(replacement, firstBuyer, replacement.offers[0]))
    }

    @Test fun resumingADirectRequestDoesNotEraseOrExpandOfferAttemptHistory() {
        val entry = registry.publish(seller, listOf(offer(), offer(name = "coal", ids = setOf(453))))!!
        val firstOffer = entry.offers[0]
        val secondOffer = entry.offers[1]
        assertTrue(registry.release(registry.claim(entry, firstBuyer, firstOffer)!!))
        val resumed = registry.resume(entry, firstBuyer)!!

        assertTrue(entry.wasAttempted(firstBuyer, firstOffer))
        assertFalse(entry.wasAttempted(firstBuyer, secondOffer))
        assertNull(registry.claim(entry, firstBuyer, secondOffer))
        assertTrue(registry.release(resumed))

        assertTrue(entry.wasAttempted(firstBuyer, firstOffer))
        assertFalse(entry.wasAttempted(firstBuyer, secondOffer))
        assertNull(registry.claim(entry, firstBuyer, firstOffer))
        assertNotNull(registry.claim(entry, firstBuyer, secondOffer))
    }
}
