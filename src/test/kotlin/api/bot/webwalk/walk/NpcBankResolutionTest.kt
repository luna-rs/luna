package api.bot.webwalk.walk

import api.bot.script.InventoryProductionFixtures
import api.bot.webwalk.model.BankDefinition
import com.google.common.collect.ImmutableList
import engine.bank.Banking
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.item.Bank
import io.luna.game.model.mob.Npc
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.function.Predicate

/**
 * Resolves immutable bank destinations against current NPC identity and native reach on arrival.
 *
 * @author lare96
 */
class NpcBankResolutionTest {

    companion object {
        private const val BANKER_ID = 2271
        private const val BOOTH_ID = 2213

        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initialize()
            Banking.loadBankingNpcs()
        }
    }

    private val anchor = Position(2352, 3166)
    private val stand = Position(2352, 3167)
    private val destination = BankDefinition("banker", anchor, stand, npcId = BANKER_ID)
    private lateinit var world: World
    private var nearby = emptySet<Npc>()
    private var objects = emptySet<GameObject>()
    private var reachable = true
    private var addedBooth = false

    @BeforeEach fun setUp() {
        world = mock(World::class.java, RETURNS_DEEP_STUBS)
        `when`(world.locator.findViewableNpcs(anchor)).thenAnswer { nearby }
        `when`(world.locator.findObjectsOnTile(eq(anchor), any())).thenAnswer { invocation ->
            val filter = invocation.getArgument<Predicate<GameObject>>(1)
            objects.filter { filter.test(it) }.toSet()
        }
        `when`(world.collisionManager.reached(eq(stand), any(Npc::class.java), same(Banking.BANKER_POLICY)))
            .thenAnswer { reachable }
        @Suppress("UNCHECKED_CAST")
        addedBooth = (Banking.bankingObjects as MutableSet<Int>).add(BOOTH_ID)
    }

    @AfterEach fun restoreRegistry() {
        if (addedBooth) (Banking.bankingObjects as MutableSet<Int>).remove(BOOTH_ID)
    }

    private fun banker(id: Int = BANKER_ID, base: Position = anchor, index: Int = 7): Npc =
        mock(Npc::class.java, RETURNS_DEEP_STUBS).also { npc ->
            `when`(npc.id).thenReturn(id)
            `when`(npc.basePosition).thenReturn(base)
            `when`(npc.position).thenReturn(base)
            `when`(npc.index).thenReturn(index)
            `when`(npc.world).thenReturn(world)
            `when`(npc.state).thenReturn(EntityState.ACTIVE)
            `when`(npc.isAlive).thenReturn(true)
            `when`(npc.def().actions).thenReturn(ImmutableList.of("Talk-to", "Bank"))
            `when`(world.npcs[index]).thenReturn(npc)
        }

    @Test fun resolvesTheExactActiveBankerAtItsSpawnAnchor() {
        val npc = banker()
        nearby = setOf(npc)

        assertSame(npc, BankWithdrawal.resolve(destination, world))
    }

    @Test fun aReachableSmallMoveDoesNotLoseTheBankersSpawnIdentity() {
        val npc = banker()
        `when`(npc.position).thenReturn(anchor.translate(1, 0))
        nearby = setOf(npc)

        assertSame(npc, BankWithdrawal.resolve(destination, world))
    }

    @Test fun aMoveBeyondNativeReachMakesThisAttemptUnavailable() {
        nearby = setOf(banker())
        reachable = false

        assertNull(BankWithdrawal.resolve(destination, world))
    }

    @ParameterizedTest @ValueSource(strings = ["missing", "wrong_id", "wrong_spawn", "inactive", "dead", "unregistered", "no_action", "replaced"])
    fun staleAndUnrelatedNpcsAreNotBankDestinations(reason: String) {
        val npc = banker()
        nearby = setOf(npc)
        when (reason) {
            "missing" -> nearby = emptySet()
            "wrong_id" -> `when`(npc.id).thenReturn(BANKER_ID + 1)
            "wrong_spawn" -> `when`(npc.basePosition).thenReturn(anchor.translate(8, 0))
            "inactive" -> `when`(npc.state).thenReturn(EntityState.INACTIVE)
            "dead" -> `when`(npc.isAlive).thenReturn(false)
            "unregistered" -> {
                `when`(npc.id).thenReturn(Int.MAX_VALUE)
                assertNull(BankWithdrawal.resolve(destination.copy(npcId = Int.MAX_VALUE), world))
                return
            }
            "no_action" -> `when`(npc.def().actions).thenReturn(ImmutableList.of("Talk-to"))
            "replaced" -> `when`(world.npcs[npc.index]).thenReturn(mock(Npc::class.java))
        }

        assertNull(BankWithdrawal.resolve(destination, world))
    }

    @Test fun aMissingNpcDestinationDoesNotSilentlyBecomeAnObjectBank() {
        val booth = mock(GameObject::class.java)
        `when`(booth.id).thenReturn(BOOTH_ID)
        objects = setOf(booth)

        assertNull(BankWithdrawal.resolve(destination, world))
    }

    @Test fun legacyBoothsStillResolveThroughTheRegisteredObjectFilter() {
        val unrelated = mock(GameObject::class.java)
        `when`(unrelated.id).thenReturn(Int.MAX_VALUE)
        val booth = mock(GameObject::class.java)
        `when`(booth.id).thenReturn(BOOTH_ID)
        objects = linkedSetOf(unrelated, booth)

        assertSame(booth, BankWithdrawal.resolve(destination.copy(npcId = null), world))
    }

    @Test fun tripSupplyDetoursOpenTheResolvedNpcAndRequireTheActualWindow() = runBlocking<Unit> {
        val npc = banker()
        nearby = setOf(npc)
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.world).thenReturn(world)
        val bank = mock(Bank::class.java)
        `when`(bot.bank).thenReturn(bank)
        val handler = bot.actionHandler
        `when`(handler.banking.open(npc)).thenReturn(true)

        assertFalse(BankWithdrawal.openBank(bot, handler, destination))
        `when`(bank.isOpen).thenReturn(true)
        assertTrue(BankWithdrawal.openBank(bot, handler, destination))
        verify(handler.banking, times(2)).open(npc)
    }

    @Test fun aMissingBankerCannotOpenABankForATripSupplyDetour() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.world).thenReturn(world)
        val handler = bot.actionHandler
        val banking = handler.banking

        assertFalse(BankWithdrawal.openBank(bot, handler, destination))
        verifyNoInteractions(banking)
    }
}
