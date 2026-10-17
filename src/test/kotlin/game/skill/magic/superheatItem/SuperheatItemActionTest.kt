package game.skill.magic.superheatItem

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.magic.*
import game.skill.smithing.BarType
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

class SuperheatItemActionTest {
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

    private fun bot(type: BarType, runes: Boolean = true): Bot = InventoryProductionFixtures.bot().also {
        val inventory = spy(it.inventory)
        for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
            doReturn(true).`when`(inventory).stackable(id)
        }
        `when`(it.inventory).thenReturn(inventory)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.magic.level).thenReturn(43)
        `when`(it.smithing.level).thenReturn(type.level)
        type.oreList.forEach { ore -> InventoryProductionFixtures.inventory(it, ore) }
        if (runes) InventoryProductionFixtures.inventory(it, Item(Rune.FIRE.id, 8), Item(Rune.NATURE.id, 2))
    }

    private fun cast(bot: Bot, index: Int = 0, beforeCompletion: () -> Unit = {}) {
        val fixtureWorld = world
        val tasks = mutableListOf<Task>()
        doAnswer { invocation -> tasks += invocation.getArgument<Task>(0); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            SuperheatItemAction(bot, index).execute()
            assertFalse(bot.inventory.any { it != null && it.id in BarType.ID_TO_BAR })
            verify(bot.magic, never()).addExperience(anyDouble())
            beforeCompletion()
            for (task in tasks) {
                assertEquals(2, task.delay)
                task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    private fun noExperience(bot: Bot) {
        verify(bot.magic, never()).addExperience(anyDouble())
        verify(bot.smithing, never()).addExperience(anyDouble())
    }

    @Test fun everyRecipeConsumesExactOresAndOneRuneCostInFullInventories() {
        for (type in BarType.entries) for (mode in listOf("normal", "beta", "admin")) {
            `when`(settings.game().betaMode()).thenReturn(mode == "beta")
            val bot = bot(type, runes = mode == "normal")
            if (mode == "admin") `when`(bot.rights).thenReturn(PlayerRights.ADMINISTRATOR)
            if (mode != "normal") `when`(bot.magic.level).thenReturn(1)
            val fillers = bot.inventory.computeRemainingSize()
            InventoryProductionFixtures.inventory(bot, Item(1925, fillers))
            assertTrue(bot.inventory.isFull)
            cast(bot)
            assertEquals(type.id, bot.inventory.computeIdForIndex(0))
            assertEquals(1, bot.inventory.computeAmountForId(type.id))
            type.oreList.forEach { assertEquals(0, bot.inventory.computeAmountForId(it.id)) }
            assertEquals(fillers, bot.inventory.computeAmountForId(1925))
            assertEquals(if (mode == "normal") 4 else 0, bot.inventory.computeAmountForId(Rune.FIRE.id))
            assertEquals(if (mode == "normal") 1 else 0, bot.inventory.computeAmountForId(Rune.NATURE.id))
            verify(bot.magic).addExperience(53.0)
            verify(bot.smithing).addExperience(type.xp)
            verify(bot).unlock()
        }
    }

    @Test fun steelTakesPrecedenceForIronOreWhileInsufficientCoalStillProducesIron() {
        val iron = BarType.IRON.oreRequired.first.id
        val coal = BarType.STEEL.oreRequired.second!!.id
        for (amount in 0..2) {
            val bot = bot(BarType.IRON)
            `when`(bot.smithing.level).thenReturn(30)
            if (amount > 0) InventoryProductionFixtures.inventory(bot, Item(coal, amount))
            cast(bot)
            val type = if (amount == 2) BarType.STEEL else BarType.IRON
            assertEquals(1, bot.inventory.computeAmountForId(type.id))
            assertEquals(0, bot.inventory.computeAmountForId(iron))
            assertEquals(if (amount == 2) 0 else amount, bot.inventory.computeAmountForId(coal))
            verify(bot.smithing).addExperience(type.xp)
        }
        val lowLevel = bot(BarType.STEEL)
        `when`(lowLevel.smithing.level).thenReturn(15)
        cast(lowLevel)
        assertTrue(lowLevel.inventory.containsAll(BarType.STEEL.oreList))
        verify(lowLevel, never()).lock()
        noExperience(lowLevel)
        val coalClick = bot(BarType.STEEL)
        cast(coalClick, coalClick.inventory.computeIndexForId(coal))
        assertTrue(coalClick.inventory.contains(BarType.STEEL.id))
    }

    @Test fun missingRequirementsOrInvalidTargetsCannotStart() {
        for (missing in listOf("magic", "smithing", "fire", "nature", "ore", "coal", "target", "negative", "capacity")) {
            val bot = bot(BarType.STEEL)
            var index = 0
            when (missing) {
                "magic" -> `when`(bot.magic.level).thenReturn(42)
                "smithing" -> `when`(bot.smithing.level).thenReturn(29)
                "fire" -> bot.inventory.remove(Item(Rune.FIRE.id, 8))
                "nature" -> bot.inventory.remove(Item(Rune.NATURE.id, 2))
                "ore" -> bot.inventory.remove(BarType.STEEL.oreRequired.first)
                "coal" -> {
                    bot.inventory.remove(BarType.STEEL.oreRequired.first)
                    index = bot.inventory.computeIndexForId(BarType.STEEL.oreRequired.second!!.id)
                }
                "target" -> index = bot.inventory.computeIndexForId(Rune.FIRE.id)
                "negative" -> index = -1
                "capacity" -> index = bot.inventory.capacity()
            }
            cast(bot, index)
            verify(bot, never()).lock()
            noExperience(bot)
        }
    }

    @Test fun changedSuppliesLevelsOrTargetAbortWithoutFurtherCostsOrRecipeDowngrade() {
        for (missing in listOf("magic", "smithing", "fire", "nature", "ore", "coal", "target")) {
            val bot = bot(BarType.STEEL)
            cast(bot) {
                when (missing) {
                    "magic" -> `when`(bot.magic.level).thenReturn(42)
                    "smithing" -> `when`(bot.smithing.level).thenReturn(29)
                    "fire" -> bot.inventory.remove(Item(Rune.FIRE.id, 8))
                    "nature" -> bot.inventory.remove(Item(Rune.NATURE.id, 2))
                    "ore" -> bot.inventory.remove(BarType.STEEL.oreRequired.first)
                    "coal" -> bot.inventory.remove(Item(BarType.STEEL.oreRequired.second!!.id))
                    "target" -> bot.inventory.set(0, Item(1925))
                }
            }
            assertFalse(bot.inventory.contains(BarType.STEEL.id))
            assertFalse(bot.inventory.contains(BarType.IRON.id))
            if (missing !in listOf("ore", "target")) assertTrue(bot.inventory.contains(BarType.STEEL.oreRequired.first))
            if (missing != "coal") assertTrue(bot.inventory.contains(BarType.STEEL.oreRequired.second!!))
            if (missing != "fire") assertEquals(8, bot.inventory.computeAmountForId(Rune.FIRE.id))
            if (missing != "nature") assertEquals(2, bot.inventory.computeAmountForId(Rune.NATURE.id))
            noExperience(bot)
            verify(bot).unlock()
        }
    }

    @Test fun fireStaffAndCombinationCostsAreResolvedAtCompletion() {
        for (removeStaff in listOf(false, true)) {
            val bot = bot(BarType.GOLD)
            bot.inventory.remove(Item(Rune.FIRE.id, 8))
            `when`(bot.equipment.weapon).thenReturn(Item(Staff.FIRE.ids.first()))
            cast(bot) { if (removeStaff) `when`(bot.equipment.weapon).thenReturn(null) }
            assertEquals(!removeStaff, bot.inventory.contains(BarType.GOLD.id))
            assertEquals(if (removeStaff) 2 else 1, bot.inventory.computeAmountForId(Rune.NATURE.id))
            if (removeStaff) noExperience(bot)
            verify(bot).unlock()
        }
        val removedWithRunes = bot(BarType.GOLD)
        `when`(removedWithRunes.equipment.weapon).thenReturn(Item(Staff.FIRE.ids.first()))
        cast(removedWithRunes) { `when`(removedWithRunes.equipment.weapon).thenReturn(null) }
        assertEquals(4, removedWithRunes.inventory.computeAmountForId(Rune.FIRE.id))
        assertEquals(1, removedWithRunes.inventory.computeAmountForId(Rune.NATURE.id))
        assertTrue(removedWithRunes.inventory.contains(BarType.GOLD.id))
        val equippedLate = bot(BarType.GOLD)
        cast(equippedLate) { `when`(equippedLate.equipment.weapon).thenReturn(Item(Staff.FIRE.ids.first())) }
        assertEquals(8, equippedLate.inventory.computeAmountForId(Rune.FIRE.id))
        assertEquals(1, equippedLate.inventory.computeAmountForId(Rune.NATURE.id))
        assertTrue(equippedLate.inventory.contains(BarType.GOLD.id))
        for (combo in CombinationRune.entries.filter { Rune.FIRE in it.represents }) {
            val bot = bot(BarType.GOLD)
            bot.inventory.remove(Item(Rune.FIRE.id, 8))
            InventoryProductionFixtures.inventory(bot, Item(combo.id, 8))
            cast(bot)
            assertEquals(4, bot.inventory.computeAmountForId(combo.id))
            assertTrue(bot.inventory.contains(BarType.GOLD.id))
            verify(bot.magic).addExperience(53.0)
        }
    }

    @Test fun inactiveOrDeadPlayersCannotCompleteAndReleaseTheirLock() {
        for (inactive in listOf(true, false)) for (atStart in listOf(true, false)) {
            val bot = bot(BarType.RUNE)
            val invalidate = {
                if (inactive) `when`(bot.state).thenReturn(EntityState.INACTIVE)
                else `when`(bot.health).thenReturn(0)
                Unit
            }
            if (atStart) invalidate()
            cast(bot) { if (!atStart) invalidate() }
            assertTrue(bot.inventory.containsAll(BarType.RUNE.oreList))
            assertTrue(bot.inventory.contains(Item(Rune.FIRE.id, 8)))
            assertTrue(bot.inventory.contains(Item(Rune.NATURE.id, 2)))
            noExperience(bot)
            if (atStart) verify(bot, never()).lock() else verify(bot).unlock()
        }
    }

    @Test fun freeSpellCostsDoNotBypassOresOrSmithingRequirements() {
        for (beta in listOf(true, false)) for (missingOre in listOf(true, false)) {
            `when`(settings.game().betaMode()).thenReturn(beta)
            val bot = bot(BarType.GOLD, runes = false)
            if (!beta) `when`(bot.rights).thenReturn(PlayerRights.ADMINISTRATOR)
            if (missingOre) bot.inventory.remove(BarType.GOLD.oreRequired.first)
            else `when`(bot.smithing.level).thenReturn(39)
            cast(bot)
            assertFalse(bot.inventory.contains(BarType.GOLD.id))
            verify(bot, never()).lock()
            noExperience(bot)
        }
    }

    @Test fun productReplacementFailureNeverConsumesSuppliesOrAwardsExperienceAndUnlocks() {
        for (throws in listOf(false, true)) {
            val bot = bot(BarType.STEEL)
            val inventory = bot.inventory
            val primary = BarType.STEEL.oreRequired.first.id
            if (throws) doThrow(IllegalStateException("fixture failure")).`when`(inventory).replace(primary, BarType.STEEL.id)
            else doReturn(false).`when`(inventory).replace(primary, BarType.STEEL.id)
            if (throws) {
                val exception = assertThrows(InvocationTargetException::class.java) { cast(bot) }
                assertInstanceOf(IllegalStateException::class.java, exception.cause)
            } else cast(bot)
            assertTrue(bot.inventory.containsAll(BarType.STEEL.oreList))
            assertEquals(8, bot.inventory.computeAmountForId(Rune.FIRE.id))
            assertEquals(2, bot.inventory.computeAmountForId(Rune.NATURE.id))
            noExperience(bot)
            verify(bot).unlock()
        }
    }
}
