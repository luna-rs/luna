package game.bot.scripts

import api.bot.action.CombatLoadout
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
import io.luna.game.model.mob.combat.Weapon
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
            InventoryProductionFixtures.initialize()
            if (io.luna.game.model.def.EquipmentDefinition.ALL.get(841).isEmpty) EquipmentDefinitionFileParser().run()
            if (io.luna.game.model.def.WeaponTypeDefinition.ALL.isEmpty()) WeaponTypeDefinitionFileParser().run()
            if (io.luna.game.model.def.WeaponDefinition.getWeapons().isEmpty()) WeaponDefinitionFileParser().run()
            AmmoDefinitionFileParser().run()
            if (io.luna.game.model.def.CombatSpellDefinition.getSpells().isEmpty()) CombatSpellDefinitionFileParser().run()
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
        `when`(it.magic.level).thenReturn(99)
        `when`(it.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(it.rights).thenReturn(PlayerRights.PLAYER)
        for (id in 0..20) `when`(it.skill(id).level).thenReturn(99)
    }
    private fun script(bot: Bot, purpose: BotGearPurpose, selected: CombatLoadout): NpcCombatScript {
        val script = InventoryProductionFixtures.active(NpcCombatScript(bot, 10.minutes,
            mutableListOf(SubZone.LUMBRIDGE_COW_PEN), purpose = purpose, spell = selected.spell?.spell))
        NpcCombatScript::class.java.getDeclaredField("loadout").apply { isAccessible = true; set(script, selected) }
        return script
    }
    private fun gear(weapon: Int, ammo: Int? = null) = arrayOfNulls<Int>(14).also {
        it[Equipment.WEAPON] = weapon
        it[Equipment.AMMUNITION] = ammo
    }

    @Test fun bowWithoutCompatibleAmmoIsRejectedWithoutFreeSupplies() {
        val bot = bot()
        bot.itemTracker.add(841)
        assertNull(CombatLoadout.find(bot, BotGearPurpose.RANGED))
        assertEquals(1, bot.itemTracker.size)
        assertEquals(0, bot.bank.size())
    }
    @Test fun bowSelectsOwnedCompatibleAmmoAndRejectsIncompatibleArrows() {
        val bot = bot()
        bot.itemTracker.add(841)
        bot.itemTracker.add(892, 500)
        assertNull(CombatLoadout.find(bot, BotGearPurpose.RANGED))
        bot.itemTracker.add(882, 500)
        val selected = CombatLoadout.find(bot, BotGearPurpose.RANGED)!!
        assertEquals(841, selected.gear[Equipment.WEAPON])
        assertEquals(882, selected.gear[Equipment.AMMUNITION])
    }
    @Test fun throwingWeaponsUseOwnedWeaponStackWithoutQuiverAmmo() {
        val bot = bot()
        bot.itemTracker.add(806, 100)
        val selected = CombatLoadout.find(bot, BotGearPurpose.RANGED)!!
        assertEquals(806, selected.gear[Equipment.WEAPON])
        assertNull(selected.gear[Equipment.AMMUNITION])
    }
    @Test fun magicRequiresStaffSpellbookLevelAndOwnedRunes() {
        val bot = bot()
        bot.itemTracker.add(1381)
        assertNull(CombatLoadout.find(bot, BotGearPurpose.MAGIC))
        bot.itemTracker.add(Rune.MIND.id, 50)
        val selected = CombatLoadout.find(bot, BotGearPurpose.MAGIC)!!
        assertEquals(CombatSpell.WIND_STRIKE, selected.spell!!.spell)
        assertEquals(mapOf(Rune.MIND.id to 1), selected.runes)
        `when`(bot.magic.level).thenReturn(0)
        assertNull(CombatLoadout.find(bot, BotGearPurpose.MAGIC))
        `when`(bot.magic.level).thenReturn(99)
        `when`(bot.spellbook).thenReturn(Spellbook.ANCIENT)
        assertNull(CombatLoadout.find(bot, BotGearPurpose.MAGIC))
    }
    @Test fun runeCostsApplyOnlyTheSelectedStaffAndKeepCatalystRunes() {
        val spell = CombatSpell.FIRE_STRIKE.def
        assertEquals(mapOf(Rune.AIR.id to 1, Rune.MIND.id to 1), CombatLoadout.runeCosts(spell, 1387))
        assertEquals(mapOf(Rune.FIRE.id to 3, Rune.MIND.id to 1), CombatLoadout.runeCosts(spell, 1381))
        assertEquals(3, CombatLoadout.runeCosts(spell, null).size)
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
        val selected = CombatLoadout(gear(841, 882))
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        val script = script(bot, BotGearPurpose.RANGED, selected)
        assertNull(script.interactionOption(mock(Npc::class.java)))
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
        assertTrue(ZonedBotScript::class.java.getDeclaredField("forceBanking").apply { isAccessible = true }.getBoolean(script))
    }
    @Test fun missingRunesClearAutocastAndRequestBanking() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        val script = script(bot, BotGearPurpose.MAGIC, CombatLoadout(gear(1381), CombatSpell.WIND_STRIKE.def))
        assertFalse(script.prepareCombat())
        verify(bot.combat.magic).setAutocasting(false)
        assertTrue(ZonedBotScript::class.java.getDeclaredField("forceBanking").apply { isAccessible = true }.getBoolean(script))
    }
    @Test fun magicUsesExistingAutocastHelperAndNeverSelectsMeleeStyle() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        `when`(bot.combat.weapon.type).thenReturn(Weapon.STAFF)
        bot.inventory.add(Item(Rune.MIND.id))
        val definition = CombatSpell.WIND_STRIKE.def
        `when`(bot.actionHandler.combat.setAutocastSpell(definition)).thenReturn(true)
        val script = script(bot, BotGearPurpose.MAGIC, CombatLoadout(gear(1381), definition))
        assertTrue(script.prepareCombat())
        verify(bot.actionHandler.combat).setAutocastSpell(definition)
        verify(bot.combat.weapon, never()).changeStyle(anyInt())
    }
    @Test fun depletedBankSuppliesRequestTotalStockAndCancelInsteadOfLooping() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        val script = script(bot, BotGearPurpose.RANGED, CombatLoadout(gear(841, 882)))
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
    @Test fun rangedStyleIsSelectedAfterEquipmentAndRetainedAcrossAttackClicks() = runBlocking<Unit> {
        val bot = bot()
        val style = mock(io.luna.game.model.def.CombatStyleDefinition::class.java)
        `when`(style.exp).thenReturn(com.google.common.collect.ImmutableList.of(io.luna.game.model.mob.Skill.RANGED))
        `when`(style.button).thenReturn(77)
        val weapon = bot.combat.weapon
        doAnswer {
            `when`(weapon.styleDef).thenReturn(style)
            null
        }.`when`(weapon).changeStyle(77)
        `when`(bot.combat.weapon.typeDef.styles).thenReturn(listOf(style))
        `when`(bot.equipment.weapon).thenReturn(Item(841))
        `when`(bot.equipment.computeAmountForId(882)).thenReturn(100)
        val script = script(bot, BotGearPurpose.RANGED, CombatLoadout(gear(841, 882)))
        val npc = mock(Npc::class.java)
        assertEquals(3, script.interactionOption(npc))
        assertEquals(3, script.interactionOption(npc))
        verify(bot.combat.weapon, times(1)).changeStyle(77)
    }
    @Test fun partialRuneWithdrawalUsesActualAvailableStockAndRestoresAutocast() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1381))
        val definition = CombatSpell.WIND_STRIKE.def
        `when`(bot.actionHandler.combat.setAutocastSpell(definition)).thenReturn(true)
        val banking = bot.actionHandler.banking
        doAnswer {
            bot.inventory.add(Item(Rune.MIND.id, 2))
            true
        }.`when`(banking).withdraw(Item(Rune.MIND.id, 500))
        val script = script(bot, BotGearPurpose.MAGIC, CombatLoadout(gear(1381), definition))
        script.onBankOpen(false)
        assertEquals(2, bot.inventory.computeAmountForId(Rune.MIND.id))
        verify(bot.actionHandler.combat).setAutocastSpell(definition)
        verify(bot.preferences, never()).addWantedItem(anyInt(), anyInt())
    }
    @Test fun mismatchedResumeEquipmentCannotAttackWithAnImaginaryStaff() {
        val bot = bot()
        `when`(bot.equipment.weapon).thenReturn(Item(1387))
        val script = script(bot, BotGearPurpose.MAGIC, CombatLoadout(gear(1381), CombatSpell.WIND_STRIKE.def))
        assertFalse(script.prepareCombat())
        verify(bot.actionHandler.combat, never()).setAutocastSpell(CombatSpell.WIND_STRIKE.def)
    }
    @Test fun finishingMagicSessionClearsManualAndAutocastSpells() = runBlocking<Unit> {
        val bot = bot()
        val script = script(bot, BotGearPurpose.MAGIC, CombatLoadout(gear(1381), CombatSpell.WIND_STRIKE.def))
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
