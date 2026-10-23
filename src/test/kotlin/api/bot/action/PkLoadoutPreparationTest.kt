package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.bot.script.BotScript
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSet
import engine.bot.gear.BotItemTracker
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.CombatBotScript
import game.player.item.consume.food.Food
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.World
import io.luna.game.model.EntityState
import io.luna.game.model.def.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.*
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.util.parser.impl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises compact packing for all seven saved style combinations with native item definitions and containers.
 * Bank/equipment acknowledgements are fixture-controlled; actual ownership, slot use and death risk are evaluated.
 *
 * @author lare96
 */
class PkLoadoutPreparationTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() {
            InventoryProductionFixtures.initialize()
            if (EquipmentDefinition.ALL.get(841).isEmpty) EquipmentDefinitionFileParser().run()
            if (WeaponTypeDefinition.ALL.isEmpty()) WeaponTypeDefinitionFileParser().run()
            if (WeaponDefinition.getWeapons().isEmpty()) WeaponDefinitionFileParser().run()
            AmmoDefinitionFileParser().run()
            if (CombatSpellDefinition.getSpells().isEmpty()) CombatSpellDefinitionFileParser().run()
        }
    }

    private lateinit var bot: Bot
    private lateinit var preparation: PkLoadoutPreparation
    private lateinit var luna: MockedStatic<Luna>
    private val stackability = HashMap<Int, Boolean>()
    private var afterWithdraw: (Item) -> Unit = {}

    @BeforeEach fun setup() = runBlocking<Unit> {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
        val field = ItemDefinition::class.java.getDeclaredField("stackable").apply { isAccessible = true }
        for (id in listOf(995, 882, 556, 558, 554, 562, 565, 806)) {
            val definition = ItemDefinition.ALL.retrieve(id)
            stackability[id] = definition.isStackable
            field.setBoolean(definition, true)
        }
        bot = InventoryProductionFixtures.bot()
        `when`(bot.asPlr()).thenReturn(bot)
        `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(bot.skullIcon).thenReturn(SkullIcon.NONE)
        val fixtureWorld = mock(World::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.world).thenReturn(fixtureWorld)
        `when`(fixtureWorld.economy.getPrice(anyInt())).thenReturn(1L)
        for (skill in 0..20) {
            `when`(bot.skill(skill).staticLevel).thenReturn(99)
            `when`(bot.skill(skill).level).thenReturn(99)
        }
        val personality = mock(BotPersonality::class.java)
        `when`(personality.type).thenReturn(BotPersonalityManager.PersonalityTemplateType.PKER)
        `when`(personality.confidence).thenReturn(0.5)
        `when`(personality.intelligence).thenReturn(0.5)
        `when`(bot.personality).thenReturn(personality)
        profile(Styles.TRIBRID)
        val equipment = Equipment(bot).also { it.setListeners(BotItemTracker(bot)) }
        `when`(bot.equipment).thenReturn(equipment)
        val bank = spy(bot.bank)
        `when`(bank.isOpen).thenReturn(true)
        `when`(bot.bank).thenReturn(bank)
        val handler = bot.actionHandler
        val fallbackItem = Item(995)
        val banking = handler.banking
        val gear = handler.equipment
        val combat = handler.combat
        doReturn(true).`when`(banking).travelToBankOpen()
        doAnswer {
            val items = bot.inventory.filterNotNull()
            for (item in items) {
                if (!bank.add(item) || !bot.inventory.remove(item)) return@doAnswer false
            }
            true
        }.`when`(banking).depositInventory(emptySet())
        doAnswer {
            equipment.withIndex().filter { it.value != null }.all { equipment.unequip(it.index) }
        }.`when`(gear).unequipAll()
        doAnswer { call ->
            val requested = call.getArgument<Item>(0)
            val amount = minOf(requested.amount, bank.computeAmountForId(requested.id))
            val item = Item(requested.id, amount)
            val success = amount > 0 && bot.inventory.hasSpaceFor(item) && bank.remove(item) && bot.inventory.add(item)
            afterWithdraw(item)
            success
        }.`when`(banking).withdraw(any(Item::class.java) ?: fallbackItem)
        doAnswer { call ->
            val index = bot.inventory.computeIndexForId(call.getArgument(0))
            index >= 0 && equipment.equip(index)
        }.`when`(gear).equip(anyInt())
        val policy = BotCombatActionHandler(bot, handler)
        doAnswer { policy.pkRiskBudget() }.`when`(combat).pkRiskBudget()
        doReturn(true).`when`(combat).fleeWilderness()
        preparation = PkLoadoutPreparation(bot, handler)
        stock()
    }

    @AfterEach fun close() {
        try {
            val field = ItemDefinition::class.java.getDeclaredField("stackable").apply { isAccessible = true }
            stackability.forEach { (id, original) -> field.setBoolean(ItemDefinition.ALL.retrieve(id), original) }
            stackability.clear()
        } finally {
            if (::luna.isInitialized && !luna.isClosed) luna.close()
        }
    }

    private fun profile(styles: Styles) {
        val preferences = BotPreference.Builder(mock(BotPersonalityManager::class.java), bot.personality)
            .addSkill(Skill.STRENGTH).addGear(BotGearSet.values().first())
            .setPk(BotPkPreference(Purpose.GENERIC, styles, Build.MAIN)).build()
        `when`(bot.preferences).thenReturn(preferences)
        preferences.setBot(bot)
    }

    private fun stock() = InventoryProductionFixtures.bank(bot, Item(4151), Item(1277), Item(841), Item(1381),
        Item(882, 1000), Item(558, 2000), Item(379, 100), Item(995, 1_000_000))

    private fun allItems() = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
        .groupBy { it.id }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }

    @Test fun clanPreparationGrantsOnlyItsMissingCapeAndKeepsItEquippedAcrossAllSavedStyles() = runBlocking<Unit> {
        for (styles in Styles.values()) {
            profile(styles)
            val saved = bot.preferences.save()
            saved.add("pk", BotPkPreference(Purpose.CLAN, styles, Build.MAIN, 4315).save())
            bot.preferences.load(saved)
            assertTrue(preparation.prepare())
            assertEquals(4315, bot.equipment[Equipment.CAPE]?.id)
            assertTrue(preparation.ready())
            assertEquals(1L, allItems()[4315])
        }
        assertTrue(bot.equipment.unequip(Equipment.CAPE))
        assertFalse(preparation.ready())
        assertTrue(preparation.prepare())
        assertEquals(1L, allItems()[4315])
    }

    @Test fun everyTeamCapeHasNativeCapeSlotAndRequirementsUsableByPureBuilds() {
        for (skill in 0..20) `when`(bot.skill(skill).staticLevel).thenReturn(1)
        for (id in 4315..4413 step 2) {
            val definition = EquipmentDefinition.ALL.retrieve(id)
            assertEquals(Equipment.CAPE, definition.index)
            assertTrue(definition.meetsAllRequirements(bot))
            assertEquals(0, definition.getBonus(Equipment.EquipmentBonus.MAGIC_ATTACK.index))
        }
    }

    @Test fun allSevenStyleCombinationsPackOwnedWeaponsFoodAndSuppliesWithoutChangingPreferences() {
        for (styles in Styles.values()) {
            profile(styles)
            val identity = bot.preferences.pk
            val before = allItems()
            val plan = preparation.plan()!!
            val items = plan.equipment + plan.inventory
            if (styles.uses(Skill.ATTACK)) assertTrue(items.any { it.id == 4151 })
            if (styles.uses(Skill.RANGED)) {
                assertTrue(items.any { it.id == 841 })
                assertEquals(500, items.filter { it.id == 882 }.sumOf { it.amount })
            }
            if (styles.uses(Skill.MAGIC)) {
                assertTrue(items.any { it.id == 1381 })
                assertEquals(500, items.filter { it.id == 558 }.sumOf { it.amount })
            }
            assertEquals(16, plan.inventory.count { Food.ID_TO_FOOD.containsKey(it.id) })
            assertTrue(plan.inventory.size <= 26)
            assertEquals(before, allItems())
            assertEquals(identity, bot.preferences.pk)
        }
    }

    @Test fun magicAndTwoHandedSwitchesKeepSpaceAndLimitExtraArmourToBodyAndLegs() {
        InventoryProductionFixtures.bank(bot, Item(1127), Item(1079), Item(1163), Item(1185), Item(577), Item(1011),
            Item(1129), Item(1095), Item(1061))
        val plan = preparation.plan()!!
        val armour = plan.inventory.filter { EquipmentDefinition.ALL.get(it.id).isPresent }
            .filter { EquipmentDefinition.ALL.retrieve(it.id).index !in setOf(Equipment.WEAPON, Equipment.AMMUNITION) }
        assertTrue(armour.all { EquipmentDefinition.ALL.retrieve(it.id).index in setOf(Equipment.CHEST, Equipment.LEGS) })
        assertTrue(plan.inventory.size <= 26)
        assertEquals(16, plan.inventory.count { Food.ID_TO_FOOD.containsKey(it.id) })
    }

    @Test fun anUnaffordablePreferredWeaponFallsBackToCheaperOwnedGear() {
        profile(Styles.MELEE)
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(bot.world.economy.getPrice(4151)).thenReturn(2_000_000L)
        val plan = preparation.plan()!!
        assertFalse((plan.inventory + plan.equipment).any { it.id == 4151 })
        assertTrue((plan.inventory + plan.equipment).any { it.id == 1277 || it.id == 1381 })
        assertTrue(PkRiskBudget.assess(bot, true, plan.inventory, plan.equipment, true).withinBudget)
    }

    @Test fun anUnaffordableDamageSpellFallsBackWithoutDroppingTheMagicDiscipline() {
        InventoryProductionFixtures.bank(bot, Item(565), Item(554, 7))
        `when`(bot.world.economy.getPrice(565)).thenReturn(1_000_000L)
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        val identity = bot.preferences.pk
        val plan = preparation.plan()!!
        assertFalse(plan.inventory.any { it.id == 565 })
        assertTrue(plan.inventory.any { it.id == 558 })
        assertTrue(plan.equipment.plus(plan.inventory).any { it.id == 1381 })
        assertEquals(identity, bot.preferences.pk)
    }

    @Test fun thrownWeaponsPackAndEquipTheirWholeOwnedAmmunitionStack() = runBlocking<Unit> {
        profile(Styles.RANGED)
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(806, 800), Item(379, 30), Item(995, 1_000_000))
        val before = allItems()
        val plan = preparation.plan()!!
        assertTrue(plan.equipment.contains(Item(806, 500)))
        assertTrue(preparation.prepare())
        assertEquals(500, bot.equipment.computeAmountForId(806))
        assertTrue(preparation.ready())
        assertEquals(before, allItems())
    }

    @Test fun limitedOwnedStocksProduceAShorterTripWithoutGrantingItems() {
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(4151), Item(841), Item(1381), Item(882, 7), Item(558, 9),
            Item(379, 3), Item(995, 10000))
        val before = allItems()
        val plan = preparation.plan()!!
        assertEquals(3, plan.inventory.count { it.id == 379 })
        assertEquals(7, (plan.inventory + plan.equipment).filter { it.id == 882 }.sumOf { it.amount })
        assertEquals(9, plan.inventory.filter { it.id == 558 }.sumOf { it.amount })
        assertEquals(before, allItems())
    }

    @Test fun aMissingAssignedStyleOrFoodDeclinesTheTripInsteadOfChangingTheSavedStyles() {
        val identity = bot.preferences.pk
        bot.bank.remove(Item(1381))
        assertNull(preparation.plan())
        assertEquals(identity, bot.preferences.pk)
        stock()
        bot.bank.remove(Item(379, bot.bank.computeAmountForId(379)))
        assertNull(preparation.plan())
    }

    @Test fun preparationMovesOnlyOwnedStockAndLeavesTheExactPlannedKit() = runBlocking<Unit> {
        InventoryProductionFixtures.inventory(bot, Item(995, 500))
        bot.preferences.raiseWantedItemTarget(558, 20_000)
        val before = allItems()
        assertTrue(preparation.prepare())
        assertTrue(preparation.ready())
        assertEquals(before, allItems())
        assertEquals(16, bot.inventory.filterNotNull().count { it.id == 379 })
        assertEquals(0, bot.inventory.computeAmountForId(995))
        assertTrue(bot.inventory.computeRemainingSize() >= 2)
        assertEquals(20_000, bot.preferences.getWantedItem(558).target())
        assertTrue(bot.preferences.getWantedItem(882).target() >= 1000)
        verify(bot.actionHandler.banking).clickBankingMode(false)
        verify(bot.overlays).closeWindows()
    }

    @Test fun partialWithdrawalsCannotAuthorizeDeparture() = runBlocking<Unit> {
        afterWithdraw = { item ->
            if (item.id == 558) bot.inventory.remove(Item(558))
        }
        assertFalse(preparation.prepare())
        verify(bot.overlays).closeWindows()
    }

    @Test fun unexpectedInventoryChangesCannotAuthorizeDeparture() = runBlocking<Unit> {
        afterWithdraw = { item -> if (item.id == 558) bot.inventory.add(Item(995)) }
        assertFalse(preparation.prepare())
    }

    @Test fun enteringCombatDuringAWithdrawalAbortsPreparation() = runBlocking<Unit> {
        afterWithdraw = { `when`(bot.combat.inCombat()).thenReturn(true) }
        assertFalse(preparation.prepare())
        verify(bot.actionHandler.equipment, never()).equip(anyInt())
    }

    @Test fun combatStartingAsTheBankOpensStillClosesTheInterface() = runBlocking<Unit> {
        val banking = bot.actionHandler.banking
        doAnswer { `when`(bot.combat.inCombat()).thenReturn(true); true }.`when`(banking).travelToBankOpen()
        assertFalse(preparation.prepare())
        verify(bot.overlays).closeWindows()
        verify(banking, never()).withdraw(any(Item::class.java) ?: Item(995))
    }

    @Test fun leavingTheActiveWorldDuringAWithdrawalAbortsPreparation() = runBlocking<Unit> {
        afterWithdraw = { `when`(bot.state).thenReturn(EntityState.INACTIVE) }
        assertFalse(preparation.prepare())
        verify(bot.actionHandler.equipment, never()).equip(anyInt())
    }

    @Test fun becomingOverBudgetAfterAnEquipmentSuspensionAbortsTheTrip() = runBlocking<Unit> {
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        afterWithdraw = { item -> `when`(bot.world.economy.getPrice(item.id)).thenReturn(100_000_000L) }
        assertFalse(preparation.prepare())
    }

    @Test fun newSessionsPrepareBeforeTravelAndSupplyExhaustionReturnsForAnotherPack() = runBlocking<Unit> {
        val session = PkBotScript(bot, 5.minutes)
        assertFalse(session.run())
        assertTrue(preparation.ready())
        bot.inventory.remove(Item(379, 16))
        assertFalse(session.run())
        assertTrue(preparation.ready())
        verify(bot.actionHandler.banking, times(2)).travelToBankOpen()
    }

    @Test fun wildernessRestockingUsesEscapeAndRejectsNewPursuits() = runBlocking<Unit> {
        bot.wildernessLevel = 3
        val session = PkBotScript(bot, 5.minutes)
        assertFalse(session.run())
        verify(bot.actionHandler.combat).fleeWilderness()
        verify(bot.combat).setTarget(null)
        verify(bot.actionHandler.banking, never()).travelToBankOpen()
    }

    @Test fun combatReflexCannotInterruptAParentThatIsEscapingForSupplies() = runBlocking<Unit> {
        val stack = BotScriptStack(bot, mock(BotScriptManager::class.java))
        `when`(bot.scriptStack).thenReturn(stack)
        bot.wildernessLevel = 3
        val session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        stack.pushTail(session)
        assertFalse(session.run())
        `when`(bot.combat.inCombat()).thenReturn(true)
        for (focus in listOf<Mob>(mock(Npc::class.java), mock(Player::class.java))) {
            `when`(bot.combat.target).thenReturn(focus)
            assertTrue(BotReflex().process(bot))
            assertSame(session, stack.current())
        }
        // A different activity above the paused PK parent still owns its ordinary defensive reflex.
        val npc = mock(Npc::class.java)
        `when`(bot.combat.target).thenReturn(npc)
        `when`(bot.emotions.getCombatResponse(npc)).thenReturn(CombatBotScript.InitialState.ATTACK)
        stack.pushHead(mock(BotScript::class.java))
        assertFalse(BotReflex().process(bot))
        assertTrue(stack.current() is CombatBotScript)
    }

    @Test fun anAbortedRiskTripKeepsEscapingEvenIfPricesLaterFall() = runBlocking<Unit> {
        val session = PkBotScript(bot, 5.minutes)
        assertFalse(session.run())
        `when`(bot.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(bot.world.economy.getPrice(4151)).thenReturn(100_000_000L)
        val combat = bot.actionHandler.combat
        doReturn(false).`when`(combat).fleeWilderness()
        assertFalse(session.run())
        `when`(bot.world.economy.getPrice(4151)).thenReturn(1L)
        assertFalse(session.run())
        verify(combat, times(2)).fleeWilderness()
        assertTrue(session.isRestocking())
    }
}
