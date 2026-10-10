package game.skill.magic.chargeOrb

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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class ChargeOrbActionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun element(type: ChargeOrbType) = Rune.valueOf(type.name)

    private fun bot(type: ChargeOrbType, orbs: Int = 1, casts: Int = 1): Bot =
        InventoryProductionFixtures.bot().also {
            // Keep the real container and listeners, with stackable rune policy local to this fixture.
            val inventory = spy(it.inventory)
            for (id in Rune.entries.map { rune -> rune.id } + CombinationRune.entries.map { rune -> rune.id }) {
                doReturn(true).`when`(inventory).stackable(id)
            }
            `when`(it.inventory).thenReturn(inventory)
            `when`(it.rights).thenReturn(PlayerRights.PLAYER)
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.magic.level).thenReturn(type.level)
            InventoryProductionFixtures.inventory(it, Item(567, orbs),
                Item(element(type).id, 30 * casts), Item(Rune.COSMIC.id, 3 * casts))
        }

    private fun cast(bot: Bot, type: ChargeOrbType, beta: Boolean = false, beforeCompletion: () -> Unit = {}) {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(beta)
        val luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
        val tasks = mutableListOf<Task>()
        doAnswer { invocation -> tasks += invocation.getArgument<Task>(0); null }
            .`when`(world).schedule(any(Task::class.java))
        try {
            ChargeOrbAction(bot, type).execute()
            beforeCompletion()
            for (task in tasks) {
                task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
            }
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
            luna.close()
        }
    }

    @Test fun recipesRequireTheirOwnElementAndThreeCosmicRunes() {
        for (type in ChargeOrbType.entries) {
            val runes = type.requirements.filterIsInstance<RuneRequirement>()
            assertEquals(listOf(element(type) to 30, Rune.COSMIC to 3), runes.map { it.rune to it.amount })
            val orb = type.requirements.filterIsInstance<ItemRequirement>().single()
            assertEquals(567, orb.id)
            assertEquals(1, orb.amount)
        }
    }

    @Test fun everyElementChargesInAFullInventoryAndRetainsTheOriginalOrbSlot() {
        for (type in ChargeOrbType.entries) {
            val bot = bot(type, orbs = 26, casts = 2)
            assertTrue(bot.inventory.isFull)
            cast(bot, type)
            assertEquals(type.chargedOrb, bot.inventory.computeIdForIndex(0))
            assertEquals(25, bot.inventory.computeAmountForId(567))
            assertEquals(1, bot.inventory.computeAmountForId(type.chargedOrb))
            assertEquals(30, bot.inventory.computeAmountForId(element(type).id))
            assertEquals(3, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            assertTrue(bot.inventory.isFull)
            verify(bot.magic).addExperience(type.xp)
            verify(bot).unlock()
        }
    }

    @Test fun repeatedCastsConsumeExactlyOneOrbAndOneSpellCostEach() {
        for (type in ChargeOrbType.entries) {
            val bot = bot(type, orbs = 3, casts = 3)
            repeat(3) { cast(bot, type) }
            assertEquals(3, bot.inventory.computeAmountForId(type.chargedOrb))
            assertFalse(bot.inventory.contains(567))
            assertFalse(bot.inventory.contains(element(type).id))
            assertFalse(bot.inventory.contains(Rune.COSMIC.id))
            verify(bot.magic, times(3)).addExperience(type.xp)
        }
    }

    @Test fun lowLevelOrMissingOrbElementOrCosmicRuneCannotStartACast() {
        for (type in ChargeOrbType.entries) for (missing in listOf(-1, 567, element(type).id, Rune.COSMIC.id)) {
            val bot = bot(type)
            if (missing == -1) `when`(bot.magic.level).thenReturn(type.level - 1)
            else bot.inventory.remove(Item(missing, bot.inventory.computeAmountForId(missing)))
            cast(bot, type)
            assertFalse(bot.inventory.contains(type.chargedOrb))
            verify(bot, never()).lock()
            verify(bot.magic, never()).addExperience(anyDouble())
        }
    }

    @Test fun nonWaterSpellsRejectWaterRunesAsTheirOnlyElementalSupply() {
        for (type in ChargeOrbType.entries.filter { it != ChargeOrbType.WATER }) {
            val bot = bot(type)
            bot.inventory.remove(Item(element(type).id, 30))
            InventoryProductionFixtures.inventory(bot, Item(Rune.WATER.id, 30))
            cast(bot, type)
            assertTrue(bot.inventory.contains(567))
            assertEquals(30, bot.inventory.computeAmountForId(Rune.WATER.id))
            assertEquals(3, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            verify(bot.magic, never()).addExperience(anyDouble())
        }
    }

    @Test fun suppliesOrLevelLostDuringTheDelayPreserveTheRemainingItemsAndReleaseTheLock() {
        for (type in ChargeOrbType.entries) for (missing in listOf(-1, 567, element(type).id, Rune.COSMIC.id)) {
            val bot = bot(type)
            cast(bot, type) {
                if (missing == -1) `when`(bot.magic.level).thenReturn(type.level - 1)
                else bot.inventory.remove(Item(missing, bot.inventory.computeAmountForId(missing)))
            }
            assertFalse(bot.inventory.contains(type.chargedOrb))
            if (missing != 567) assertTrue(bot.inventory.contains(567))
            if (missing != element(type).id) assertEquals(30, bot.inventory.computeAmountForId(element(type).id))
            if (missing != Rune.COSMIC.id) assertEquals(3, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            verify(bot.magic, never()).addExperience(anyDouble())
            verify(bot).unlock()
        }
    }

    @Test fun inactiveOrDeadPlayersCannotCompleteADelayedCast() {
        for (type in ChargeOrbType.entries) for (inactive in listOf(true, false)) {
            val bot = bot(type)
            cast(bot, type) {
                if (inactive) `when`(bot.state).thenReturn(EntityState.INACTIVE)
                else `when`(bot.health).thenReturn(0)
            }
            assertTrue(bot.inventory.contains(567))
            assertFalse(bot.inventory.contains(type.chargedOrb))
            assertEquals(30, bot.inventory.computeAmountForId(element(type).id))
            assertEquals(3, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            verify(bot.magic, never()).addExperience(anyDouble())
            verify(bot).unlock()
        }
    }

    @Test fun elementalStavesSubstituteRunesAndAreRecheckedAtCompletion() {
        for (type in ChargeOrbType.entries) for (removeStaff in listOf(false, true)) {
            val bot = bot(type)
            bot.inventory.remove(Item(element(type).id, 30))
            val staff = Staff.entries.first { it.represents == setOf(element(type)) }.ids.first()
            `when`(bot.equipment.weapon).thenReturn(Item(staff))
            cast(bot, type) { if (removeStaff) `when`(bot.equipment.weapon).thenReturn(null) }
            assertEquals(!removeStaff, bot.inventory.contains(type.chargedOrb))
            assertEquals(removeStaff, bot.inventory.contains(567))
            assertEquals(if (removeStaff) 3 else 0, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            verify(bot.magic, times(if (removeStaff) 0 else 1)).addExperience(type.xp)
            verify(bot).unlock()
        }
    }

    @Test fun combinationRunesStillPayTheElementalCost() {
        for (type in ChargeOrbType.entries) {
            val bot = bot(type)
            bot.inventory.remove(Item(element(type).id, 30))
            val combined = CombinationRune.entries.first { element(type) in it.represents }
            InventoryProductionFixtures.inventory(bot, Item(combined.id, 35))
            cast(bot, type)
            assertEquals(5, bot.inventory.computeAmountForId(combined.id))
            assertFalse(bot.inventory.contains(567))
            assertEquals(1, bot.inventory.computeAmountForId(type.chargedOrb))
            verify(bot.magic).addExperience(type.xp)
        }
    }

    @Test fun administratorBypassPreservesFreeCastsButCannotAwardXpWithoutOutputSpace() {
        for (type in ChargeOrbType.entries) for (full in listOf(false, true)) {
            val bot = bot(type)
            `when`(bot.rights).thenReturn(PlayerRights.ADMINISTRATOR)
            bot.inventory.clear()
            if (full) InventoryProductionFixtures.inventory(bot, Item(1925, 28))
            cast(bot, type)
            assertEquals(!full, bot.inventory.contains(type.chargedOrb))
            verify(bot.magic, times(if (full) 0 else 1)).addExperience(type.xp)
            verify(bot).unlock()
        }
    }

    @Test fun betaBypassRetainsInputsAndRequiresSpaceForTheFreeProduct() {
        for (type in ChargeOrbType.entries) for (full in listOf(false, true)) {
            val bot = bot(type, orbs = if (full) 26 else 1)
            cast(bot, type, beta = true)
            assertEquals(!full, bot.inventory.contains(type.chargedOrb))
            assertEquals(if (full) 26 else 1, bot.inventory.computeAmountForId(567))
            assertEquals(30, bot.inventory.computeAmountForId(element(type).id))
            assertEquals(3, bot.inventory.computeAmountForId(Rune.COSMIC.id))
            verify(bot.magic, times(if (full) 0 else 1)).addExperience(type.xp)
            verify(bot).unlock()
        }
    }
}
