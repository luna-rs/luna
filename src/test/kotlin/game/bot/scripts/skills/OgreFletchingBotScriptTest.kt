package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FletchingScriptFactory
import game.bot.scripts.skills.OgreFletchingBotScript.Companion.OgreFletchingData
import game.skill.fletching.ogre.AcheyProduct
import game.skill.fletching.ogre.BrutalArrow
import game.skill.fletching.ogre.ChiselBonesAction
import game.skill.fletching.ogre.ChiselBonesMenu
import game.skill.fletching.ogre.CutAcheyLogAction
import game.skill.fletching.ogre.OgreFletching
import game.skill.fletching.ogre.OgreFletching.ACHEY_LOGS
import game.skill.fletching.ogre.OgreFletching.BOW_STRING
import game.skill.fletching.ogre.OgreFletching.CHISEL
import game.skill.fletching.ogre.OgreFletching.COMP_BOW_EXP
import game.skill.fletching.ogre.OgreFletching.COMP_OGRE_BOW
import game.skill.fletching.ogre.OgreFletching.FEATHER
import game.skill.fletching.ogre.OgreFletching.FLIGHTED_EXP
import game.skill.fletching.ogre.OgreFletching.FLIGHTED_OGRE_ARROW
import game.skill.fletching.ogre.OgreFletching.HAMMER
import game.skill.fletching.ogre.OgreFletching.KNIFE
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW_LEVEL
import game.skill.fletching.ogre.OgreFletching.OGRE_ARROW_SHAFT
import game.skill.fletching.ogre.OgreFletching.SHAFTS_PER_LOG
import game.skill.fletching.ogre.OgreFletching.TIPS_PER_BONE
import game.skill.fletching.ogre.OgreFletching.TIP_EXP
import game.skill.fletching.ogre.OgreFletching.UNSTRUNG_COMP_BOW
import game.skill.fletching.ogre.OgreFletching.WOLFBONE_ARROWTIPS
import game.skill.fletching.ogre.OgreFletching.WOLF_BONES
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises ogre production selection, supply ratios, native conversions and saved bot sessions.
 *
 * @author lare96
 */
class OgreFletchingBotScriptTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.fletching.staticLevel).thenReturn(99)
        `when`(it.fletching.level).thenReturn(99)
        `when`(it.personality.isDextrous).thenReturn(true)
    }

    private fun script(bot: Bot, key: String) =
        OgreFletchingBotScript(bot, OgreFletchingBotScript.RECIPES.getValue(key), 10.minutes)

    private suspend fun withImmediateConditions(body: suspend () -> Unit) {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            body()
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
        }
    }

    @Test
    fun scriptUsesTheAcheyProductIndexAndDedicatedBoneMakeAllButton() = runBlocking {
        withImmediateConditions {
            for (key in listOf("SHAFTS", "CUT_BOW", "TIPS")) {
                val bot = bot()
                val script = script(bot, key)
                val recipe = script.recipe
                InventoryProductionFixtures.inventory(bot,
                    *(recipe.tools.map { Item(it) } + recipe.materials).toTypedArray())
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                val menu = if (recipe.bones) ChiselBonesMenu::class.java else MakeItemDialogue::class.java
                `when`(bot.actionHandler.inventory.useItem(recipe.used).onItem(recipe.target)).thenAnswer {
                    `when`(bot.overlays.has(menu)).thenReturn(true)
                    true
                }
                if (recipe.bones) {
                    `when`(bot.output.clickButton(ChiselBonesMenu.MAKE_ALL)).thenAnswer {
                        InventoryProductionFixtures.execute(bot, ChiselBonesAction(bot, 1))
                        true
                    }
                } else {
                    val product = requireNotNull(recipe.acheyProduct)
                    val widgets = bot.actionHandler.widgets
                    doAnswer { invocation ->
                        assertEquals(product.ordinal, invocation.getArgument<Int>(0))
                        assertEquals(1, invocation.getArgument<Int>(1))
                        InventoryProductionFixtures.execute(bot, CutAcheyLogAction(bot, product, 1))
                        null
                    }.`when`(widgets).clickMakeItem(anyInt(), anyInt())
                }
                script.executeInZone()
                assertTrue(bot.inventory.contains(recipe.output), key)
                assertEquals(0, script.snapshot().failures, key)
                verify(bot.actionHandler.inventory.useItem(recipe.used)).onItem(recipe.target)
                if (recipe.bones) {
                    verify(bot.output).clickButton(ChiselBonesMenu.MAKE_ALL)
                    verify(bot.actionHandler.widgets, never()).clickMakeItem(anyInt(), anyInt())
                } else {
                    verify(bot.actionHandler.widgets).clickMakeItem(requireNotNull(recipe.acheyProduct).ordinal, 1)
                    verify(bot.output, never()).clickButton(anyInt())
                }
            }
        }
    }

    @Test
    fun scriptUsesDirectItemInteractionsForAttachmentsAndCompositeStringing() = runBlocking {
        withImmediateConditions {
            for (key in listOf("FLIGHTED", "OGRE", "BRUTAL:RUNE", "STRING_BOW")) {
                val bot = bot()
                val script = script(bot, key)
                val recipe = script.recipe
                InventoryProductionFixtures.inventory(bot,
                    *(recipe.tools.map { Item(it) } + recipe.materials).toTypedArray())
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(recipe.used).onItem(recipe.target)).thenAnswer {
                    when (key) {
                        "FLIGHTED" -> OgreFletching.featherShafts(bot)
                        "OGRE" -> OgreFletching.tipArrows(bot)
                        "BRUTAL:RUNE" -> OgreFletching.nailArrows(bot, BrutalArrow.RUNE)
                        else -> OgreFletching.stringCompBow(bot)
                    }
                }
                script.executeInZone()
                assertEquals(1, bot.inventory.computeAmountForId(recipe.output), key)
                assertEquals(0, script.snapshot().failures, key)
                verify(bot.actionHandler.inventory.useItem(recipe.used)).onItem(recipe.target)
                verify(bot.actionHandler.widgets, never()).clickMakeItem(anyInt(), anyInt())
                verify(bot.output, never()).clickButton(anyInt())
            }
        }
    }

    @Test
    fun allNativeRecipesCanBeSelectedInBothModesFromOwnedMinimumSupplies() {
        assertEquals(13, OgreFletchingBotScript.RECIPES.size)
        for ((key, recipe) in OgreFletchingBotScript.RECIPES) {
            val bot = bot()
            InventoryProductionFixtures.bank(bot, *(recipe.tools.map { Item(it) } + recipe.materials).toTypedArray())
            assertTrue(script(bot, key).isEligible(), key)
            for (training in listOf(true, false)) {
                val selected = FletchingScriptFactory.getScript(bot, 99, mutableListOf(), training)
                assertInstanceOf(OgreFletchingBotScript::class.java, selected, key)
                assertEquals(key, (selected as OgreFletchingBotScript).recipe.key)
            }
        }
    }

    @Test
    fun featheringRequiresFourFeathersAndBalancesPartialBankStock() {
        val bot = bot()
        val script = script(bot, "FLIGHTED")
        InventoryProductionFixtures.bank(bot, Item(OGRE_ARROW_SHAFT, 11), Item(FEATHER, 3))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(FEATHER, 42))
        assertTrue(script.isEligible())
        assertEquals(setOf(Item(OGRE_ARROW_SHAFT, 11), Item(FEATHER, 44)), script.bankBatch().toSet())
    }

    @ParameterizedTest
    @EnumSource(BrutalArrow::class)
    fun brutalArrowsRequireTheirNativeLevelAndAHammer(arrow: BrutalArrow) {
        val bot = bot()
        val script = script(bot, "BRUTAL:${arrow.name}")
        InventoryProductionFixtures.bank(bot, Item(FLIGHTED_OGRE_ARROW, 7), Item(arrow.nails, 3))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(HAMMER))
        assertTrue(script.isEligible())
        assertEquals(setOf(Item(HAMMER), Item(FLIGHTED_OGRE_ARROW, 3), Item(arrow.nails, 3)),
            script.bankBatch().toSet())
        `when`(bot.fletching.staticLevel).thenReturn(arrow.level - 1)
        assertFalse(script.isEligible())
    }

    @Test
    fun compositeCuttingBalancesLogsAndBonesWhileReservingTheKnife() {
        val bot = bot()
        val script = script(bot, "CUT_BOW")
        InventoryProductionFixtures.bank(bot, Item(KNIFE), Item(ACHEY_LOGS, 100))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(WOLF_BONES, 3))
        assertEquals(setOf(Item(KNIFE), Item(ACHEY_LOGS, 3), Item(WOLF_BONES, 3)), script.bankBatch().toSet())
        InventoryProductionFixtures.bank(bot, Item(WOLF_BONES, 100))
        assertEquals(setOf(Item(KNIFE), Item(ACHEY_LOGS, 13), Item(WOLF_BONES, 13)), script.bankBatch().toSet())
    }

    @Test
    fun boneAndLogProcessingReserveOneToolSlotAndStringingUsesFourteenPairs() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(KNIFE), Item(CHISEL), Item(ACHEY_LOGS, 100),
            Item(WOLF_BONES, 100), Item(UNSTRUNG_COMP_BOW, 100), Item(BOW_STRING, 100))
        assertEquals(setOf(Item(KNIFE), Item(ACHEY_LOGS, 27)), script(bot, "SHAFTS").bankBatch().toSet())
        assertEquals(setOf(Item(CHISEL), Item(WOLF_BONES, 27)), script(bot, "TIPS").bankBatch().toSet())
        assertEquals(setOf(Item(UNSTRUNG_COMP_BOW, 14), Item(BOW_STRING, 14)),
            script(bot, "STRING_BOW").bankBatch().toSet())
    }

    @Test
    fun fullInventoryContinuesOnlyWhenTheNextPartialBatchReleasesOutputSpace() = runBlocking {
        val processable = bot()
        InventoryProductionFixtures.inventory(processable, Item(OGRE_ARROW_SHAFT, 6), Item(FEATHER, 24), Item(1205, 26))
        assertTrue(processable.inventory.isFull)
        assertFalse(script(processable, "FLIGHTED").onBankRequested(false))

        val blocked = bot()
        InventoryProductionFixtures.inventory(blocked, Item(OGRE_ARROW_SHAFT, 7), Item(FEATHER, 28), Item(1205, 26))
        assertTrue(blocked.inventory.isFull)
        assertTrue(script(blocked, "FLIGHTED").onBankRequested(false))
    }

    @Test
    fun nativeFeatheringUsesFourFeathersPerArrowAndKeepsPartialRemainders() {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(OGRE_ARROW_SHAFT, 8), Item(FEATHER, 27))
        assertTrue(OgreFletching.featherShafts(bot))
        assertEquals(6, bot.inventory.computeAmountForId(FLIGHTED_OGRE_ARROW))
        assertEquals(2, bot.inventory.computeAmountForId(OGRE_ARROW_SHAFT))
        assertEquals(3, bot.inventory.computeAmountForId(FEATHER))
        assertFalse(OgreFletching.featherShafts(bot))
        verify(bot.fletching).addExperience(FLIGHTED_EXP * 6)
    }

    @ParameterizedTest
    @EnumSource(BrutalArrow::class)
    fun nativeBrutalConversionConsumesOnlyPairedMaterialsAndRetainsHammer(arrow: BrutalArrow) {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(HAMMER), Item(FLIGHTED_OGRE_ARROW, 9), Item(arrow.nails, 3))
        assertTrue(OgreFletching.nailArrows(bot, arrow))
        assertEquals(3, bot.inventory.computeAmountForId(arrow.id))
        assertEquals(6, bot.inventory.computeAmountForId(FLIGHTED_OGRE_ARROW))
        assertFalse(bot.inventory.contains(arrow.nails))
        assertTrue(bot.inventory.contains(HAMMER))
        verify(bot.fletching).addExperience(arrow.exp * 3)
    }

    @Test
    fun nativeActionsConsumeOneLogOrBoneAndAwardTheirActualOutputExperience() {
        val shafts = bot()
        InventoryProductionFixtures.inventory(shafts, Item(KNIFE), Item(ACHEY_LOGS))
        InventoryProductionFixtures.execute(shafts, CutAcheyLogAction(shafts, AcheyProduct.OGRE_ARROW_SHAFTS, 1))
        val shaftAmount = shafts.inventory.computeAmountForId(OGRE_ARROW_SHAFT)
        assertTrue(shaftAmount in SHAFTS_PER_LOG)
        assertFalse(shafts.inventory.contains(ACHEY_LOGS))
        verify(shafts.fletching).addExperience(AcheyProduct.OGRE_ARROW_SHAFTS.exp * shaftAmount)

        val tips = bot()
        InventoryProductionFixtures.inventory(tips, Item(CHISEL), Item(WOLF_BONES))
        InventoryProductionFixtures.execute(tips, ChiselBonesAction(tips, 1))
        val tipAmount = tips.inventory.computeAmountForId(WOLFBONE_ARROWTIPS)
        assertTrue(tipAmount in TIPS_PER_BONE)
        assertFalse(tips.inventory.contains(WOLF_BONES))
        verify(tips.fletching).addExperience(TIP_EXP * tipAmount)
    }

    @Test
    fun nativeCompositeBowCuttingAndStringingUseTheConfiguredMaterials() {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(KNIFE), Item(ACHEY_LOGS), Item(WOLF_BONES), Item(BOW_STRING))
        InventoryProductionFixtures.execute(bot, CutAcheyLogAction(bot, AcheyProduct.COMP_OGRE_BOW, 1))
        assertTrue(bot.inventory.contains(UNSTRUNG_COMP_BOW))
        assertFalse(bot.inventory.contains(ACHEY_LOGS))
        assertFalse(bot.inventory.contains(WOLF_BONES))
        assertTrue(OgreFletching.stringCompBow(bot))
        assertTrue(bot.inventory.contains(COMP_OGRE_BOW))
        assertFalse(bot.inventory.contains(UNSTRUNG_COMP_BOW))
        assertFalse(bot.inventory.contains(BOW_STRING))
        verify(bot.fletching, times(2)).addExperience(COMP_BOW_EXP)
    }

    @Test
    fun registrationRestoresRecipeZonesAndRetryBudgetsAfterJsonRoundTrip() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val original = OgreFletchingData().apply {
                recipe = "BRUTAL:RUNE"
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { original.save(it) }
            val data = OgreFletchingData().apply { load(json) }
            val restored = registry.loadScript(OgreFletchingBotScript::class.qualifiedName, bot(), data)
            assertInstanceOf(OgreFletchingBotScript::class.java, restored)
            val saved = (restored as OgreFletchingBotScript).snapshot()
            assertEquals(original.recipe, saved.recipe)
            assertEquals(original.duration, saved.duration)
            assertEquals(original.zones, saved.zones)
            assertEquals(original.failures, saved.failures)
            assertEquals(original.bankFailures, saved.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test
    fun currentLevelLossStopsProductionAndExhaustedRetriesRemainStoppedAfterResume() = runBlocking {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(OGRE_ARROW_SHAFT), Item(FEATHER, 4))
        `when`(bot.fletching.level).thenReturn(OGRE_ARROW_LEVEL - 1)
        val script = InventoryProductionFixtures.active(script(bot, "FLIGHTED"))
        script.executeInZone()
        assertTrue(script.isTerminated())
        verify(bot.actionHandler.inventory, never()).useItem(FEATHER)
        val data = script.snapshot().apply { failures = 3 }
        assertFalse(InventoryProductionFixtures.active(OgreFletchingBotScript(bot, data)).onInit(true))
    }
}
