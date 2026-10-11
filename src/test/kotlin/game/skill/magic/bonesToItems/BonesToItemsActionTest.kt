package game.skill.magic.bonesToItems

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.magic.*
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.Task
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.lang.reflect.InvocationTargetException

class BonesToItemsActionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
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

    private fun bot(type: BonesToItemsType, bones: Int = 1, casts: Int = 2): Bot =
        InventoryProductionFixtures.bot().also {
            val inventory = spy(it.inventory)
            for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
                doReturn(true).`when`(inventory).stackable(id)
            }
            `when`(it.inventory).thenReturn(inventory)
            `when`(it.rights).thenReturn(PlayerRights.PLAYER)
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.magic.level).thenReturn(type.level)
            if (bones > 0) InventoryProductionFixtures.inventory(it, Item(526, bones))
            type.requirements.filterIsInstance<RuneRequirement>().forEach { req ->
                InventoryProductionFixtures.inventory(it, Item(req.rune.id, req.amount * casts))
            }
        }

    private fun cast(bot: Bot, type: BonesToItemsType, beforeCompletion: () -> Unit = {}) {
        val fixtureWorld = world
        val tasks = mutableListOf<Task>()
        doAnswer { invocation -> tasks += invocation.getArgument<Task>(0); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            BonesToItemsAction(bot, type).execute()
            beforeCompletion()
            for (task in tasks) task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun oneBoneAndFullBatchesConvertEveryInputWhilePayingOneCast() {
        for (type in BonesToItemsType.entries) for (count in listOf(1, 25)) {
            val bot = bot(type, count)
            if (count == 25) assertTrue(bot.inventory.isFull)
            cast(bot, type)
            assertFalse(bot.inventory.contains(526))
            assertEquals(count, bot.inventory.computeAmountForId(type.id))
            for (index in 0 until count) assertEquals(type.id, bot.inventory.computeIdForIndex(index))
            if (count == 25) assertTrue(bot.inventory.isFull)
            type.requirements.filterIsInstance<RuneRequirement>().forEach {
                assertEquals(it.amount, bot.inventory.computeAmountForId(it.rune.id))
            }
            verify(bot.magic).addExperience(type.xp)
            verify(bot).unlock()
        }
    }

    @Test fun missingBonesRunesOrLevelCannotStartACast() {
        for (type in BonesToItemsType.entries) for (missing in listOf(-1, 526, Rune.EARTH.id, Rune.WATER.id, Rune.NATURE.id)) {
            val bot = bot(type)
            if (missing == -1) `when`(bot.magic.level).thenReturn(type.level - 1)
            else bot.inventory.remove(Item(missing, bot.inventory.computeAmountForId(missing)))
            cast(bot, type)
            assertFalse(bot.inventory.contains(type.id))
            verify(bot, never()).lock()
            verify(bot.magic, never()).addExperience(anyDouble())
        }
    }

    @Test fun suppliesOrLevelLostDuringTheDelayPreventConversionAndFurtherCosts() {
        for (type in BonesToItemsType.entries) for (missing in listOf(-1, 526, Rune.EARTH.id, Rune.WATER.id, Rune.NATURE.id)) {
            val bot = bot(type, 4)
            cast(bot, type) {
                if (missing == -1) `when`(bot.magic.level).thenReturn(type.level - 1)
                else bot.inventory.remove(Item(missing, bot.inventory.computeAmountForId(missing)))
            }
            assertFalse(bot.inventory.contains(type.id))
            if (missing != 526) assertEquals(4, bot.inventory.computeAmountForId(526))
            type.requirements.filterIsInstance<RuneRequirement>().filter { it.rune.id != missing }.forEach {
                assertEquals(it.amount * 2, bot.inventory.computeAmountForId(it.rune.id))
            }
            verify(bot.magic, never()).addExperience(anyDouble())
            verify(bot).unlock()
        }
    }

    @Test fun partialBoneRemovalConvertsTheRemainingBonesWithOneRuneCost() {
        for (type in BonesToItemsType.entries) {
            val bot = bot(type, 6)
            cast(bot, type) { bot.inventory.remove(Item(526)) }
            assertEquals(5, bot.inventory.computeAmountForId(type.id))
            assertFalse(bot.inventory.contains(526))
            verify(bot.magic).addExperience(type.xp)
        }
    }

    @Test fun inactiveOrDeadPlayersCannotCompleteAndAlwaysReleaseTheLock() {
        for (type in BonesToItemsType.entries) for (inactive in listOf(true, false)) {
            val bot = bot(type)
            cast(bot, type) {
                if (inactive) `when`(bot.state).thenReturn(EntityState.INACTIVE)
                else `when`(bot.health).thenReturn(0)
            }
            assertTrue(bot.inventory.contains(526))
            assertFalse(bot.inventory.contains(type.id))
            verify(bot.magic, never()).addExperience(anyDouble())
            verify(bot).unlock()
        }
    }

    @Test fun staffCostsAreResolvedAgainAtCompletionAndCombinationRunesCoverBothElements() {
        for (type in BonesToItemsType.entries) for (removeStaff in listOf(false, true)) {
            val bot = bot(type)
            bot.inventory.remove(Item(Rune.EARTH.id, bot.inventory.computeAmountForId(Rune.EARTH.id)))
            bot.inventory.remove(Item(Rune.WATER.id, bot.inventory.computeAmountForId(Rune.WATER.id)))
            `when`(bot.equipment.weapon).thenReturn(Item(Staff.MUD.ids.first()))
            cast(bot, type) { if (removeStaff) `when`(bot.equipment.weapon).thenReturn(null) }
            assertEquals(!removeStaff, bot.inventory.contains(type.id))
            assertEquals(removeStaff, bot.inventory.contains(526))
            assertEquals(if (removeStaff) 2 else 1, bot.inventory.computeAmountForId(Rune.NATURE.id))
        }
        for (type in BonesToItemsType.entries) {
            val bot = bot(type)
            bot.inventory.remove(Item(Rune.EARTH.id, bot.inventory.computeAmountForId(Rune.EARTH.id)))
            bot.inventory.remove(Item(Rune.WATER.id, bot.inventory.computeAmountForId(Rune.WATER.id)))
            InventoryProductionFixtures.inventory(bot, Item(CombinationRune.MUD.id, 10))
            cast(bot, type)
            val amount = type.requirements.filterIsInstance<RuneRequirement>().first().amount
            assertEquals(10 - amount, bot.inventory.computeAmountForId(CombinationRune.MUD.id))
            assertEquals(1, bot.inventory.computeAmountForId(type.id))
            assertEquals(1, bot.inventory.computeAmountForId(Rune.NATURE.id))
        }
    }

    @Test fun betaAndAdministratorCastsStillNeedBonesAndConvertFullInventoriesWithoutRuneCosts() {
        for (type in BonesToItemsType.entries) for (beta in listOf(true, false)) for (count in listOf(0, 28)) {
            `when`(settings.game().betaMode()).thenReturn(beta)
            val bot = bot(type, bones = 0)
            bot.inventory.clear()
            if (!beta) `when`(bot.rights).thenReturn(PlayerRights.ADMINISTRATOR)
            `when`(bot.magic.level).thenReturn(1)
            if (count > 0) InventoryProductionFixtures.inventory(bot, Item(526, count))
            cast(bot, type)
            assertFalse(bot.inventory.contains(526))
            assertEquals(count, bot.inventory.computeAmountForId(type.id))
            verify(bot.magic, times(if (count > 0) 1 else 0)).addExperience(type.xp)
            if (count > 0) verify(bot).unlock() else verify(bot, never()).lock()
        }
    }

    @Test fun conversionFailureAwardsNoXpConsumesNoRunesAndReleasesTheLock() {
        for (type in BonesToItemsType.entries) {
            val bot = bot(type)
            val inventory = bot.inventory
            doThrow(IllegalStateException("fixture failure")).`when`(inventory).replaceAll(526, type.id)
            val exception = assertThrows(InvocationTargetException::class.java) { cast(bot, type) }
            assertInstanceOf(IllegalStateException::class.java, exception.cause)
            verify(bot.magic, never()).addExperience(anyDouble())
            type.requirements.filterIsInstance<RuneRequirement>().forEach {
                assertEquals(it.amount * 2, bot.inventory.computeAmountForId(it.rune.id))
            }
            verify(bot).unlock()
        }
    }
}
