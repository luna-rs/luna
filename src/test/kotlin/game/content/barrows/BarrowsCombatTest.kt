package game.content.barrows

import engine.combat.prayer.CombatPrayer
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.util.RandomUtils
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.OptionalInt
import kotlin.random.Random

/**
 * Checks proc effects at impact, overkill healing, prayer bypass, and the reconstructed spell selection.
 *
 * @author lare96
 */
class BarrowsCombatTest {
    private val npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val proc = fixed(0)
    private val noProc = fixed(3)

    private fun fixed(value: Int) = object : Random() {
        override fun nextBits(bitCount: Int) = error("Unexpected bit roll")
        override fun nextInt(until: Int) = value
    }

    private fun damage(amount: Int): CombatDamage {
        return CombatDamage::class.java.getDeclaredConstructor(Mob::class.java, Mob::class.java,
            CombatDamageType::class.java, OptionalInt::class.java).apply { isAccessible = true }
            .newInstance(npc, player, CombatDamageType.MELEE, OptionalInt.of(amount))
    }

    private fun apply(brother: BarrowsBrother, amount: Int, random: Random = proc): CombatDamage {
        `when`(npc.isAlive).thenReturn(true)
        `when`(npc.combatDef().attackSpeed).thenReturn(5)
        val attack = BarrowsCombat.attack(npc, player, brother, random)
        return damage(amount).also { it.apply(attack) }
    }

    @Test
    fun guthanHealsAppliedDamageOnlyAndCannotExceedFullHealthOrHealOnZero() {
        `when`(npc.health).thenReturn(50)
        `when`(npc.totalHealth).thenReturn(100)
        `when`(player.health).thenReturn(3)
        val hit = apply(BarrowsBrother.GUTHAN, 24)
        assertEquals(24, hit.rawAmount)
        assertEquals(3, hit.appliedAmount)
        verify(npc).health = 53
        clearInvocations(npc)
        `when`(npc.health).thenReturn(99)
        apply(BarrowsBrother.GUTHAN, 24)
        verify(npc).health = 100
        clearInvocations(npc)
        apply(BarrowsBrother.GUTHAN, 0)
        apply(BarrowsBrother.GUTHAN, 24, noProc)
        verify(npc, never()).health = anyInt()
    }

    @Test
    fun ahrimKarilAndToragApplyCorrectDrainsOnlyOnPositiveProcs() {
        `when`(npc.isAlive).thenReturn(true)
        val hit = mock(CombatDamage::class.java)
        `when`(hit.victim).thenReturn(player)
        `when`(hit.appliedAmount).thenReturn(10)
        val strength = player.skill(Skill.STRENGTH)
        val agility = player.skill(Skill.AGILITY)
        `when`(strength.level).thenReturn(3)
        `when`(agility.level).thenReturn(57)
        `when`(player.runEnergy).thenReturn(55.0)
        BarrowsCombat.effect(npc, BarrowsBrother.AHRIM, hit, proc)
        BarrowsCombat.effect(npc, BarrowsBrother.KARIL, hit, proc)
        BarrowsCombat.effect(npc, BarrowsBrother.TORAG, hit, proc)
        verify(strength).level = 0
        verify(agility).level = 46
        verify(player).runEnergy = 44.0
        clearInvocations(strength, agility, player)
        `when`(hit.appliedAmount).thenReturn(0)
        for (brother in BarrowsBrother.entries) BarrowsCombat.effect(npc, brother, hit, proc)
        verify(strength, never()).level = anyInt()
        verify(agility, never()).level = anyInt()
        verify(player, never()).runEnergy = anyDouble()
    }

    @Test
    fun dharokScalesContinuouslyWithMissingHealth() {
        assertEquals(29, BarrowsCombat.dharokMaximum(29, 100, 100))
        assertEquals(43, BarrowsCombat.dharokMaximum(29, 50, 100))
        assertEquals(57, BarrowsCombat.dharokMaximum(29, 1, 100))
        assertEquals(29, BarrowsCombat.dharokMaximum(29, 120, 100))
        val maxima = (100 downTo 1).map { BarrowsCombat.dharokMaximum(29, it, 100) }
        assertTrue(maxima.zipWithNext().all { (before, after) -> before <= after })
    }

    @Test
    fun veracBypassesAccuracyAndPrayerOnProcButUsesReducedProtectedMaximum() {
        `when`(player.asPlr()).thenReturn(player)
        val prayers = player.combat.prayers
        `when`(npc.combatDef().maximumHit).thenReturn(23)
        `when`(npc.combatDef().attackSpeed).thenReturn(5)
        mockStatic(CombatFormula::class.java).use { formula ->
            formula.`when`<Double> { CombatFormula.calculateHitChance(npc, player, CombatDamageType.MELEE) }
                .thenReturn(0.0)
            mockStatic(RandomUtils::class.java).use { rolls ->
                rolls.`when`<Boolean> { RandomUtils.roll(2.0) }.thenReturn(true)
                rolls.`when`<Int> { RandomUtils.inclusive(anyInt()) }.thenAnswer { it.getArgument<Int>(0) }
                val attack = BarrowsCombat.attack(npc, player, BarrowsBrother.VERAC, proc)
                assertEquals(23, attack.calculateDamage(player).rawAmount)
                `when`(prayers.isActive(CombatPrayer.PROTECT_FROM_MELEE)).thenReturn(true)
                assertEquals(15, attack.calculateDamage(player).rawAmount)
                val ordinary = BarrowsCombat.attack(npc, player, BarrowsBrother.VERAC, noProc)
                assertEquals(0, ordinary.calculateDamage(player).rawAmount)
                rolls.`when`<Boolean> { RandomUtils.roll(0.0) }.thenReturn(true)
                assertEquals(0, ordinary.calculateDamage(player).rawAmount)
            }
        }
    }

    @Test
    fun ahrimUsesThreeCursesAndFireWaveAndDoesNotRecurseAlreadyLoweredSkills() {
        val cases = listOf(CombatSpell.CONFUSE to Skill.ATTACK, CombatSpell.WEAKEN to Skill.STRENGTH,
                           CombatSpell.CURSE to Skill.DEFENCE)
        for ((index, pair) in cases.withIndex()) {
            val (spell, skillId) = pair
            val skill = player.skill(skillId)
            `when`(skill.staticLevel).thenReturn(70)
            `when`(skill.level).thenReturn(70)
            assertEquals(spell, BarrowsCombat.ahrimSpell(player, fixed(index)))
            `when`(skill.level).thenReturn(65)
            assertEquals(CombatSpell.FIRE_WAVE, BarrowsCombat.ahrimSpell(player, fixed(index)))
        }
        for (roll in 3..15) assertEquals(CombatSpell.FIRE_WAVE, BarrowsCombat.ahrimSpell(player, fixed(roll)))
    }
}
