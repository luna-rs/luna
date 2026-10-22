package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import game.bot.scripts.combat.CombatBotScript
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.SkullIcon
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.brain.BotReflex
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.interact.InteractionPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies visible encounter evidence, conservative chase detection and kind GENERIC intervention.
 * Native attack application drives fresh-start observation; combat legality remains fixture-controlled.
 *
 * @author lare96
 */
class PkCombatObservationsTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private lateinit var attacker: Player
    private lateinit var victim: Player
    private lateinit var observations: PkCombatObservations
    private lateinit var session: PkBotScript
    private var cycle = 0L

    @BeforeEach fun setup() {
        cycle = 0
        bot = InventoryProductionFixtures.bot()
        `when`(bot.isAlive).thenReturn(true)
        `when`(bot.position).thenReturn(Position(3100, 3540))
        `when`(bot.cycles).thenAnswer { cycle }
        `when`(bot.combatLevel).thenReturn(50)
        `when`(bot.combat.isAttackable).thenReturn(true)
        `when`(bot.personality.isKind).thenReturn(true)
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.GENERIC, Styles.TRIBRID, Build.MAIN))
        val stack = BotScriptStack(bot, mock(BotScriptManager::class.java))
        `when`(bot.scriptStack).thenReturn(stack)
        attacker = player(3102, 3540)
        victim = player(3105, 3540)
        `when`(attacker.combat.target).thenReturn(victim)
        `when`(world.locator.findViewablePlayers(attacker)).thenReturn(setOf(bot))
        `when`(world.locator.findViewablePlayers(bot)).thenReturn(setOf(attacker, victim))
        for (player in listOf(attacker, victim)) {
            `when`(bot.combat.checkMultiCombat(player)).thenReturn(true)
            `when`(bot.controllers.checkCombat(player)).thenReturn(true)
        }
        observations = PkCombatObservations(bot)
        session = InventoryProductionFixtures.active(PkBotScript(bot, 5.minutes))
        bot.scriptStack.pushTail(session)
    }

    private fun player(x: Int, y: Int): Player = mock(Player::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.position).thenReturn(Position(x, y))
        `when`(it.isAlive).thenReturn(true)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.world).thenReturn(world)
        `when`(it.isViewableFrom(bot)).thenReturn(true)
        `when`(it.combatLevel).thenReturn(50)
        `when`(it.combat.isAttackable).thenReturn(true)
        `when`(it.skullIcon).thenReturn(SkullIcon.NONE)
    }

    private fun moveChase() {
        doReturn(Position(3103, 3540)).`when`(attacker).position
        doReturn(Position(3106, 3540)).`when`(victim).position
    }

    private fun nativeAttack(cancelled: Boolean = false): CombatAttack<Player> {
        val damage = mock(CombatDamage::class.java)
        return object : CombatAttack<Player>(attacker, victim, mock(InteractionPolicy::class.java), 4) {
            override fun calculateDamage(other: Mob): CombatDamage = damage
            override fun onAttack(damage: CombatDamage): CombatDamage? = if (cancelled) null else damage
            override fun attack() {}
        }
    }

    @Test fun kindGenericChallengesIdleSkullsButDoesNotGuessWhoStartedAnExistingFight() {
        assertFalse(session.wantsToAttack(attacker))
        `when`(attacker.skullIcon).thenReturn(SkullIcon.WHITE)
        assertFalse(session.wantsToAttack(attacker))
        `when`(attacker.combat.target).thenReturn(null)
        assertTrue(session.wantsToAttack(attacker))
        `when`(attacker.combat.inCombat()).thenReturn(true)
        assertFalse(session.wantsToAttack(attacker))
        assertFalse(session.protectObservedPlayer())
        assertSame(session, bot.scriptStack.current())
    }

    @Test fun freshNativeAttackAllowsDefenseAgainstAnUnskulledWitnessedAggressor() {
        assertFalse(session.wantsToAttack(attacker))
        nativeAttack().apply()
        `when`(attacker.combat.inCombat()).thenReturn(true)
        `when`(victim.combat.inCombat()).thenReturn(true)
        assertTrue(session.wantsToAttack(attacker))
        assertTrue(session.allowsPursuit(attacker))
        assertFalse(session.wantsToAttack(victim))
    }

    @Test fun cancelledAttackDoesNotCreateWitnessEvidence() {
        nativeAttack(cancelled = true).apply()
        assertFalse(session.wantsToAttack(attacker))
    }

    @Test fun anAttackWithinAnAlreadyOngoingFightDoesNotEstablishAnAggressor() {
        `when`(victim.combat.inCombat()).thenReturn(true)
        nativeAttack().apply()
        assertFalse(session.wantsToAttack(attacker))
        assertFalse(session.wantsToAttack(victim))
    }

    @Test fun attackingSomeoneWhoWasFightingAnNpcStillHasAWitnessablePlayerInitiator() {
        `when`(victim.combat.inCombat()).thenReturn(true)
        `when`(victim.combat.lastCombatWith).thenReturn(mock(Npc::class.java))
        nativeAttack().apply()
        assertTrue(session.wantsToAttack(attacker))
    }

    @Test fun bothPlayersMustBeVisibleAtTheAttackStart() {
        `when`(victim.isViewableFrom(bot)).thenReturn(false)
        nativeAttack().apply()
        `when`(victim.isViewableFrom(bot)).thenReturn(true)
        assertFalse(session.wantsToAttack(attacker))
        observations.attackStarted(attacker, victim)
        assertTrue(observations.canProtectAgainst(attacker))
        `when`(attacker.isViewableFrom(bot)).thenReturn(false)
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun twoConsecutiveVisibleMovementSamplesCanEstablishAChase() {
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
        cycle++
        moveChase()
        observations.observe(listOf(attacker, victim))
        assertTrue(observations.canProtectAgainst(attacker))
        // A subsequent standstill does not erase the witnessed pursuit while the same victim is still targeted.
        cycle++
        observations.observe(listOf(attacker, victim))
        assertTrue(observations.canProtectAgainst(attacker))
    }

    @Test fun mutualCombatMovementDoesNotRevealWhoStartedTheFight() {
        `when`(victim.combat.target).thenReturn(attacker)
        observations.observe(listOf(attacker, victim))
        cycle++
        moveChase()
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun aWitnessedChaseVictimFightingBackIsNotBlamedAsTheAggressor() {
        observations.observe(listOf(attacker, victim))
        cycle++
        moveChase()
        observations.observe(listOf(attacker, victim))
        observations.attackStarted(victim, attacker)
        assertTrue(observations.canProtectAgainst(attacker))
        assertFalse(observations.canProtectAgainst(victim))
    }

    @Test fun sharedMovementWithoutAPursuitIsNotAChase() {
        `when`(attacker.combat.target).thenReturn(null)
        observations.observe(listOf(attacker, victim))
        cycle++
        moveChase()
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun skippedCyclesAndSameCycleResamplingCannotEstablishAChase() {
        observations.observe(listOf(attacker, victim))
        moveChase()
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
        cycle += 2
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun teleportsOrPlayersFirstAppearingMidChaseDoNotSupplyContinuousMovementEvidence() {
        observations.observe(listOf(attacker))
        cycle++
        moveChase()
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
        cycle++
        doReturn(Position(3108, 3540)).`when`(attacker).position
        doReturn(Position(3111, 3540)).`when`(victim).position
        observations.observe(listOf(attacker, victim))
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun evidenceEndsWhenTheVictimDisappearsOrTheAttackerStopsThreateningThem() {
        observations.attackStarted(attacker, victim)
        assertTrue(observations.canProtectAgainst(attacker))
        `when`(victim.isViewableFrom(bot)).thenReturn(false)
        observations.observe(listOf(attacker))
        `when`(victim.isViewableFrom(bot)).thenReturn(true)
        assertFalse(observations.canProtectAgainst(attacker))
        observations.attackStarted(attacker, victim)
        `when`(attacker.combat.target).thenReturn(null)
        assertFalse(observations.canProtectAgainst(attacker))
        observations.clear()
        `when`(attacker.combat.target).thenReturn(victim)
        assertFalse(observations.canProtectAgainst(attacker))
    }

    @Test fun perTickObservationInterruptsPatrolForProtectionButRespectsSingleCombatOwnership() {
        nativeAttack().apply()
        `when`(bot.combat.checkMultiCombat(attacker)).thenReturn(false)
        BotReflex().process(bot)
        assertSame(session, bot.scriptStack.current())
        `when`(bot.combat.checkMultiCombat(attacker)).thenReturn(true)
        cycle++
        BotReflex().process(bot)
        assertTrue(bot.scriptStack.current() is CombatBotScript)
        assertTrue(session.isPaused())
        verify(bot.navigator).cancel()
        verify(bot.combat, never()).attack(attacker)
    }

    @Test fun nonKindGenericAndClanInitiationWaitForTheirOwnPolicyWithoutAffectingValueHunting() {
        `when`(attacker.skullIcon).thenReturn(SkullIcon.WHITE)
        `when`(bot.personality.isKind).thenReturn(false)
        assertFalse(session.wantsToAttack(attacker))
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.CLAN, Styles.TRIBRID, Build.MAIN))
        assertFalse(session.wantsToAttack(attacker))
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(Purpose.VALUE, Styles.TRIBRID, Build.MAIN))
        `when`(attacker.skullIcon).thenReturn(SkullIcon.NONE)
        assertTrue(session.wantsToAttack(attacker))
    }

    @Test fun kindGenericCanDefendItselfAgainstAnUnskulledAttacker() {
        `when`(attacker.combat.target).thenReturn(bot)
        assertTrue(session.allowsPursuit(attacker))
        `when`(attacker.combat.target).thenReturn(victim)
        assertFalse(session.allowsPursuit(attacker))
    }
}
