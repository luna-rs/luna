package game.bot.scripts

import api.bot.action.BotBankingActionHandler
import api.bot.script.InventoryProductionFixtures
import api.bot.script.AbstractBotScript
import api.predef.*
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.combat.CombatBotScript
import game.skill.magic.Rune
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotReflex
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.model.mob.combat.CombatSpell
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import api.bot.zone.SubZone
import kotlin.time.Duration.Companion.minutes

class NpcCombatPreparationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            NpcCombatStylesTest.fixtures()
        }
    }

    private lateinit var luna: MockedStatic<Luna>
    @BeforeEach fun normalMode() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }
    @AfterEach fun restoreMode() = luna.close()

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.reflex).thenReturn(BotReflex())
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        for (id in 0..20) `when`(it.skill(id).level).thenReturn(99)
        it.itemTracker.add(1381)
        it.itemTracker.add(Rune.MIND.id, 10)
    }

    private fun script(bot: Bot, purpose: BotGearPurpose = BotGearPurpose.MAGIC) =
        InventoryProductionFixtures.active(NpcCombatScript(bot, 10.minutes,
            mutableListOf(SubZone.LUMBRIDGE_COW_PEN), purpose = purpose, spell = CombatSpell.WIND_STRIKE))

    private fun select(script: NpcCombatScript, weapon: Int = 1381, ammo: Int? = null) {
        val gear = arrayOfNulls<Int>(14).also {
            it[Equipment.WEAPON] = weapon
            it[Equipment.AMMUNITION] = ammo
        }
        val selected = NpcCombatLoadout(gear, if (script.purpose == BotGearPurpose.MAGIC)
            CombatSpell.WIND_STRIKE.def else null)
        NpcCombatScript::class.java.getDeclaredField("loadout").apply {
            isAccessible = true
            set(script, selected)
        }
    }

    @Test fun freshInitializationLeavesAutomaticCombatResponseAvailable() = runBlocking<Unit> {
        val bot = bot()
        assertTrue(script(bot).onInit(false))
        assertFalse(bot.reflex.isDisableCombatReflex)
    }

    @Test fun attackDuringBankPreparationCanTriggerTheActualCombatReflex() = runBlocking<Unit> {
        val bot = bot()
        val script = script(bot)
        assertTrue(script.onInit(false))
        val threat = mock(Npc::class.java)
        `when`(bot.combat.inCombat()).thenReturn(true)
        `when`(bot.combat.target).thenReturn(threat)
        val stack = BotScriptStack(bot, BotScriptManager())
        `when`(bot.scriptStack).thenReturn(stack)
        stack.push(script)
        `when`(bot.emotions.getCombatResponse(threat)).thenReturn(CombatBotScript.InitialState.ATTACK)

        // The real bank handler is still forbidden to click, even when its interface is already open.
        val bank = spy(bot.bank)
        doReturn(true).`when`(bank).isOpen
        `when`(bot.bank).thenReturn(bank)
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BotBankingActionHandler(bot, bot.actionHandler).travelToBankOpen()
        }
        AbstractBotScript::class.java.getDeclaredField("progress").apply {
            isAccessible = true
            set(script, pending)
        }
        try {
            assertFalse(pending.isCompleted)
            assertFalse(bot.reflex.process(bot))
            assertTrue(stack.current() is CombatBotScript)
            assertTrue(pending.isCancelled)
        } finally {
            pending.cancelAndJoin()
        }
        verifyNoInteractions(bot.output)
    }

    @Test fun bankingRelinquishesCombatOwnershipBeforeClearingTheSpell() = runBlocking<Unit> {
        val bot = bot()
        bot.reflex.isDisableCombatReflex = true
        assertTrue(script(bot).onBankRequestedTargeting(false))
        assertFalse(bot.reflex.isDisableCombatReflex)
        verify(bot.combat.magic).setAutocasting(false)
    }

    @Test fun missingRunesRelinquishCombatOwnership() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        bot.reflex.isDisableCombatReflex = true
        val script = script(bot).also { select(it) }
        assertFalse(script.prepareCombat())
        assertFalse(bot.reflex.isDisableCombatReflex)
        verify(bot.combat.magic).setAutocasting(false)
    }

    @Test fun emptyQuiverRelinquishesCombatOwnership() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        bot.reflex.isDisableCombatReflex = true
        val script = script(bot, BotGearPurpose.RANGED).also { select(it, 841, 882) }
        assertFalse(script.prepareCombat())
        assertFalse(bot.reflex.isDisableCombatReflex)
    }

    @Test fun readyMagicEngagementTakesCombatOwnership() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        bot.inventory.add(Item(Rune.MIND.id))
        `when`(bot.actionHandler.combat.setAutocastSpell(CombatSpell.WIND_STRIKE.def)).thenReturn(true)
        val script = script(bot).also { select(it) }
        assertTrue(script.prepareCombat())
        assertTrue(bot.reflex.isDisableCombatReflex)
    }

    @Test fun successfulBankHookKeepsReflexAvailableUntilNextEngagement() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        `when`(bot.actionHandler.combat.setAutocastSpell(CombatSpell.WIND_STRIKE.def)).thenReturn(true)
        val banking = bot.actionHandler.banking
        bot.bank.add(Item(Rune.MIND.id, 2))
        `when`(banking.withdraw(Item(Rune.MIND.id, 500))).thenAnswer {
            assertFalse(bot.reflex.isDisableCombatReflex)
            check(bot.bank.remove(Item(Rune.MIND.id, 2)))
            check(bot.inventory.add(Item(Rune.MIND.id, 2)))
            true
        }
        `when`(banking.withdrawAnyFood(amount = 8, minimumHeal = 0, retry = true)).thenAnswer {
            assertFalse(bot.reflex.isDisableCombatReflex)
            false
        }
        val script = script(bot).also { select(it) }
        script.onBankOpen(false)
        assertFalse(bot.reflex.isDisableCombatReflex)
        assertEquals(2, bot.inventory.computeAmountForId(Rune.MIND.id))
        assertEquals(0, bot.bank.computeAmountForId(Rune.MIND.id))
        assertTrue(script.prepareCombat())
        assertTrue(bot.reflex.isDisableCombatReflex)
    }

    @Test fun resumeValidationDoesNotTakeOwnershipWhileStillInitializing() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        bot.inventory.add(Item(Rune.MIND.id))
        `when`(bot.actionHandler.combat.setAutocastSpell(CombatSpell.WIND_STRIKE.def)).thenReturn(true)
        assertTrue(script(bot).onInit(true))
        assertFalse(bot.reflex.isDisableCombatReflex)
    }
}
