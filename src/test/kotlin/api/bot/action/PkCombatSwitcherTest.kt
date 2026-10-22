package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearSet
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker
import game.bot.scripts.combat.CombatBotScript
import game.skill.magic.Rune
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.def.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.Item
import io.luna.game.model.mob.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.*
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.util.parser.impl.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*

/**
 * Uses native equipment/inventory movement with mocked click completion and a monotonic fixture clock.
 * Verifies carried supply selection, delayed switching, spell cleanup and the active combat script integration.
 *
 * @author lare96
 */
class PkCombatSwitcherTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() {
            InventoryProductionFixtures.initialize()
            if (EquipmentDefinition.ALL.get(841).isEmpty) EquipmentDefinitionFileParser().run()
            if (WeaponTypeDefinition.ALL.isEmpty()) WeaponTypeDefinitionFileParser().run()
            if (WeaponDefinition.getWeapons().isEmpty()) WeaponDefinitionFileParser().run()
            AmmoDefinitionFileParser().run()
            if (io.luna.game.model.def.CombatSpellDefinition.getSpells().isEmpty()) CombatSpellDefinitionFileParser().run()
        }
    }
    private lateinit var luna: MockedStatic<Luna>
    private lateinit var bot: Bot
    private lateinit var enemy: Player
    private lateinit var gear: BotEquipmentActionHandler
    private var now = 0L
    private var afterEquip: (Int) -> Unit = {}
    private val originalStackability = mutableMapOf<ItemDefinition, Boolean>()
    private val stackableField = ItemDefinition::class.java.getDeclaredField("stackable").apply { isAccessible = true }

    @BeforeEach fun setup() = runBlocking<Unit> {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(false)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
        // The shared item-dump fixture omits stackability. Restore native semantics for carried runes/arrows.
        for (id in listOf(Rune.MIND.id, 882, 806)) {
            val original = ItemDefinition.ALL.get(id).get()
            originalStackability[original] = original.isStackable
            stackableField.setBoolean(original, true)
        }
        bot = InventoryProductionFixtures.bot()
        `when`(bot.isAlive).thenReturn(true)
        doReturn(Position(3100, 3540)).`when`(bot).position
        `when`(bot.combat.isAttackable).thenReturn(true)
        `when`(bot.healthPercent).thenReturn(100)
        `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(bot.rights).thenReturn(PlayerRights.PLAYER)
        for (id in 0..20) {
            `when`(bot.skill(id).level).thenReturn(99)
            `when`(bot.skill(id).staticLevel).thenReturn(99)
        }
        val personality = mock(BotPersonality::class.java)
        `when`(personality.type).thenReturn(BotPersonalityManager.PersonalityTemplateType.PKER)
        `when`(personality.intelligence).thenReturn(1.0)
        `when`(personality.dexterity).thenReturn(1.0)
        `when`(bot.personality).thenReturn(personality)
        profile(Styles.TRIBRID)
        val equipment = Equipment(bot).also { it.setListeners(BotItemTracker(bot)) }
        `when`(bot.equipment).thenReturn(equipment)
        enemy = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(enemy.isAlive).thenReturn(true)
        `when`(enemy.state).thenReturn(EntityState.ACTIVE)
        doReturn(Position(3101, 3540)).`when`(enemy).position
        `when`(enemy.combat.isAttackable).thenReturn(true)
        `when`(enemy.isViewableFrom(bot)).thenReturn(true)
        `when`(bot.combat.checkMultiCombat(enemy)).thenReturn(true)
        `when`(bot.controllers.checkCombat(enemy)).thenReturn(true)
        val weapon = bot.combat.weapon
        val unarmed = WeaponDefinition.getUnarmed().typeDef
        `when`(weapon.typeDef).thenReturn(unarmed)
        `when`(weapon.type).thenReturn(unarmed.type)
        `when`(weapon.styleDef).thenReturn(unarmed.styles.first())
        doAnswer { call ->
            val selected = weapon.typeDef.styles.first { it.button == call.getArgument<Int>(0) }
            `when`(weapon.styleDef).thenReturn(selected)
            null
        }.`when`(weapon).changeStyle(anyInt())
        val magic = bot.combat.magic
        var auto = CombatSpellDefinition.NONE
        var manual = CombatSpellDefinition.NONE
        var casting = false
        `when`(magic.autocastSpell).thenAnswer { auto }
        `when`(magic.selectedSpell).thenAnswer { manual }
        `when`(magic.isAutocasting).thenAnswer { casting }
        doAnswer { auto = it.getArgument(0); null }.`when`(magic).setAutocastSpell(any(CombatSpellDefinition::class.java))
        doAnswer { manual = it.getArgument(0); null }.`when`(magic).setSelectedSpell(any(CombatSpellDefinition::class.java))
        doAnswer { casting = it.getArgument(0); null }.`when`(magic).setAutocasting(anyBoolean())
        gear = bot.actionHandler.equipment
        doAnswer { call ->
            val id = call.getArgument<Int>(0)
            val index = bot.inventory.computeIndexForId(id)
            val success = index >= 0 && equipment.equip(index)
            if (success) {
                val definition = WeaponDefinition.ALL.get(equipment.weapon?.id ?: -1).orElse(WeaponDefinition.getUnarmed()).typeDef
                `when`(weapon.typeDef).thenReturn(definition)
                `when`(weapon.type).thenReturn(definition.type)
                `when`(weapon.styleDef).thenReturn(definition.styles.first())
                `when`(weapon.isRanged).thenReturn(definition.styles.any { Skill.RANGED in it.exp })
            }
            afterEquip(id)
            success
        }.`when`(gear).equip(anyInt())
        doAnswer { call ->
            val slot = call.getArgument<Int>(0)
            equipment[slot] == null || equipment.unequip(slot)
        }.`when`(gear).unequip(anyInt())
    }
    @AfterEach fun close() {
        try {
            originalStackability.forEach { (definition, stackable) -> stackableField.setBoolean(definition, stackable) }
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
    private fun carry(vararg items: Item) { InventoryProductionFixtures.inventory(bot, *items) }
    private fun switcher() = PkCombatSwitcher(bot, bot.actionHandler) { now }

    @Test fun carriedLookupNeverUsesBankedWeaponsRunesOrAmmunition() {
        InventoryProductionFixtures.bank(bot, Item(1381), Item(Rune.MIND.id, 100), Item(882, 100))
        carry(Item(841))
        assertNull(CombatLoadout.find(bot, BotGearPurpose.MAGIC, carriedOnly = true))
        assertNull(CombatLoadout.find(bot, BotGearPurpose.RANGED, carriedOnly = true))
        carry(Item(882, 20))
        val ranged = CombatLoadout.find(bot, BotGearPurpose.RANGED, carriedOnly = true)!!
        assertEquals(841, ranged.gear[Equipment.WEAPON])
        assertEquals(882, ranged.gear[Equipment.AMMUNITION])
        assertEquals(100, bot.bank.computeAmountForId(882))
        carry(Item(1381), Item(Rune.MIND.id, 20))
        assertNotNull(CombatLoadout.find(bot, BotGearPurpose.MAGIC, carriedOnly = true))
    }

    @Test fun firstEquipWaitsForTheReactionDeadlineAndRetainsTheReadyStyle() = runBlocking<Unit> {
        profile(Styles.MELEE)
        carry(Item(1277))
        val switching = switcher()
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        now = 599_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        verify(gear, never()).equip(anyInt())
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        assertEquals(1277, bot.equipment.weapon!!.id)
        now += 100_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        verify(gear, times(1)).equip(1277)
    }

    @Test fun armourChangeSwitchesCarriedMagicToRangedAndClearsAutocast() = runBlocking<Unit> {
        carry(Item(1381), Item(Rune.MIND.id, 50), Item(841), Item(882, 50))
        `when`(enemy.equipment.getBonus(EquipmentBonus.MAGIC_DEFENCE)).thenReturn(-100)
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        assertEquals(1381, bot.equipment.weapon!!.id)
        assertTrue(bot.combat.magic.isAutocasting)
        `when`(enemy.equipment.getBonus(EquipmentBonus.MAGIC_DEFENCE)).thenReturn(100)
        `when`(enemy.equipment.getBonus(EquipmentBonus.RANGED_DEFENCE)).thenReturn(-100)
        now = 1_200_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        assertEquals(841, bot.equipment.weapon!!.id)
        assertEquals(50, bot.equipment.computeAmountForId(882))
        assertFalse(bot.combat.magic.isAutocasting)
        assertEquals(CombatSpellDefinition.NONE, bot.combat.magic.autocastSpell)
        assertEquals(1, bot.inventory.computeAmountForId(1381))
    }

    @Test fun magicPhaseRemovesNegativeAccuracyArmourThroughTheNormalUnequipHandler() = runBlocking<Unit> {
        profile(Styles.MAGIC)
        carry(Item(1127), Item(1381), Item(Rune.MIND.id, 50))
        assertTrue(gear.equip(1127))
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        assertNull(bot.equipment[Equipment.CHEST])
        assertEquals(1, bot.inventory.computeAmountForId(1127))
        assertTrue(bot.combat.magic.isAutocasting)
    }

    @Test fun fullInventoryPreventsATwoHandedSwitchWithoutDeletingOrSpawningGear() = runBlocking<Unit> {
        carry(Item(1381), Item(1540), Item(841), Item(882, 50), Item(Rune.MIND.id, 50))
        assertTrue(gear.equip(1381))
        assertTrue(gear.equip(1540))
        while (!bot.inventory.isFull) assertTrue(bot.inventory.add(Item(1277)))
        profile(Styles.RANGED)
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        assertEquals(1381, bot.equipment.weapon!!.id)
        assertEquals(1540, bot.equipment[Equipment.SHIELD]!!.id)
        assertEquals(1, bot.inventory.computeAmountForId(841))
        assertTrue(bot.inventory.isFull)
        now += 600_000_000
        assertEquals(PkCombatSwitcher.Result.UNAVAILABLE, switching.prepare(enemy))
    }

    @Test fun runeLossWhileEquippingCannotEnableAnInvalidCastOrUseBankStock() = runBlocking<Unit> {
        profile(Styles.MAGIC)
        carry(Item(1381), Item(Rune.MIND.id, 50))
        InventoryProductionFixtures.bank(bot, Item(Rune.MIND.id, 1000))
        afterEquip = { bot.inventory.remove(Item(Rune.MIND.id, bot.inventory.computeAmountForId(Rune.MIND.id))) }
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        assertFalse(bot.combat.magic.isAutocasting)
        now += 600_000_000
        assertEquals(PkCombatSwitcher.Result.UNAVAILABLE, switching.prepare(enemy))
        assertEquals(1000, bot.bank.computeAmountForId(Rune.MIND.id))
    }

    @Test fun unexpectedSpellStateCannotLeakIntoAnActiveRangedPhase() = runBlocking<Unit> {
        profile(Styles.RANGED)
        carry(Item(841), Item(882, 50))
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
        bot.combat.magic.autocastSpell = io.luna.game.model.mob.combat.CombatSpell.WIND_STRIKE.def
        bot.combat.magic.selectedSpell = io.luna.game.model.mob.combat.CombatSpell.WATER_STRIKE.def
        bot.combat.magic.isAutocasting = true
        now += 100_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        assertFalse(bot.combat.magic.isAutocasting)
        assertEquals(CombatSpellDefinition.NONE, bot.combat.magic.selectedSpell)
        assertEquals(CombatSpellDefinition.NONE, bot.combat.magic.autocastSpell)
        now = 1_200_000_000
        assertEquals(PkCombatSwitcher.Result.READY, switching.prepare(enemy))
    }

    @Test fun targetDisappearingDuringAnEquipAbortsTheSwitch() = runBlocking<Unit> {
        profile(Styles.MAGIC)
        carry(Item(1381), Item(Rune.MIND.id, 50))
        afterEquip = { `when`(enemy.isViewableFrom(bot)).thenReturn(false) }
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        assertFalse(bot.combat.magic.isAutocasting)
        verify(bot.combat, never()).attack(any())
    }

    @Test fun targetCrossingTheWildernessBoundaryWhileEquippingCannotStartAnAttack() = runBlocking<Unit> {
        profile(Styles.MAGIC)
        carry(Item(1381), Item(Rune.MIND.id, 50))
        afterEquip = { doReturn(Position(3101, 3518)).`when`(enemy).position }
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        assertFalse(bot.combat.magic.isAutocasting)
        verify(bot.combat, never()).attack(any())
    }

    @Test fun failedEquipIsNotRetriedIndefinitely() = runBlocking<Unit> {
        profile(Styles.MELEE)
        carry(Item(1277))
        doReturn(false).`when`(gear).equip(1277)
        val switching = switcher()
        switching.prepare(enemy)
        now = 600_000_000
        assertEquals(PkCombatSwitcher.Result.WAITING, switching.prepare(enemy))
        now += 600_000_000
        assertEquals(PkCombatSwitcher.Result.UNAVAILABLE, switching.prepare(enemy))
        verify(gear, times(1)).equip(1277)
    }

    @Test fun combatScriptWaitsThenAttacksWithItsCarriedRangedChoice() = runBlocking<Unit> {
        profile(Styles.RANGED)
        carry(Item(806, 50))
        `when`(bot.prayer.level).thenReturn(0)
        val script = InventoryProductionFixtures.active(CombatBotScript(bot, enemy))
        CombatBotScript::class.java.getDeclaredField("pkSwitcher").apply {
            isAccessible = true
            set(script, switcher())
        }
        assertFalse(script.init(false))
        verify(bot.combat, never()).attack(any())
        now = 600_000_000
        assertFalse(script.run())
        verify(bot.combat, times(1)).attack(enemy)
        assertEquals(806, bot.equipment.weapon!!.id)
    }
}
