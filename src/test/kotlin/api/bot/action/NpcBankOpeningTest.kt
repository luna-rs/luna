package api.bot.action

import api.bot.script.InventoryProductionFixtures
import com.google.common.collect.ImmutableList
import engine.bank.Banking
import io.luna.game.model.EntityState
import io.luna.game.model.item.Bank
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.task.Task
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Keeps NPC bank opening on the native interaction path through movement, disappearance and interface timeouts.
 *
 * @author lare96
 */
class NpcBankOpeningTest {

    companion object {
        private const val NPC_INDEX = 1734
        private val runTask = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }

        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initialize()
            Banking.loadBankingNpcs()
        }
    }

    private lateinit var bot: Bot
    private lateinit var banker: Npc
    private lateinit var bank: Bank
    private var previous: Npc? = null
    private var nativeWindow = false
    private var reached = true

    private fun fixture(): Bot {
        bot = InventoryProductionFixtures.bot()
        val world = bot.world
        val npcs = world.npcs
        previous = npcs[NPC_INDEX]
        banker = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(banker.id).thenReturn(2271)
        `when`(banker.def().actions).thenReturn(ImmutableList.of("Talk-to", "Bank"))
        `when`(banker.index).thenReturn(NPC_INDEX)
        `when`(banker.world).thenReturn(world)
        `when`(banker.state).thenReturn(EntityState.ACTIVE)
        `when`(banker.isAlive).thenReturn(true)
        `when`(npcs[NPC_INDEX]).thenReturn(banker)
        `when`(world.collisionManager.reached(bot, banker, Banking.BANKER_POLICY)).thenAnswer { reached }
        bank = mock(Bank::class.java)
        `when`(bank.isOpen).thenAnswer { nativeWindow }
        `when`(bot.bank).thenReturn(bank)
        doAnswer { invocation ->
            val task = invocation.getArgument<Task>(0)
            repeat(10) { runTask.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))
        return bot
    }

    @AfterEach fun restoreWorld() {
        if (::bot.isInitialized) {
            val world = bot.world
            `when`(world.npcs[NPC_INDEX]).thenReturn(previous)
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }

    @Test fun registeredBankerUsesSecondClickAndRequiresTheNativeWindow() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(2, banker)).thenAnswer {
            nativeWindow = true
            true
        }

        assertTrue(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(handler.interactions).interact(2, banker)
        verify(handler.interactions, never()).interact(3, banker)
        verify(bank, never()).open()
    }

    @Test fun bankClicksUseNativeTellerRangeWhileAttacksKeepTheirOwnPolicy() {
        fixture()
        val interactions = BotInteractionActionHandler(bot, bot.actionHandler)
        `when`(bot.combat.getDefaultAttack(banker)).thenReturn(null)

        assertSame(Banking.BANKER_POLICY, interactions.interactionPolicy(1, banker))
        assertSame(Banking.BANKER_POLICY, interactions.interactionPolicy(2, banker))
        assertSame(InteractionPolicy.STANDARD_SIZE, interactions.interactionPolicy(3, banker))
    }

    @Test fun unrelatedNpcClicksRetainStandardAdjacentReach() {
        fixture()
        val interactions = BotInteractionActionHandler(bot, bot.actionHandler)
        val other = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(other.id).thenReturn(Int.MAX_VALUE)

        assertSame(InteractionPolicy.STANDARD_SIZE, interactions.interactionPolicy(2, other))
    }

    @Test fun aNativeInterfaceTimeoutCannotForceOpenAnNpcBank() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(2, banker)).thenReturn(true)

        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(bank, never()).open()
    }

    @Test fun failedNavigationCannotOpenTheNpcBank() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(2, banker)).thenReturn(false)

        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(bank, never()).open()
    }

    @Test fun aBankerMovingOutOfReachDuringInteractionFailsCleanly() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(2, banker)).thenAnswer {
            reached = false
            true
        }

        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(bank, never()).open()
    }

    @Test fun aReusedNpcIndexCannotAuthorizeTheOldBanker() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        val registry = bot.world.npcs
        `when`(handler.interactions.interact(2, banker)).thenAnswer {
            `when`(registry[NPC_INDEX]).thenReturn(mock(Npc::class.java))
            true
        }

        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(bank, never()).open()
    }

    @Test fun aBankerDisappearingDuringTheWindowWaitFailsCleanly() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        val world = bot.world
        `when`(handler.interactions.interact(2, banker)).thenReturn(true)
        doAnswer { invocation ->
            `when`(banker.state).thenReturn(EntityState.INACTIVE)
            val task = invocation.getArgument<Task>(0)
            repeat(10) { runTask.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))

        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(banker) })

        verify(bank, never()).open()
    }

    @Test fun aBankerRemovedDuringReactionDelayIsNeverClicked() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BotBankingActionHandler(bot, handler).open(banker)
        }
        `when`(banker.state).thenReturn(EntityState.INACTIVE)

        assertFalse(withTimeout(5_000) { pending.await() })

        verifyNoInteractions(handler.interactions)
        verify(bank, never()).open()
    }

    @Test fun cancellationDuringReactionDelayDoesNotClickOrOpenTheBank() = runBlocking<Unit> {
        fixture()
        val handler = bot.actionHandler
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BotBankingActionHandler(bot, handler).open(banker)
        }

        pending.cancelAndJoin()

        assertTrue(pending.isCancelled)
        verifyNoInteractions(handler.interactions)
        verify(bank, never()).open()
    }
}
