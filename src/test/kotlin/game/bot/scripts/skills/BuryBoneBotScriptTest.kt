package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.PrayerScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator
import game.bot.scripts.skills.BuryBoneBotScript.Companion.BoneData
import game.skill.prayer.Bone
import game.skill.prayer.buryBone.BuryBoneAction
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

@Timeout(90)
class BuryBoneBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }
    private fun bot() = InventoryProductionFixtures.bot().also {
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.personality.isDextrous).thenReturn(true)
    }
    private fun script(bot: Bot, bone: Bone = Bone.BONES) =
        InventoryProductionFixtures.active(BuryBoneBotScript(bot, bone, 10.minutes))

    @Test fun everyBoneSupportsPartialBatchesAndPrayerIsRegisteredInBothCoordinatorModes() {
        for (bone in Bone.entries) {
            val bot = bot()
            val script = script(bot, bone)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            InventoryProductionFixtures.bank(bot, Item(bone.id, 3))
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(bone.id, 3)), script.bankBatch())
            assertEquals(bone, PrayerScriptFactory.getProductionScript(bot)!!.bone)
            InventoryProductionFixtures.bank(bot, Item(bone.id, 30))
            assertEquals(listOf(Item(bone.id, 28)), script.bankBatch())
        }
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Bone.BONES.boneItem, Bone.DRAGON_BONES.boneItem)
        assertEquals(Bone.DRAGON_BONES, PrayerScriptFactory.getProductionScript(bot)!!.bone)
        val field = SkillingCoordinator::class.java.getDeclaredField("factories").apply { isAccessible = true }
        assertTrue((field.get(SkillingCoordinator(true)) as Map<*, *>).containsKey(SKILL_PRAYER))
        assertTrue((field.get(SkillingCoordinator(false)) as Map<*, *>).containsKey(SKILL_PRAYER))
    }

    @Test fun firstInventoryClicksRunRealPlayerBurialForEveryBoneAndFullInventories() = runBlocking<Unit> {
        val fixtureWorld = world
        var tick = 0L
        `when`(fixtureWorld.currentTick).thenAnswer { tick }
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (bone in Bone.entries) {
                val bot = bot()
                val amount = if (bone == Bone.BONES) 28 else 1
                InventoryProductionFixtures.inventory(bot, Item(bone.id, amount))
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val inventoryHandler = bot.actionHandler.inventory
                doAnswer {
                    tick += BuryBoneAction.DELAY
                    bot.actions.submit(BuryBoneAction(bot, bone))
                    repeat(3) { bot.actions.process() }
                    true
                }.`when`(inventoryHandler).clickItem(1, bone.id, -1)
                val script = script(bot, bone)
                if (amount == 28) assertTrue(bot.inventory.isFull)
                assertFalse(script.onBankRequested(false))
                assertTrue(script.executeInZone())
                assertEquals(amount - 1, bot.inventory.computeAmountForId(bone.id))
                verify(bot.prayer).addExperience(bone.exp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.currentTick).thenReturn(0L)
        }
    }

    @Test fun cooldownChecksDoNotResetTheClockOrConsumeNativeFailureBudget() = runBlocking<Unit> {
        val bot = bot()
        var tick = 100L
        val fixtureWorld = world
        `when`(fixtureWorld.currentTick).thenAnswer { tick }
        try {
            InventoryProductionFixtures.inventory(bot, Item(Bone.BONES.id, 2))
            bot.actions.submit(BuryBoneAction(bot, Bone.BONES))
            repeat(3) { bot.actions.process() }
            assertFalse(BuryBoneAction.isReady(bot))
            val script = script(bot)
            repeat(3) { script.executeInZone() }
            assertEquals(0, script.snapshot().failures)
            assertFalse(script.isTerminated())
            verify(bot.actionHandler.inventory, never()).clickItem(1, Bone.BONES.id, -1)
            tick += 2
            assertFalse(BuryBoneAction.isReady(bot))
            tick++
            assertTrue(BuryBoneAction.isReady(bot))
            assertTrue(BuryBoneAction.isReady(bot))
        } finally { `when`(fixtureWorld.currentTick).thenReturn(0L) }
    }

    @Test fun queuedBurialCannotAwardExperienceForMissingBonesDeadPlayersOrFailedRemoval() {
        for (state in 0..3) {
            val bot = bot()
            InventoryProductionFixtures.inventory(bot, Bone.DRAGON_BONES.boneItem)
            val action = BuryBoneAction(bot, Bone.DRAGON_BONES)
            bot.actions.submit(action)
            when (state) {
                0 -> bot.inventory.remove(Bone.DRAGON_BONES.boneItem)
                1 -> `when`(bot.health).thenReturn(0)
                2 -> `when`(bot.state).thenReturn(EntityState.NEW)
                3 -> {
                    val inventory = spy(bot.inventory)
                    doReturn(false).`when`(inventory).remove(Bone.DRAGON_BONES.boneItem)
                    `when`(bot.inventory).thenReturn(inventory)
                }
            }
            repeat(3) { bot.actions.process() }
            verify(bot.prayer, never()).addExperience(anyDouble())
            verify(bot, never()).sendMessage("You bury the bones.")
            if (state > 0) assertTrue(bot.inventory.contains(Bone.DRAGON_BONES.id))
        }
    }

    @Test fun missingStartupStockRequestsBulkTargetsAndUnsafeStatesDeferClicks() = runBlocking<Unit> {
        val missing = bot()
        assertFalse(script(missing).onInit(false))
        verify(missing.preferences).raiseWantedItemTarget(Bone.BONES.id, 1_000)
        for (state in 0..2) {
            val bot = bot()
            InventoryProductionFixtures.inventory(bot, Bone.BONES.boneItem)
            when (state) {
                0 -> `when`(bot.health).thenReturn(0)
                1 -> `when`(bot.isLocked).thenReturn(true)
                2 -> `when`(bot.combat.inCombat()).thenReturn(true)
            }
            assertTrue(script(bot).executeInZone())
            verify(bot.actionHandler.inventory, never()).clickItem(1, Bone.BONES.id, -1)
            assertTrue(bot.inventory.contains(Bone.BONES.id))
        }
    }

    @Test fun rejectedClicksExhaustPersistentBudgetAndActualRegistryRestoresBoneRecipe() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Bone.BAT_BONES.boneItem)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(bot.actionHandler.inventory.clickItem(1, Bone.BAT_BONES.id, -1)).thenReturn(false)
        val script = script(bot, Bone.BAT_BONES)
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        assertEquals(3, script.snapshot().failures)
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = BoneData().apply { load(JsonObject().also { script.snapshot().save(it) }) }
            val restored = registry.loadScript(BuryBoneBotScript::class.qualifiedName, bot, data) as BuryBoneBotScript
            assertEquals(Bone.BAT_BONES, restored.bone)
            assertEquals(data.duration, restored.snapshot().duration)
            assertEquals(data.zones, restored.snapshot().zones)
            assertFalse(InventoryProductionFixtures.active(restored).onInit(true))
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }

    @Test fun bankingVerifiesUnnotedBonesAndRetainsIndependentRetryBudget() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Bone.BONES.id, 3))
        val script = script(bot)
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(BuryBoneBotScript(bot, script.snapshot())).onInit(true))
        val requested = listOf(Item(Bone.BONES.id, 3))
        `when`(bot.actionHandler.banking.withdrawAll(requested)).thenReturn(true)
        val failed = script(bot)
        failed.onBankOpen(false)
        assertTrue(failed.isTerminated())
        InventoryProductionFixtures.inventory(bot, *requested.toTypedArray())
        val success = script(bot)
        assertTrue(success.onInit(false))
        assertTrue(success.onBankRequested(false))
        success.onBankOpen(false)
        assertFalse(success.isTerminated())
        assertEquals(0, success.snapshot().bankFailures)
    }
}
