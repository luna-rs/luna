package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import com.google.gson.JsonObject
import engine.bot.coordinator.CombatCoordinator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.skill.magic.Rune
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.util.parser.impl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class NpcCombatStylesTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initializeCombat()
        }
    }
    private lateinit var luna: MockedStatic<Luna>
    @BeforeEach fun normalMode() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }
    @AfterEach fun restoreMode() { luna.close() }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.preferences.pk).thenReturn(null)
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        for (id in 0..20) `when`(it.skill(id).level).thenReturn(99)
    }
    private fun script(bot: Bot, purpose: BotGearPurpose, selected: NpcCombatLoadout): NpcCombatScript {
        val script = InventoryProductionFixtures.active(NpcCombatScript(bot, 10.minutes,
            mutableListOf(SubZone.LUMBRIDGE_COW_PEN), purpose = purpose, spell = selected.spell?.spell))
        NpcCombatScript::class.java.getDeclaredField("loadout").apply { isAccessible = true; set(script, selected) }
        return script
    }
    private fun gear(weapon: Int, ammo: Int? = null) = arrayOfNulls<Int>(14).also {
        it[Equipment.WEAPON] = weapon
        it[Equipment.AMMUNITION] = ammo
    }

    @Test fun runeCostsApplyOnlyTheSelectedStaffAndKeepCatalystRunes() {
        val spell = CombatSpell.FIRE_STRIKE.def
        assertEquals(mapOf(Rune.AIR.id to 1, Rune.MIND.id to 1), NpcCombatLoadout.runeCosts(spell, 1387))
        assertEquals(mapOf(Rune.FIRE.id to 3, Rune.MIND.id to 1), NpcCombatLoadout.runeCosts(spell, 1381))
        assertEquals(3, NpcCombatLoadout.runeCosts(spell, null).size)
    }
    @Test fun savedModeSpellAndNamesRoundTripAndLegacyDataDefaultsToMelee() {
        val bot = bot()
        val script = NpcCombatScript(bot, 10.minutes, mutableListOf(SubZone.LUMBRIDGE_COW_PEN),
            ImmutableSetMultimap.of(SubZone.LUMBRIDGE_COW_PEN, "Cow"), BotGearPurpose.MAGIC, CombatSpell.WIND_STRIKE)
        val json = JsonObject().also { script.snapshot().save(it) }
        val loaded = NpcCombatData().also { it.load(json) }
        assertEquals(BotGearPurpose.MAGIC, loaded.purpose)
        assertEquals(CombatSpell.WIND_STRIKE, loaded.spell)
        assertEquals(setOf("Cow"), loaded.names[SubZone.LUMBRIDGE_COW_PEN])
        json.remove("purpose"); json.remove("spell"); json.remove("names")
        val legacy = NpcCombatData().also { it.load(json) }
        assertEquals(BotGearPurpose.MELEE, legacy.purpose)
        assertNull(legacy.spell)
        assertTrue(legacy.names.isEmpty)
    }
    @Test fun centralRegistrationLoadsCombatSpecificState() {
        val previous = world.botManager.scriptManager
        val manager = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(manager)
        try {
            InventoryProductionFixtures.register("Scripts")
            val json = JsonObject().also { NpcCombatScript(bot(), 10.minutes,
                mutableListOf(SubZone.LUMBRIDGE_COW_PEN), purpose = BotGearPurpose.RANGED).snapshot().save(it) }
            val loaded = manager.loadScript(NpcCombatScript::class.java.name, bot(), NpcCombatData().also { it.load(json) }) as NpcCombatScript
            assertEquals(BotGearPurpose.RANGED, loaded.purpose)
            val legacy = ZonedBotScript.Companion.ZonedBotScriptData().apply {
                duration = 10.minutes; zones = mutableListOf(SubZone.LUMBRIDGE_COW_PEN)
            }
            assertEquals(BotGearPurpose.MELEE,
                (manager.loadScript(NpcCombatScript::class.java.name, bot(), legacy) as NpcCombatScript).purpose)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
    @Test fun emptyQuiverBanksBeforeAnyAttackClick() = runBlocking<Unit> {
        val bot = bot()
        val selected = NpcCombatLoadout(gear(841, 882))
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        val script = script(bot, BotGearPurpose.RANGED, selected)
        assertNull(script.interactionOption(mock(Npc::class.java)))
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
        assertTrue(ZonedBotScript::class.java.getDeclaredField("forceBanking").apply { isAccessible = true }.getBoolean(script))
    }
    @Test fun depletedBankSuppliesRequestTotalStockAndCancelInsteadOfLooping() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        val script = script(bot, BotGearPurpose.RANGED, NpcCombatLoadout(gear(841, 882)))
        `when`(bot.actionHandler.banking.withdraw(Item(882, 500))).thenReturn(false)
        script.onBankOpen(false)
        verify(bot.preferences).addWantedItem(882, 1000)
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }
    @Test fun dragonDispatchRequiresProfitMeleeAndOwnedShield() {
        fun choices(training: Boolean, purpose: BotGearPurpose, shield: Boolean) =
            CombatCoordinator.eligibleCamps(126, training, purpose, shield).map { it.zone }
        assertTrue(SubZone.GREEN_DRAGONS in choices(false, BotGearPurpose.MELEE, true))
        assertFalse(SubZone.GREEN_DRAGONS in choices(true, BotGearPurpose.MELEE, true))
        assertFalse(SubZone.GREEN_DRAGONS in choices(false, BotGearPurpose.RANGED, true))
        assertFalse(SubZone.GREEN_DRAGONS in choices(false, BotGearPurpose.MAGIC, true))
        assertFalse(SubZone.GREEN_DRAGONS in choices(false, BotGearPurpose.MELEE, false))
    }

    private fun queenGear() = gear(4151).also {
        it[Equipment.HEAD] = 1163
        it[Equipment.CHEST] = 1127
        it[Equipment.LEGS] = 1079
        it[Equipment.SHIELD] = 1201
    }

    private fun queenBot(): Bot = bot().also { bot ->
        `when`(bot.combatLevel).thenReturn(110)
        `when`(bot.attack.staticLevel).thenReturn(85)
        `when`(bot.strength.staticLevel).thenReturn(85)
        `when`(bot.defence.staticLevel).thenReturn(85)
        `when`(bot.hitpoints.staticLevel).thenReturn(85)
        `when`(bot.prayer.staticLevel).thenReturn(70)
        `when`(bot.prayer.level).thenReturn(70)
        queenGear().forEachIndexed { slot, id ->
            `when`(bot.equipment[slot]).thenReturn(id?.let(::Item))
            if (id != null) {
                bot.itemTracker.add(id)
            }
        }
        KalphiteQueenTrip.supplies.forEach { (id, amount) -> bot.itemTracker.add(id, amount) }
    }

    @Test fun queenSelectionRequiresProfitMeleeAndTripReadiness() {
        fun selected(training: Boolean, purpose: BotGearPurpose, ready: Boolean, level: Int = 110) =
            CombatCoordinator.eligibleCamps(level, training, purpose, false, ready).any { it.zone == SubZone.KALPHITE_QUEEN }
        assertTrue(selected(false, BotGearPurpose.MELEE, true))
        assertFalse(selected(true, BotGearPurpose.MELEE, true))
        assertFalse(selected(false, BotGearPurpose.RANGED, true))
        assertFalse(selected(false, BotGearPurpose.MAGIC, true))
        assertFalse(selected(false, BotGearPurpose.MELEE, false))
        assertFalse(selected(false, BotGearPurpose.MELEE, true, 109))
    }

    @Test fun queenReadinessRejectsBoostedLowDefenceAndWeakGear() {
        val bot = queenBot()
        val loadout = NpcCombatLoadout(queenGear())
        assertTrue(KalphiteQueenTrip.ready(bot, loadout))
        `when`(bot.defence.staticLevel).thenReturn(84)
        `when`(bot.defence.level).thenReturn(99)
        assertFalse(KalphiteQueenTrip.ready(bot, loadout))
        `when`(bot.defence.staticLevel).thenReturn(85)
        assertFalse(KalphiteQueenTrip.ready(bot, NpcCombatLoadout(gear(1277))))
        val noArmour = queenGear().also { it[Equipment.CHEST] = null }
        assertFalse(KalphiteQueenTrip.gearReady(noArmour))
    }

    @Test fun queenTripsRequireOwnedStockAndKeepAnEscapeReserve() {
        val bot = queenBot()
        val loadout = NpcCombatLoadout(queenGear())
        assertTrue(KalphiteQueenTrip.ready(bot, loadout))
        bot.itemTracker.remove(KalphiteQueenTrip.ROPE, 1)
        assertFalse(KalphiteQueenTrip.ready(bot, loadout))
        InventoryProductionFixtures.inventory(bot, Item(KalphiteQueenTrip.FOOD, 3))
        assertTrue(KalphiteQueenTrip.canContinue(bot))
        bot.inventory.remove(Item(KalphiteQueenTrip.FOOD))
        assertFalse(KalphiteQueenTrip.canContinue(bot))
    }

    @Test fun queenPreparationStopsAttackClicksWhenSuppliesAreMissing() {
        val bot = queenBot()
        val script = NpcCombatScript(bot, 10.minutes, mutableListOf(SubZone.KALPHITE_QUEEN))
        assertFalse(script.prepareCombat())
        assertTrue(ZonedBotScript::class.java.getDeclaredField("forceBanking").apply { isAccessible = true }.getBoolean(script))
        verify(bot.combat).target = null
        verify(bot.reflex).isDisableCombatReflex = true
    }

    @Test fun queenZoneDoesNotIncludeTheUpperLairFloor() {
        val bot = bot()
        `when`(bot.localSubZones).thenReturn(com.google.common.collect.ImmutableSet.of(SubZone.KALPHITE_QUEEN))
        val memberships = java.util.EnumSet.noneOf(SubZone::class.java)
        doReturn(memberships).`when`(bot).subZones
        val upperFloor = io.luna.game.model.Position(3485, 9498, 2)
        val chamber = io.luna.game.model.Position(3485, 9498)
        `when`(bot.position).thenReturn(upperFloor)
        val positionField = io.luna.game.model.Entity::class.java.getDeclaredField("position").apply { isAccessible = true }
        positionField.set(bot, upperFloor)
        SubZone.updateSubZone(bot)
        assertFalse(SubZone.KALPHITE_QUEEN in memberships)
        `when`(bot.position).thenReturn(chamber)
        positionField.set(bot, chamber)
        SubZone.updateSubZone(bot)
        assertTrue(SubZone.KALPHITE_QUEEN in memberships)
    }

    @Test fun queenSessionKeepsItsZoneAndNpcRestrictionWhenSaved() {
        val original = NpcCombatScript(bot(), 10.minutes, mutableListOf(SubZone.KALPHITE_QUEEN),
            ImmutableSetMultimap.of(SubZone.KALPHITE_QUEEN, "Kalphite Queen"))
        val json = JsonObject()
        original.snapshot().save(json)
        val data = NpcCombatData().also { it.load(json) }
        val restored = NpcCombatScript(bot(), data).snapshot()
        assertEquals(listOf(SubZone.KALPHITE_QUEEN), restored.zones)
        assertEquals(setOf("Kalphite Queen"), restored.names[SubZone.KALPHITE_QUEEN])
        assertEquals(BotGearPurpose.MELEE, restored.purpose)
    }
    @Test fun explicitNpcRestrictionsCannotBeBypassedByRockCrabException() {
        val bot = bot()
        val target = mock(Npc::class.java, RETURNS_DEEP_STUBS)
        `when`(target.def().name).thenReturn("Rock Crab")
        val script = NpcCombatScript(bot, 10.minutes, mutableListOf(SubZone.LUMBRIDGE_COW_PEN),
            ImmutableSetMultimap.of(SubZone.LUMBRIDGE_COW_PEN, "Cow"))
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, SubZone.LUMBRIDGE_COW_PEN) }
        val canAttack = NpcCombatScript::class.java.getDeclaredMethod("canAttack", Npc::class.java).apply { isAccessible = true }
        assertFalse(canAttack.invoke(script, target) as Boolean)
        `when`(target.def().name).thenReturn("Cow")
        assertTrue(canAttack.invoke(script, target) as Boolean)
    }
    @Test fun mismatchedResumeEquipmentCannotAttackWithAnImaginaryStaff() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1387))
        val script = script(bot, BotGearPurpose.MAGIC, NpcCombatLoadout(gear(1381), CombatSpell.WIND_STRIKE.def))
        assertFalse(script.prepareCombat())
        verify(bot.actionHandler.combat, never()).setAutocastSpell(CombatSpell.WIND_STRIKE.def)
    }
    @Test fun finishingMagicSessionClearsManualAndAutocastSpells() = runBlocking<Unit> {
        val bot = bot()
        val script = script(bot, BotGearPurpose.MAGIC, NpcCombatLoadout(gear(1381), CombatSpell.WIND_STRIKE.def))
        script.finish()
        verify(bot.combat.magic).setSelectedSpell(io.luna.game.model.def.CombatSpellDefinition.NONE)
        verify(bot.combat.magic).setAutocastSpell(io.luna.game.model.def.CombatSpellDefinition.NONE)
        verify(bot.combat.magic).setAutocasting(false)
    }

    @Test fun highDefensiveCombatLevelDoesNotSendNoviceOffensiveStylesToStrongNpcs() {
        val bot = bot()
        `when`(bot.combatLevel).thenReturn(126)
        `when`(bot.ranged.level).thenReturn(1)
        `when`(bot.magic.level).thenReturn(1)
        `when`(bot.attack.level).thenReturn(1)
        `when`(bot.strength.level).thenReturn(1)
        for (purpose in listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) {
            assertEquals(5, CombatCoordinator.combatReadiness(bot, purpose))
        }
    }

}
