package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.MagicScriptFactory
import io.luna.game.model.mob.bot.script.BotScriptManager
import game.bot.scripts.skills.ChargeOrbBotScript.Companion.ChargeOrbData
import game.skill.magic.*
import game.skill.magic.chargeOrb.ChargeOrbAction
import game.skill.magic.chargeOrb.ChargeOrbType
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

class ChargeOrbBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            check(SubZone.HOME.area.contains(SubZone.HOME.inside))
        }
    }
    private lateinit var luna: MockedStatic<Luna>
    private lateinit var settings: LunaSettings
    @BeforeEach fun normalMode() {
        settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }
    @AfterEach fun restoreMode() { luna.close() }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.magic.staticLevel).thenReturn(99)
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        val inventory = spy(it.inventory)
        for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
            doReturn(true).`when`(inventory).stackable(id)
        }
        `when`(it.inventory).thenReturn(inventory)
    }

    // Fire uses an explicit fixture zone until its production parent is confirmed.
    private fun zone(type: ChargeOrbType) = when (type) {
        ChargeOrbType.WATER -> SubZone.WATER_OBELISK
        ChargeOrbType.AIR -> SubZone.AIR_OBELISK
        ChargeOrbType.EARTH -> SubZone.EARTH_OBELISK
        ChargeOrbType.FIRE -> SubZone.HOME
    }
    private fun script(bot: Bot, type: ChargeOrbType) =
        InventoryProductionFixtures.active(ChargeOrbBotScript(bot, type, 10.minutes, mutableListOf(zone(type))))
    private fun supplies(bot: Bot, type: ChargeOrbType, amount: Int) {
        InventoryProductionFixtures.bank(bot, Item(567, amount), Item(Rune.valueOf(type.name).id, amount * 30),
            Item(Rune.COSMIC.id, amount * 3))
    }

    @Test fun stackableRuneBatchesReserveSlotsAndRespectPartialSuppliesForAllSpells() {
        for (type in ChargeOrbType.entries) {
            val bot = bot()
            val script = script(bot, type)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            supplies(bot, type, 100)
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(567, 26), Item(Rune.valueOf(type.name).id, 780), Item(Rune.COSMIC.id, 78)),
                script.bankBatch())
            bot.bank.remove(Item(Rune.COSMIC.id, 294))
            assertEquals(listOf(Item(567, 2), Item(Rune.valueOf(type.name).id, 60), Item(Rune.COSMIC.id, 6)),
                script.bankBatch())
        }
    }

    @Test fun equippedStaffAndCombinationRunesUseTheCorrectBankFootprint() {
        for (type in ChargeOrbType.entries) {
            val bot = bot()
            val element = Rune.valueOf(type.name)
            InventoryProductionFixtures.bank(bot, Item(567, 100), Item(Rune.COSMIC.id, 300))
            val staff = Staff.entries.first { it.represents == setOf(element) }.ids.first()
            `when`(bot.equipment.weapon).thenReturn(Item(staff))
            val script = script(bot, type)
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(567, 27), Item(Rune.COSMIC.id, 81)), script.bankBatch())
            `when`(bot.equipment.weapon).thenReturn(null)
            assertFalse(script.isEligible())
            val combined = CombinationRune.entries.first { element in it.represents }
            InventoryProductionFixtures.bank(bot, Item(combined.id, 900))
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(567, 26), Item(combined.id, 780), Item(Rune.COSMIC.id, 78)), script.bankBatch())
        }
    }

    @Test fun regularSpellbookPermanentLevelAndOwnedInputsControlEligibility() = runBlocking<Unit> {
        for (type in ChargeOrbType.entries) {
            val bot = bot()
            supplies(bot, type, 1)
            val script = script(bot, type)
            assertTrue(script.isEligible())
            `when`(bot.magic.staticLevel).thenReturn(type.level - 1)
            assertFalse(script.isEligible())
            `when`(bot.magic.staticLevel).thenReturn(99)
            `when`(bot.spellbook).thenReturn(Spellbook.ANCIENT)
            assertFalse(script.isEligible())
            assertFalse(script.onInit(false))
            verify(bot.preferences, never()).raiseWantedItemTarget(anyInt(), anyInt())
        }
    }

    @Test fun missingCostsRequestRealisticTargetsAndCurrentLevelLossStopsBeforeCasting() = runBlocking<Unit> {
        for (type in ChargeOrbType.entries) {
            val bot = bot()
            InventoryProductionFixtures.bank(bot, Item(567))
            val script = script(bot, type)
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(Rune.valueOf(type.name).id, 1_000)
            verify(bot.preferences).raiseWantedItemTarget(Rune.COSMIC.id, 1_000)
            val lowered = script(bot, type)
            InventoryProductionFixtures.inventory(bot, Item(567), Item(Rune.valueOf(type.name).id, 30), Item(Rune.COSMIC.id, 3))
            `when`(bot.magic.level).thenReturn(type.level - 1)
            assertTrue(lowered.executeInZone())
            assertTrue(lowered.isTerminated())
            verify(bot.botClient.output, never()).useSpellOnObject(anyInt(), any())
        }
    }

    @Test fun eachObeliskUsesTheNormalSpellActionAndTracksInputOrBetaOutputProgress() = runBlocking<Unit> {
        val fixtureWorld = world
        val locator = fixtureWorld.locator
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (beta in listOf(false, true)) for (type in ChargeOrbType.entries) {
                `when`(settings.game().betaMode()).thenReturn(beta)
                val bot = bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val zone = zone(type)
                val obelisk = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(obelisk.id).thenReturn(type.objectId)
                `when`(obelisk.position).thenReturn(zone.inside)
                val wrong = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(wrong.id).thenReturn(type.objectId + 100)
                `when`(wrong.position).thenReturn(zone.inside)
                val center = zone.area.centerPosition
                val radius = zone.area.tileRadius
                doAnswer { invocation ->
                    val predicate = invocation.getArgument<Predicate<GameObject>>(2)
                    assertTrue(predicate.test(obelisk))
                    assertFalse(predicate.test(wrong))
                    setOf(obelisk)
                }.`when`(locator).findObjects(eq(center), eq(radius), any())
                InventoryProductionFixtures.inventory(bot, Item(567, 2), Item(Rune.valueOf(type.name).id, 60), Item(Rune.COSMIC.id, 6))
                val output = bot.botClient.output
                doAnswer { ChargeOrbAction(bot, type).execute(); true }
                    .`when`(output).useSpellOnObject(type.spellId, obelisk)
                val data = script(bot, type).snapshot().also { it.failures = 2 }
                val script = InventoryProductionFixtures.active(ChargeOrbBotScript(bot, data))
                ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, zone) }
                assertTrue(script.executeInZone())
                verify(output).useSpellOnObject(type.spellId, obelisk)
                assertEquals(if (beta) 2 else 1, bot.inventory.computeAmountForId(567))
                assertEquals(1, bot.inventory.computeAmountForId(type.chargedOrb))
                assertEquals(0, script.snapshot().failures)
                verify(bot.magic).addExperience(type.xp)
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            doReturn(emptySet<GameObject>()).`when`(locator).findObjects(any(Locatable::class.java), anyInt(), any())
        }
    }

    @Test fun configuredOrbsAreSelectedInBothFactoryModesAndRespectCurrentLevel() {
        for (type in listOf(ChargeOrbType.WATER, ChargeOrbType.EARTH, ChargeOrbType.AIR)) {
            for (training in listOf(false, true)) {
                val bot = bot()
                supplies(bot, type, 1)
                val selected = MagicScriptFactory.getScript(bot, type.level, mutableListOf(), training)
                assertInstanceOf(ChargeOrbBotScript::class.java, selected)
                selected as ChargeOrbBotScript
                assertEquals(type, selected.type)
                assertEquals(listOf(zone(type)), selected.zones)
                assertNull(MagicScriptFactory.getOrbScript(bot, type.level - 1))
                `when`(bot.spellbook).thenReturn(Spellbook.ANCIENT)
                assertNull(MagicScriptFactory.getOrbScript(bot, 99))
            }
        }
        val bot = bot()
        `when`(bot.personality.isIntelligent).thenReturn(true)
        for (type in ChargeOrbType.entries) supplies(bot, type, 1)
        assertEquals(ChargeOrbType.AIR, MagicScriptFactory.getOrbScript(bot, 99)?.type)
        bot.bank.clear()
        assertNull(MagicScriptFactory.getOrbScript(bot, 99))
        assertInstanceOf(SplashBotScript::class.java,
            MagicScriptFactory.getTrainingScript(bot, 99, mutableListOf()))
    }

    @Test fun centralRegistryRestoresEverySpellWithItsSessionAndBudgets() = runBlocking<Unit> {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            for (type in ChargeOrbType.entries) {
                val bot = bot()
                val data = script(bot, type).snapshot().also { it.failures = 3; it.bankFailures = 2 }
                val json = JsonObject().also { data.save(it) }
                val loaded = ChargeOrbData().also { it.load(json) }
                val restored = registry.loadScript(ChargeOrbBotScript::class.qualifiedName, bot, loaded)
                assertInstanceOf(ChargeOrbBotScript::class.java, restored)
                restored as ChargeOrbBotScript
                assertEquals(type, restored.type)
                assertEquals(listOf(zone(type)), restored.zones)
                assertEquals(10.minutes, restored.duration)
                assertEquals(2, restored.snapshot().bankFailures)
                InventoryProductionFixtures.active(restored)
                assertFalse(restored.onInit(true))
                assertTrue(restored.isTerminated())
            }
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }
    @Test fun betaBatchesReserveOutputSpaceAndSnapshotsKeepSpellAndBudgets() = runBlocking<Unit> {
        `when`(settings.game().betaMode()).thenReturn(true)
        for (type in ChargeOrbType.entries) {
            val bot = bot()
            InventoryProductionFixtures.bank(bot, Item(567, 100))
            val script = script(bot, type)
            assertEquals(listOf(Item(567, 14)), script.bankBatch())
            InventoryProductionFixtures.inventory(bot, Item(567, 14), Item(type.chargedOrb, 14))
            assertTrue(script.onBankRequested(false))
            val data = script.snapshot().also { it.failures = 3; it.bankFailures = 2 }
            val json = JsonObject().also { data.save(it) }
            val loaded = ChargeOrbData().also { it.load(json) }
            val restored = InventoryProductionFixtures.active(ChargeOrbBotScript(bot, loaded))
            assertEquals(type, restored.type)
            assertEquals(script.zones, restored.zones)
            assertEquals(10.minutes, restored.duration)
            assertEquals(2, restored.snapshot().bankFailures)
            assertFalse(restored.onInit(true))
            assertTrue(restored.isTerminated())
        }
    }
}
