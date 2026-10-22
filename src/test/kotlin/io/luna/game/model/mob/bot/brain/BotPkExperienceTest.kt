package io.luna.game.model.mob.bot.brain

import api.bot.script.InventoryProductionFixtures
import engine.bot.gear.BotGearSet
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

/**
 * Exercises PK build ceilings through the real native XP award method and real skill state.
 *
 * @author lare96
 */
class BotPkExperienceTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() { InventoryProductionFixtures.initialize() }
    }

    private fun skills(build: Build? = Build.DRAGON_PURE, skiller: Boolean = false): Pair<Bot, SkillSet> {
        val bot = InventoryProductionFixtures.bot()
        val personality = mock(BotPersonality::class.java)
        `when`(personality.type).thenReturn(if (skiller) PersonalityTemplateType.SKILLER else
            PersonalityTemplateType.JACK_OF_ALL_TRADES)
        `when`(bot.personality).thenReturn(personality)
        val builder = BotPreference.Builder(mock(BotPersonalityManager::class.java), personality)
            .addSkill(Skill.WOODCUTTING).addGear(BotGearSet.values().first())
        if (build != null) builder.setPk(BotPkPreference(Purpose.GENERIC, Styles.MAGIC, build))
        val preferences = builder.build()
        `when`(bot.preferences).thenReturn(preferences)
        preferences.setBot(bot)
        val skills = SkillSet(bot)
        `when`(bot.skills).thenReturn(skills)
        `when`(bot.skill(anyInt())).thenAnswer { skills.getSkill(it.getArgument(0)) }
        return bot to skills
    }

    @Test fun oversizedAwardStopsExactlyAtAttackTargetAndSubsequentAwardsDoNothing() {
        val (bot, skills) = skills()
        val attack = skills.getSkill(Skill.ATTACK)
        val cap = SkillSet.experienceForLevel(60).toDouble()
        attack.experience = cap - 0.5
        clearInvocations(bot.plugins)
        attack.addExperience(1_000_000.0)
        assertEquals(cap, attack.experience)
        assertEquals(60, attack.staticLevel)
        val event = ArgumentCaptor.forClass(SkillChangeEvent::class.java)
        verify(bot.plugins).post(event.capture())
        assertTrue(event.value.isLevelUp)
        clearInvocations(bot.plugins)
        attack.addExperience(1_000.0)
        assertEquals(cap, attack.experience)
        verifyNoInteractions(bot.plugins)
    }

    @Test fun fractionalAwardsBelowTheTargetAreUnchanged() {
        val (_, skills) = skills()
        val attack = skills.getSkill(Skill.ATTACK)
        attack.experience = 100.25
        attack.addExperience(0.125)
        assertEquals(100.375, attack.experience)
        attack.addExperience(0.0)
        attack.addExperience(-100.0)
        attack.addExperience(Double.NaN)
        assertEquals(100.375, attack.experience)
    }

    @Test fun existingLevelsAboveTheSavedTargetArePreserved() {
        val (_, skills) = skills()
        val attack = skills.getSkill(Skill.ATTACK)
        val existing = SkillSet.experienceForLevel(61).toDouble() + 12.25
        attack.experience = existing
        attack.addExperience(1_000.0)
        assertEquals(existing, attack.experience)
        assertEquals(61, attack.staticLevel)
    }

    @Test fun oversizedPrayerRewardStopsAtTheSavedPrayerTarget() {
        val (_, skills) = skills(Build.GMAUL_PURE)
        val prayer = skills.getSkill(Skill.PRAYER)
        prayer.addExperience(100_000.0)
        assertEquals(SkillSet.experienceForLevel(31).toDouble(), prayer.experience)
        assertEquals(31, prayer.staticLevel)
    }

    @Test fun controlledDamageAwardsAreCappedIndependentlyWithoutLosingOtherSkillsXp() {
        val (_, skills) = skills()
        val attack = skills.getSkill(Skill.ATTACK)
        val defence = skills.getSkill(Skill.DEFENCE)
        val strength = skills.getSkill(Skill.STRENGTH)
        attack.experience = SkillSet.experienceForLevel(60) - 0.25
        attack.addExperience(1.33)
        strength.addExperience(1.33)
        defence.addExperience(1.33)
        skills.getSkill(Skill.HITPOINTS).addExperience(1.33)
        assertEquals(SkillSet.experienceForLevel(60).toDouble(), attack.experience)
        assertEquals(0.0, defence.experience)
        assertEquals(1, defence.staticLevel)
        assertEquals(1.33, strength.experience)
        assertEquals(1301.33, skills.getSkill(Skill.HITPOINTS).experience)
    }

    @Test fun skillersCannotGainAnyCombatXpButCanTrainOtherSkills() {
        val (_, skills) = skills(skiller = true)
        for (id in 0..6) {
            val skill = skills.getSkill(id)
            val before = skill.experience
            skill.addExperience(1_000_000.0)
            assertEquals(before, skill.experience)
        }
        skills.getSkill(Skill.WOODCUTTING).addExperience(125.0)
        assertEquals(125.0, skills.getSkill(Skill.WOODCUTTING).experience)
    }

    @Test fun level99TargetsAndNonCombatSkillsRetainNormalXpGains() {
        val (_, skills) = skills(Build.MAIN)
        for (id in listOf(Skill.ATTACK, Skill.RANGED, Skill.MAGIC, Skill.WOODCUTTING)) {
            val skill = skills.getSkill(id)
            val before = SkillSet.experienceForLevel(99).toDouble()
            skill.experience = before
            skill.addExperience(500.0)
            assertEquals(before + 500.0, skill.experience)
        }
    }

    @Test fun botsWithoutPkProfilesKeepTheirExistingOneTimesXpRate() {
        val (_, skills) = skills(null)
        skills.getSkill(Skill.ATTACK).addExperience(75.25)
        assertEquals(75.25, skills.getSkill(Skill.ATTACK).experience)
    }

    @Test fun humanXpStillUsesTheConfiguredMultiplier() {
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val skills = SkillSet(player)
        `when`(player.skills).thenReturn(skills)
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().experienceMultiplier()).thenReturn(10.0)
        mockStatic(Luna::class.java, CALLS_REAL_METHODS).use { luna ->
            luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
            skills.getSkill(Skill.ATTACK).addExperience(12.5)
        }
        assertEquals(125.0, skills.getSkill(Skill.ATTACK).experience)
    }
}
