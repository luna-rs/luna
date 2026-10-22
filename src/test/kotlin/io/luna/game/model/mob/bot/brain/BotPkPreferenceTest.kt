package io.luna.game.model.mob.bot.brain

import api.bot.action.BotCombatActionHandler
import api.bot.script.InventoryProductionFixtures
import engine.bot.gear.BotGearPurpose
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import engine.bot.gear.BotGearSet
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.combat.CombatStance
import io.luna.game.model.mob.combat.Weapon
import io.luna.util.parser.impl.WeaponDefinitionFileParser
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

/**
 * Exercises permanent PK identity, legacy migration, and shared combat training decisions.
 * Uses native style definitions, with no server or generated combat experience.
 *
 * @author lare96
 */
class BotPkPreferenceTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() {
            InventoryProductionFixtures.initialize()
            if (io.luna.game.model.def.WeaponTypeDefinition.ALL.isEmpty())
                io.luna.util.parser.impl.WeaponTypeDefinitionFileParser().run()
            if (WeaponDefinition.getWeapons().isEmpty()) WeaponDefinitionFileParser().run()
        }
    }

    private fun personality(type: PersonalityTemplateType) = mock(BotPersonality::class.java).also {
        `when`(it.type).thenReturn(type)
    }

    private fun builder(personality: BotPersonality): BotPreference.Builder =
        BotPreference.Builder(mock(BotPersonalityManager::class.java), personality)
            .addSkill(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.PRAYER, Skill.WOODCUTTING)
            .addGear(BotGearSet.values().first())

    private fun bot(profile: BotPkPreference? = null, type: PersonalityTemplateType = PersonalityTemplateType.PKER): Bot {
        val bot = InventoryProductionFixtures.bot()
        val personality = personality(type)
        `when`(bot.personality).thenReturn(personality)
        for (id in 0..20) {
            `when`(bot.skill(id).staticLevel).thenReturn(if (id == Skill.HITPOINTS) 10 else 1)
        }
        val builder = builder(personality)
        if (profile != null) builder.setPk(profile)
        val preferences = builder.build()
        `when`(bot.preferences).thenReturn(preferences)
        preferences.setBot(bot)
        return bot
    }

    @Test fun explicitProfileSurvivesBuilderAndSaveLoadWithoutReroll() {
        val profile = BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.DRAGON_PURE)
        val saved = bot(profile).preferences.save()
        repeat(10) {
            val restored = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.MAIN))
            restored.preferences.load(saved)
            assertEquals(profile, restored.preferences.pk)
            assertEquals(saved.get("pk"), restored.preferences.save().get("pk"))
        }
    }

    @Test fun allSevenStyleCombinationsRoundTrip() {
        assertEquals(7, Styles.values().size)
        for (styles in Styles.values()) {
            val profile = BotPkPreference(Purpose.GENERIC, styles, Build.MAIN)
            assertEquals(profile, BotPkPreference.load(profile.save()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.ELEMENTAL_MAGE)
        }
    }

    @Test fun malformedSavedIdentityFailsInsteadOfSilentlyRerolling() {
        val saved = BotPkPreference(Purpose.VALUE, Styles.RANGED, Build.MAIN).save()
        saved.addProperty("build", "UNKNOWN")
        assertThrows(IllegalArgumentException::class.java) { BotPkPreference.load(saved) }
    }

    @Test fun legacyMigrationUsesRestoredLevelsInsteadOfProvisionalNewBotBuild() {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.GMAUL_PURE))
        val legacy = account.preferences.save().also { it.remove("pk") }
        for (id in 0..6) `when`(account.skill(id).staticLevel).thenReturn(99)
        account.preferences.load(legacy)
        assertEquals(Build.MAIN, account.preferences.pk!!.build())
        assertTrue(account.preferences.pk!!.build().fits(account))
        val migrated = account.preferences.save()
        account.preferences.load(migrated)
        assertEquals(migrated.get("pk"), account.preferences.save().get("pk"))
        for (id in 0..6) assertEquals(99, account.skill(id).staticLevel)
    }

    @Test fun legacyNonPkerDiscardsRandomProvisionalProfile() {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.GMAUL_PURE),
            PersonalityTemplateType.JACK_OF_ALL_TRADES)
        val legacy = account.preferences.save().also { it.remove("pk") }
        account.preferences.load(legacy)
        assertNull(account.preferences.pk)
    }

    @Test fun skillersRejectExplicitAndSavedPkingProfilesAndCombatWeights() {
        val profile = BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.MAIN)
        val account = bot(profile, PersonalityTemplateType.SKILLER)
        assertTrue(account.preferences.isSkiller)
        assertNull(account.preferences.pk)
        assertTrue(account.preferences.skills.none(Skill::isCombatSkill))
        val saved = bot(profile).preferences.save()
        saved.getAsJsonArray("activities").forEach { it.asJsonObject.addProperty("weight", 1.0) }
        account.preferences.load(saved)
        assertNull(account.preferences.pk)
        assertEquals(0.0, account.preferences.activities[BotActivity.PKING])
        for (activity in listOf(BotActivity.PKING, BotActivity.TRAINING_COMBAT, BotActivity.PROFIT_COMBAT)) {
            account.preferences.activities[activity] = 1.0
            assertFalse(account.preferences.canChooseActivity(activity))
            assertFalse(account.preferences.likesActivity(activity))
            assertFalse(account.preferences.lovesActivity(activity))
        }
        assertTrue(BotCombatActionHandler.wantedCombatSkills(account).isEmpty())
        assertTrue(BotCombatActionHandler.wantsToTrain(account, Skill.WOODCUTTING))
    }

    @Test fun skillerBrainRejectsCombatEvenInItsRandomFallback() {
        val account = bot(type = PersonalityTemplateType.SKILLER)
        account.preferences.activities.clear()
        account.preferences.activities[BotActivity.PKING] = 1.0
        account.preferences.activities[BotActivity.TRAINING_COMBAT] = 1.0
        account.preferences.activities[BotActivity.PROFIT_COMBAT] = 1.0
        val forbidden = setOf(BotActivity.PKING.coordinator, BotActivity.TRAINING_COMBAT.coordinator,
            BotActivity.PROFIT_COMBAT.coordinator)
        repeat(100) { assertTrue(BotBrain().process(account) !in forbidden) }
    }

    @Test fun resumedPrayerAndLampScriptsCannotBypassBuildTargets() = kotlinx.coroutines.runBlocking {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.GMAUL_PURE))
        `when`(account.skill(Skill.PRAYER).staticLevel).thenReturn(31)
        InventoryProductionFixtures.bank(account, io.luna.game.model.item.Item(game.skill.prayer.Bone.BONES.id, 28),
            io.luna.game.model.item.Item(game.bot.scripts.UseLampBotScript.LAMP))
        val burial = InventoryProductionFixtures.active(game.bot.scripts.skills.BuryBoneBotScript(
            account, game.skill.prayer.Bone.BONES, kotlin.time.Duration.parse("10m")))
        val lamp = InventoryProductionFixtures.active(game.bot.scripts.UseLampBotScript(
            account, game.item.experienceLamp.ExperienceLampInterface.InterfaceSkill.PRAYER,
            kotlin.time.Duration.parse("10m")))
        assertFalse(burial.onInit(true))
        assertFalse(lamp.onInit(true))
        assertTrue(burial.isTerminated())
        assertTrue(lamp.isTerminated())
        assertEquals(28, account.bank.computeAmountForId(game.skill.prayer.Bone.BONES.id))
    }

    @Test fun trainingPolicyUsesPermanentLevelsAndBuildTargets() {
        val account = bot(BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.DRAGON_PURE))
        assertFalse(BotCombatActionHandler.wantsToTrain(account, Skill.DEFENCE))
        assertTrue(BotCombatActionHandler.wantsToTrain(account, Skill.ATTACK))
        `when`(account.skill(Skill.ATTACK).staticLevel).thenReturn(60)
        `when`(account.skill(Skill.ATTACK).level).thenReturn(1)
        assertFalse(BotCombatActionHandler.wantsToTrain(account, Skill.ATTACK))
        `when`(account.skill(Skill.ATTACK).staticLevel).thenReturn(59)
        `when`(account.skill(Skill.ATTACK).level).thenReturn(99)
        assertTrue(BotCombatActionHandler.wantsToTrain(account, Skill.ATTACK))
        `when`(account.skill(Skill.PRAYER).staticLevel).thenReturn(52)
        assertFalse(BotCombatActionHandler.wantsToTrain(account, Skill.PRAYER))
    }

    @Test fun pureRejectsControlledAndDefensiveMeleeAndLongrangeRanged() {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE_RANGED, Build.DRAGON_PURE))
        val sword = WeaponDefinition.getWeapons()[Weapon.LONGSWORD]!!.typeDef.styles
        val bow = WeaponDefinition.getWeapons()[Weapon.SHORTBOW]!!.typeDef.styles
        assertTrue((sword + bow).flatMap { it.exp }.all { Skill.isCombatSkill(it) })
        assertEquals(setOf(Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE),
            sword.first { it.stance == CombatStance.CONTROLLED }.exp.toSet())
        assertTrue(bow.any { it.stance == CombatStance.DEFENSIVE })
        val melee = BotCombatActionHandler.allowedStyles(account, sword)
        val ranged = BotCombatActionHandler.allowedStyles(account, bow)
        assertTrue(melee.isNotEmpty())
        assertTrue(ranged.isNotEmpty())
        assertTrue((melee + ranged).none { Skill.DEFENCE in it.exp })
        `when`(account.skill(Skill.ATTACK).staticLevel).thenReturn(60)
        assertTrue(BotCombatActionHandler.allowedStyles(account, sword).all { Skill.STRENGTH in it.exp })
    }

    @Test fun equippedStyleIsRevalidatedAfterLevelOrWeaponChanges() {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.TRIBRID, Build.DRAGON_PURE))
        val weapon = account.combat.weapon
        val styles = WeaponDefinition.getWeapons()[Weapon.LONGSWORD]!!.typeDef.styles
        `when`(account.combat.magic.selectedSpell).thenReturn(io.luna.game.model.def.CombatSpellDefinition.NONE)
        `when`(weapon.typeDef.styles).thenReturn(styles)
        `when`(weapon.styleDef).thenReturn(styles.first { it.stance == CombatStance.CONTROLLED })
        doAnswer { call ->
            `when`(weapon.styleDef).thenReturn(styles.first { it.button == call.getArgument<Int>(0) })
            null
        }.`when`(weapon).changeStyle(anyInt())
        val policy = BotCombatActionHandler(account, account.actionHandler)
        assertTrue(policy.prepareTrainingStyle())
        assertEquals(CombatStance.ACCURATE, weapon.styleDef.stance)
        `when`(account.skill(Skill.ATTACK).staticLevel).thenReturn(60)
        assertTrue(policy.prepareTrainingStyle())
        assertEquals(CombatStance.AGGRESSIVE, weapon.styleDef.stance)
        assertTrue(policy.prepareTrainingStyle())
        verify(weapon, times(2)).changeStyle(anyInt())
    }

    @Test fun armourCounterChoosesLowestAvailableDefenceWithinTheSavedStyles() {
        val account = bot(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        `when`(account.personality.intelligence).thenReturn(1.0)
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val melee = BotGearPurpose.MELEE
        val ranged = BotGearPurpose.RANGED
        val magic = BotGearPurpose.MAGIC
        for (bonus in listOf(EquipmentBonus.STAB_DEFENCE,
            EquipmentBonus.SLASH_DEFENCE,
            EquipmentBonus.CRUSH_DEFENCE)) {
            `when`(target.equipment.getBonus(bonus)).thenReturn(100)
        }
        `when`(target.equipment.getBonus(EquipmentBonus.RANGED_DEFENCE)).thenReturn(80)
        `when`(target.equipment.getBonus(EquipmentBonus.MAGIC_DEFENCE)).thenReturn(-20)
        val policy = BotCombatActionHandler(account, account.actionHandler)
        assertEquals(magic, policy.selectPkStyle(target, setOf(melee, ranged, magic), 1.0))
        assertEquals(ranged, policy.selectPkStyle(target, setOf(melee, ranged), 1.0))
        assertNull(policy.selectPkStyle(target, emptySet(), 1.0))
    }

    @Test fun singleStylePkersAndSkillersCannotSwitchOutsideTheirIdentity() {
        val account = bot(BotPkPreference(Purpose.SKILL, Styles.MELEE, Build.GMAUL_PURE))
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val melee = BotGearPurpose.MELEE
        val ranged = BotGearPurpose.RANGED
        val magic = BotGearPurpose.MAGIC
        val policy = BotCombatActionHandler(account, account.actionHandler)
        assertEquals(melee, policy.selectPkStyle(target, setOf(melee, ranged, magic), 0.0))
        assertNull(policy.selectPkStyle(target, setOf(ranged, magic), 0.0))
        val skiller = bot(type = PersonalityTemplateType.SKILLER)
        assertNull(BotCombatActionHandler(skiller, skiller.actionHandler)
            .selectPkStyle(target, setOf(melee, ranged, magic), 0.0))
    }

    @Test fun mistakesChooseOnlyAnotherAvailableSavedStyleAndArmourTiesRetainCurrentStyle() {
        val account = bot(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val policy = BotCombatActionHandler(account, account.actionHandler)
        val available = setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED)
        `when`(account.personality.intelligence).thenReturn(0.0)
        `when`(account.personality.dexterity).thenReturn(0.0)
        `when`(target.equipment.getBonus(EquipmentBonus.RANGED_DEFENCE)).thenReturn(-20)
        assertEquals(BotGearPurpose.RANGED, policy.selectPkStyle(target, available, 1.0))
        assertEquals(BotGearPurpose.MELEE, policy.selectPkStyle(target, available, 0.0))
        `when`(target.equipment.getBonus(EquipmentBonus.RANGED_DEFENCE)).thenReturn(0)
        `when`(account.combat.weapon.isRanged).thenReturn(true)
        assertEquals(BotGearPurpose.RANGED, policy.selectPkStyle(target, available, 1.0))
    }

    @Test fun allSevenCombinationsAdmitOnlyTheirDeclaredDisciplines() {
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        for (styles in Styles.values()) {
            val account = bot(BotPkPreference(Purpose.GENERIC, styles, Build.MAIN))
            val policy = BotCombatActionHandler(account, account.actionHandler)
            for ((purpose, skill) in mapOf(BotGearPurpose.MELEE to Skill.ATTACK,
                BotGearPurpose.RANGED to Skill.RANGED, BotGearPurpose.MAGIC to Skill.MAGIC)) {
                assertEquals(if (styles.uses(skill)) purpose else null,
                    policy.selectPkStyle(target, setOf(purpose), 1.0))
            }
        }
    }

    @Test fun reactionDelayScalesWithinOneToFiveGameTicks() {
        val account = bot()
        val policy = BotCombatActionHandler(account, account.actionHandler)
        `when`(account.personality.intelligence).thenReturn(0.0)
        `when`(account.personality.dexterity).thenReturn(0.0)
        assertEquals(3000, policy.pkSwitchDelay().inWholeMilliseconds)
        `when`(account.personality.intelligence).thenReturn(1.0)
        `when`(account.personality.dexterity).thenReturn(1.0)
        assertEquals(600, policy.pkSwitchDelay().inWholeMilliseconds)
    }

    @Test fun maxedMainCanFightWithoutWantingMoreTraining() {
        val account = bot(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        for (id in 0..6) `when`(account.skill(id).staticLevel).thenReturn(99)
        assertTrue(BotCombatActionHandler.wantedCombatSkills(account).isEmpty())
        assertFalse(BotCombatActionHandler.allowedStyles(account,
            WeaponDefinition.getUnarmed().typeDef.styles).isEmpty())
    }

    @Test fun mainWithoutPkPreferencesKeepsOrdinaryCombatChoices() {
        val account = bot(type = PersonalityTemplateType.JACK_OF_ALL_TRADES)
        assertNull(account.preferences.pk)
        assertEquals((0..6).toSet(), BotCombatActionHandler.wantedCombatSkills(account))
        assertEquals(WeaponDefinition.getUnarmed().typeDef.styles,
            BotCombatActionHandler.allowedStyles(account, WeaponDefinition.getUnarmed().typeDef.styles))
    }
}
