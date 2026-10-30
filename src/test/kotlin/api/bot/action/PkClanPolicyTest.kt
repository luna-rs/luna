package api.bot.action

import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import engine.controllers.WildernessLocatableController.wildernessLevel
import api.bot.webwalk.plan.BankAccess
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Checks universal allegiance while retaining purpose-specific departure and patrol behavior.
 *
 * @author lare96
 */
class PkClanPolicyTest {
    private fun bot(purpose: BotPkPreference.Purpose): Bot {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.username).thenReturn("member")
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(purpose,
            BotPkPreference.Styles.MELEE, BotPkPreference.Build.MAIN))
        `when`(bot.world.botManager.clanRegistry.capeFor("member")).thenReturn(4315)
        return bot
    }

    @Test fun everyPurposeHasAllegianceButOnlyClanOutingsNeedCompany() {
        for (purpose in BotPkPreference.Purpose.values()) {
            val bot = bot(purpose)
            val clan = PkClanPolicy(bot)
            assertTrue(clan.enabled)
            assertEquals(4315, clan.cape)
            assertEquals(purpose == BotPkPreference.Purpose.CLAN, clan.clanOuting)
            `when`(bot.world.players.stream()).thenAnswer { java.util.stream.Stream.empty<Player>() }
            val script = PkBotScript(bot, 30.minutes, soloClanOuting = false)
            assertEquals(purpose != BotPkPreference.Purpose.CLAN, script.clanCanHunt())
        }
    }

    @Test fun matchingCapeProtectsAgainstInitiationForEveryPurpose() {
        for (purpose in BotPkPreference.Purpose.values()) {
            val bot = bot(purpose)
            val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
            `when`(target.equipment[Equipment.CAPE]).thenReturn(Item(4315))
            assertTrue(PkClanPolicy(bot).apparentAlly(target))
            assertFalse(PkBotScript(bot, 30.minutes, false).wantsToAttack(target))
            `when`(bot.equipment[Equipment.CAPE]).thenReturn(Item(4317))
            assertFalse(PkClanPolicy(bot).wearingCape())
            `when`(bot.equipment[Equipment.CAPE]).thenReturn(Item(4315))
            assertTrue(PkClanPolicy(bot).wearingCape())
        }
    }

    @Test fun nonClanPurposeMemberCountsAsOnlineCompanyButNotAsGroupPatrolLeader() {
        val bot = bot(BotPkPreference.Purpose.CLAN)
        val member = bot(BotPkPreference.Purpose.VALUE)
        `when`(member.username).thenReturn("other")
        `when`(bot.world.botManager.clanRegistry.capeFor("other")).thenReturn(4315)
        `when`(member.state).thenReturn(EntityState.ACTIVE)
        `when`(member.isAlive).thenReturn(true)
        `when`(member.scriptStack.containsActive(PkBotScript::class.java)).thenReturn(true)
        `when`(bot.world.players.stream()).thenAnswer { java.util.stream.Stream.of<Player>(bot, member) }
        assertEquals(listOf(member), PkClanPolicy(bot).onlineMembers(activePkOnly = false))
        assertTrue(PkClanPolicy(bot).onlineMembers().isEmpty())
    }

    @Test fun skillerAndMissingIdentityCannotJoinThroughWornCape() {
        val bot = bot(BotPkPreference.Purpose.GENERIC)
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertFalse(PkClanPolicy(bot).enabled)
        assertNull(PkClanPolicy(bot).cape)
        `when`(bot.preferences.isSkiller).thenReturn(false)
        `when`(bot.preferences.pk).thenReturn(null)
        assertFalse(PkClanPolicy(bot).enabled)
        assertNull(PkClanPolicy(bot).cape)
    }

    @Test fun atLeastNinetyPercentOfOutingsAlwaysChooseGroupingEvenAtMaximumConfidence() {
        val bot = bot(BotPkPreference.Purpose.CLAN)
        for (confidence in listOf(0.0, 0.69, 0.7, 0.9, 1.0, 2.0, Double.NaN)) {
            `when`(bot.personality.confidence).thenReturn(confidence)
            val solo = (0 until 1000).count { PkClanPolicy.chooseSolo(bot, it / 1000.0) }
            assertTrue(solo <= 100, "confidence=$confidence selected $solo solo outings")
        }
        `when`(bot.personality.confidence).thenReturn(1.0)
        assertTrue(PkClanPolicy.chooseSolo(bot, 0.099))
        assertFalse(PkClanPolicy.chooseSolo(bot, 0.1))
    }

    /**
     * A three-member clan with controllable positions and real outing policies, without starting a server.
     *
     * @author lare96
     */
    private inner class Patrol {
        val leader = member("leader", 1, Position(3000, 3800))
        val follower = member("follower", 2, Position(3020, 3800))
        val third = member("third", 3, Position(3002, 3800))
        val bots = listOf(leader, follower, third)
        val scripts = bots.associateWith { PkBotScript(it, 30.minutes, false) }

        init {
            for (bot in bots) {
                `when`(bot.asPlr()).thenReturn(bot)
                `when`(bot.attributes()).thenReturn(AttributeMap())
                bot.wildernessLevel = 36
                for (other in bots) {
                    `when`(bot.world.botManager.clanRegistry.capeFor(other.username)).thenReturn(4315)
                }
                `when`(bot.world.players.stream()).thenAnswer { bots.stream().map { it as Player } }
                `when`(bot.scriptStack.containsActive(PkBotScript::class.java)).thenReturn(true)
                `when`(bot.scriptStack.findActive(PkBotScript::class.java)).thenReturn(java.util.Optional.of(scripts.getValue(bot)))
                `when`(bot.scriptStack.current()).thenReturn(scripts.getValue(bot))
                `when`(bot.walking.isEmpty).thenReturn(true)
            }
        }

        private fun member(name: String, hash: Long, position: Position): Bot = bot(BotPkPreference.Purpose.CLAN).also {
            `when`(it.username).thenReturn(name)
            `when`(it.usernameHash).thenReturn(hash)
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.isAlive).thenReturn(true)
            `when`(it.position).thenReturn(position)
            `when`(it.equipment[Equipment.CAPE]).thenReturn(Item(4315))
        }
    }

    @Test fun distantOnlineClanmatesDoNotPermitSoloInitiationButNearbyPatrolMembersDo() {
        val patrol = Patrol()
        val script = patrol.scripts.getValue(patrol.follower)
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.position).thenReturn(Position(3021, 3800))
        assertTrue(script.clanCanHunt())
        assertFalse(script.wantsToAttack(target))
        `when`(patrol.follower.position).thenReturn(Position(3005, 3800))
        assertTrue(script.wantsToAttack(target))
    }

    @Test fun followersRallyOnTheirLeadersPositionAndLeadersWaitForStragglers() = runBlocking<Unit> {
        val patrol = Patrol()
        val follower = patrol.scripts.getValue(patrol.follower)
        val leader = patrol.scripts.getValue(patrol.leader)
        assertEquals(patrol.leader.position, follower.selectPatrolDestination())
        leader.travelToNewArea()
        verifyNoInteractions(patrol.leader.actionHandler)
        `when`(patrol.follower.actionHandler.webWalkTo(patrol.leader.position,
            radius = PkClanPolicy.FOLLOW_RADIUS, bank = BankAccess.NONE, fallback = false)).thenReturn(false)
        follower.travelToNewArea()
        verify(patrol.follower.actionHandler).webWalkTo(patrol.leader.position,
            radius = PkClanPolicy.FOLLOW_RADIUS, bank = BankAccess.NONE, fallback = false)
        `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
        clearInvocations(patrol.follower.actionHandler)
        follower.travelToNewArea()
        verifyNoInteractions(patrol.follower.actionHandler)
    }

    @Test fun formationChangesInterruptSuspendedPatrolTravel() = runBlocking<Unit> {
        val patrol = Patrol()
        `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
        val destination = PkBotScript.PkArea.DEMONIC_RUINS.anchors.first()
        // Restore an unfinished route so the leader must retain it after stopping for a straggler.
        val restored = PkBotScript(patrol.leader, PkBotScript.Companion.PkData().apply {
            duration = 30.minutes
            patrolDestination = destination
        })
        `when`(patrol.leader.scriptStack.current()).thenReturn(restored)
        `when`(patrol.leader.scriptStack.findActive(PkBotScript::class.java)).thenReturn(java.util.Optional.of(restored))
        `when`(patrol.leader.actionHandler.webWalkTo(destination, radius = 2,
            bank = BankAccess.NONE, fallback = false)).thenAnswer {
            `when`(patrol.follower.position).thenReturn(Position(3030, 3800))
            restored.maintainClanFormation()
            false
        }
        restored.travelToNewArea()
        verify(patrol.leader.navigator).cancel()
        verify(patrol.leader.walking).clear()
        assertEquals(destination, restored.patrolDestination)
    }

    @Test fun followersStopTheirSuspendedWalkWhenTheyCatchUp() = runBlocking<Unit> {
        val patrol = Patrol()
        val follower = patrol.scripts.getValue(patrol.follower)
        val destination = patrol.leader.position
        `when`(patrol.follower.actionHandler.webWalkTo(destination, radius = PkClanPolicy.FOLLOW_RADIUS,
            bank = BankAccess.NONE, fallback = false)).thenAnswer {
            `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
            follower.maintainClanFormation()
            false
        }
        follower.travelToNewArea()
        verify(patrol.follower.navigator).cancel()
        verify(patrol.follower.walking).clear()
    }

    @Test fun losingTheLeaderInterruptsAnOldRallyRoute() = runBlocking<Unit> {
        val patrol = Patrol()
        val follower = patrol.scripts.getValue(patrol.follower)
        val destination = patrol.leader.position
        `when`(patrol.follower.actionHandler.webWalkTo(destination, radius = PkClanPolicy.FOLLOW_RADIUS,
            bank = BankAccess.NONE, fallback = false)).thenAnswer {
            `when`(patrol.leader.state).thenReturn(EntityState.INACTIVE)
            patrol.scripts.getValue(patrol.third).expireAt = 0
            follower.maintainClanFormation()
            false
        }
        follower.travelToNewArea()
        verify(patrol.follower.navigator).cancel()
        assertNotEquals(destination, follower.selectPatrolDestination())
    }

    @Test fun anActualRouteFailureAllowsTheClanToChooseAnotherPatrol() = runBlocking<Unit> {
        val patrol = Patrol()
        `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
        val destination = PkBotScript.PkArea.DEMONIC_RUINS.anchors.first()
        val leader = PkBotScript(patrol.leader, PkBotScript.Companion.PkData().apply {
            duration = 30.minutes
            patrolDestination = destination
        })
        `when`(patrol.leader.actionHandler.webWalkTo(destination, radius = 2,
            bank = BankAccess.NONE, fallback = false)).thenReturn(false)
        leader.travelToNewArea()
        assertNull(leader.patrolDestination)
    }

    @Test fun loneGroupOutingsHaveAStableClanRallyAndDoNotStartFightsThere() {
        val patrol = Patrol()
        patrol.scripts.getValue(patrol.leader).expireAt = 0
        patrol.scripts.getValue(patrol.third).expireAt = 0
        val follower = patrol.scripts.getValue(patrol.follower)
        val rally = follower.selectPatrolDestination()
        repeat(20) { assertEquals(rally, follower.selectPatrolDestination()) }
        `when`(patrol.follower.position).thenReturn(rally)
        val target = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(target.position).thenReturn(rally)
        assertFalse(follower.wantsToAttack(target))
    }

    @Test fun offlineDeadUnarmedAndOtherPlaneMembersCannotProvideFormationSupport() {
        val patrol = Patrol()
        patrol.scripts.getValue(patrol.third).expireAt = 0
        `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
        val policy = PkClanPolicy(patrol.follower)
        assertTrue(policy.inFormation())
        `when`(patrol.leader.isAlive).thenReturn(false)
        assertFalse(policy.inFormation())
        `when`(patrol.leader.isAlive).thenReturn(true)
        `when`(patrol.leader.state).thenReturn(EntityState.INACTIVE)
        assertFalse(policy.inFormation())
        `when`(patrol.leader.state).thenReturn(EntityState.ACTIVE)
        `when`(patrol.leader.equipment[Equipment.CAPE]).thenReturn(null)
        assertFalse(policy.inFormation())
        `when`(patrol.leader.equipment[Equipment.CAPE]).thenReturn(Item(4315))
        `when`(patrol.leader.position).thenReturn(Position(3000, 3800, 1))
        assertFalse(policy.inFormation())
    }

    @Test fun soloExpiredAndDifferentClanSessionsCannotLeadOrSupplyFormationSupport() {
        val patrol = Patrol()
        `when`(patrol.follower.position).thenReturn(Position(3003, 3800))
        val policy = PkClanPolicy(patrol.follower)
        assertSame(patrol.leader, policy.patrolLeader())
        patrol.scripts.getValue(patrol.leader).expireAt = 0
        assertSame(patrol.follower, policy.patrolLeader())
        val solo = PkBotScript(patrol.third, 30.minutes, true)
        `when`(patrol.third.scriptStack.findActive(PkBotScript::class.java)).thenReturn(java.util.Optional.of(solo))
        assertFalse(policy.inFormation())
        `when`(patrol.third.scriptStack.findActive(PkBotScript::class.java))
            .thenReturn(java.util.Optional.of(patrol.scripts.getValue(patrol.third)))
        `when`(patrol.follower.world.botManager.clanRegistry.capeFor(patrol.third.username)).thenReturn(4317)
        assertFalse(policy.inFormation())
    }

    @Test fun clanMembersConcentrateOnTheMostSupportedLegalTarget() {
        val patrol = Patrol()
        val target = mock(Player::class.java)
        val other = mock(Player::class.java)
        `when`(patrol.leader.combat.target).thenReturn(target)
        `when`(patrol.third.combat.target).thenReturn(target)
        val script = patrol.scripts.getValue(patrol.follower)
        assertEquals(listOf(target, other), script.orderTargets(listOf(other, target)))
    }
}
