package api.bot.script

import api.bot.action.trading.BotMarketplace
import api.bot.script.BotScript.ActionSafety
import api.bot.zone.SubZone
import engine.bot.gear.BotGearLocator
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.EnumSet
import kotlin.time.Duration.Companion.minutes

/**
 * Covers safety changes inside suspendable initialization, marketplace travel and recurring banking.
 *
 * @author lare96
 */
class ZonedActionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }
    private lateinit var bot: Bot
    private var position = Position(2728, 3494)

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        doReturn(EnumSet.of(SubZone.SEERS_VILLAGE_MARKETPLACE)).`when`(bot).subZones
        `when`(bot.position).thenAnswer { position }
        `when`(bot.abs()).thenAnswer { position }
    }

    /**
     * Exercises the shared final lifecycle methods with observable activity and banking hooks.
     *
     * @author lare96
     */
    private class Activity(bot: Bot, override val actionSafety: ActionSafety = ActionSafety.NON_COMBAT) :
        ZonedBotScript(bot, 8.minutes, mutableListOf(SubZone.SEERS_VILLAGE_MARKETPLACE)) {
        var setups = 0
        var bankVisits = 0
        var executions = 0
        var initialBank = false
        init { depositInventoryWhenBanking = false }
        fun bank(bank: GameObject) { forceBanking = true; cachedBank = bank }
        fun bankingPending() = forceBanking
        override suspend fun onInit(resumed: Boolean): Boolean { setups++; return true }
        override suspend fun equipment(): BotGearLocator? = null
        override suspend fun onBankRequested(initial: Boolean) = !initial || initialBank
        override suspend fun onBankOpen(initial: Boolean) { bankVisits++ }
        override suspend fun executeInZone(): Boolean { executions++; return true }
        override fun snapshot(): BotScriptData? = null
    }

    private fun setZone(script: Activity) {
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
            isAccessible = true
            set(script, SubZone.SEERS_VILLAGE_MARKETPLACE)
        }
    }

    @Test fun resumedInitializationWaitsBeforeCallingSetup() = runBlocking<Unit> {
        val script = Activity(bot)
        `when`(bot.isLocked).thenReturn(true)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { script.init(true) }
        assertEquals(0, script.setups)
        `when`(bot.isLocked).thenReturn(false)
        assertFalse(withTimeout(4_000) { pending.await() })
        assertEquals(1, script.setups)
    }

    @Test fun initialBankHookWaitsForInterruptionAfterTravelToSettle() = runBlocking<Unit> {
        val script = Activity(bot).also { it.initialBank = true }
        val banking = bot.actionHandler.banking
        `when`(banking.travelToBankDepositAll()).thenAnswer {
            `when`(bot.isLocked).thenReturn(true)
            true
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { script.init(false) }
        assertFalse(pending.isCompleted)
        assertEquals(0, script.bankVisits)
        `when`(bot.isLocked).thenReturn(false)
        assertFalse(withTimeout(4_000) { pending.await() })
        assertEquals(1, script.bankVisits)
    }

    @ParameterizedTest @ValueSource(booleans = [true, false])
    fun marketplaceTravelRechecksSafetyBeforeExecutingProduction(travelSucceeded: Boolean) = runBlocking<Unit> {
        val script = Activity(bot)
        assertFalse(script.init(true))
        setZone(script)
        val handler = bot.actionHandler
        var firstWalk = true
        `when`(handler.webWalkTo(any(Position::class.java) ?: Position(0, 0), eq(0), eq(1))).thenAnswer {
            if (firstWalk) {
                firstWalk = false
                if (travelSucceeded) position = it.getArgument(0)
                `when`(bot.isLocked).thenReturn(true)
                travelSucceeded
            } else {
                position = it.getArgument(0)
                true
            }
        }
        val pending = async(start = CoroutineStart.UNDISPATCHED) { script.run() }
        assertFalse(pending.isCompleted)
        assertEquals(0, script.executions)
        `when`(bot.isLocked).thenReturn(false)
        assertFalse(withTimeout(5_000) { pending.await() })
        assertEquals(0, script.executions)
        assertFalse(script.run())
        assertEquals(1, script.executions)
        assertTrue(BotMarketplace.SEERS_VILLAGE.contains(position))
        assertEquals(1, script.zones.size)
    }

    @Test fun combatActivityDoesNotPerformBankHooksWhileStillFighting() = runBlocking<Unit> {
        val script = Activity(bot, ActionSafety.COMBAT)
        assertFalse(script.init(true))
        setZone(script)
        val bank = spy(bot.bank)
        doReturn(true).`when`(bank).isOpen
        `when`(bot.bank).thenReturn(bank)
        script.bank(mock(GameObject::class.java))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(script.run())
        assertEquals(0, script.bankVisits)
        assertTrue(script.bankingPending())
        `when`(bot.combat.inCombat()).thenReturn(false)
        assertFalse(script.run())
        assertEquals(1, script.bankVisits)
        assertFalse(script.bankingPending())
    }
}
