package api.bot.action

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.bot.gear.BotItemTracker
import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.bot.brain.BotPkPreference.*
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises persistent cape allegiance, online group eligibility, per-outing solo decisions and visible infighting.
 * Native scene, combat and online state are fixture-controlled; no protocol or player clan membership is added.
 *
 * @author lare96
 */
class PkClanPolicyTest {
    companion object {
        @JvmStatic @BeforeAll fun definitions() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private lateinit var policy: PkClanPolicy
    private lateinit var session: PkBotScript
    private var online = emptyList<Player>()

    @BeforeEach fun setup() {
        online = emptyList()
        bot = clanBot(4315)
        policy = PkClanPolicy(bot)
        session = PkBotScript(bot, 5.minutes, soloClanOuting = true)
        `when`(world.players.stream()).thenAnswer { online.stream() }
    }

    private fun clanBot(cape: Int): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.preferences.pk).thenReturn(BotPkPreference(Purpose.CLAN, Styles.TRIBRID, Build.MAIN, cape))
        `when`(it.isAlive).thenReturn(true)
        doReturn(Position(3100, 3540)).`when`(it).position
        val equipment = Equipment(it).also { gear -> gear.setListeners(BotItemTracker(it)) }
        `when`(it.equipment).thenReturn(equipment)
        equipment.set(Equipment.CAPE, Item(cape))
        `when`(it.combat.target).thenReturn(null)
        `when`(it.personality.confidence).thenReturn(0.0)
        val stack = BotScriptStack(it, mock(BotScriptManager::class.java))
        `when`(it.scriptStack).thenReturn(stack)
    }

    private fun player(cape: Int?): Player = mock(Player::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.isAlive).thenReturn(true)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        doReturn(Position(3101, 3540)).`when`(it).position
        `when`(it.isViewableFrom(bot)).thenReturn(true)
        `when`(it.equipment[Equipment.CAPE]).thenReturn(cape?.let(::Item))
        `when`(it.combat.target).thenReturn(null)
    }

    @Test fun matchingHumanCapesMaskAllegianceButOtherCapesAndBarePlayersRemainTargets() {
        assertFalse(session.wantsToAttack(player(4315)))
        assertTrue(session.wantsToAttack(player(4317)))
        assertTrue(session.wantsToAttack(player(null)))
        bot.equipment.set(Equipment.CAPE, Item(4317))
        assertFalse(policy.wearingCape())
        assertFalse(session.wantsToAttack(player(null)))
    }

    @Test fun freshVisibleFriendlyInitiatorIsTargetedAndRetaliatingVictimRemainsFriendly() {
        val attacker = player(4315)
        val victim = player(4315)
        `when`(attacker.combat.target).thenReturn(victim)
        session.observeAttack(attacker, victim)
        assertTrue(session.wantsToAttack(attacker))
        assertFalse(session.wantsToAttack(victim))
        `when`(victim.combat.target).thenReturn(attacker)
        session.observeAttack(victim, attacker)
        assertFalse(session.wantsToAttack(victim))
        `when`(attacker.combat.target).thenReturn(null)
        session.observeMovement(listOf(attacker, victim))
        assertFalse(session.wantsToAttack(attacker))
    }

    @Test fun unseenStartsAndExistingChasesCannotBrandMatchingCapesAsTraitors() {
        val attacker = player(4315)
        val victim = player(4315)
        `when`(attacker.combat.target).thenReturn(victim)
        `when`(victim.isViewableFrom(bot)).thenReturn(false)
        session.observeAttack(attacker, victim)
        `when`(victim.isViewableFrom(bot)).thenReturn(true)
        session.observeMovement(listOf(attacker, victim))
        assertFalse(session.wantsToAttack(attacker))
    }

    @Test fun onlineMembersNeedTheSameSavedClanAndActivePkScriptButNeedNotBeNearby() {
        val member = clanBot(4315)
        val stranger = clanBot(4317)
        online = listOf(bot, member, stranger, player(4315))
        assertTrue(policy.onlineMembers().isEmpty())
        val active = InventoryProductionFixtures.active(PkBotScript(member, 5.minutes, false))
        member.scriptStack.pushTail(active)
        doReturn(Position(3300, 3890)).`when`(member).position
        assertEquals(listOf(member), policy.onlineMembers())
        assertTrue(PkBotScript(bot, 5.minutes, false).clanCanHunt())
        `when`(member.state).thenReturn(EntityState.INACTIVE)
        assertFalse(PkBotScript(bot, 5.minutes, false).clanCanHunt())
        assertTrue(session.clanCanHunt())
    }

    @Test fun soloChoiceUsesConfidenceOnceAndTheSnapshotRetainsItWithoutChangingIdentity() {
        `when`(bot.personality.confidence).thenReturn(0.69)
        assertFalse(PkClanPolicy.chooseSolo(bot, 0.0))
        `when`(bot.personality.confidence).thenReturn(1.0)
        assertTrue(PkClanPolicy.chooseSolo(bot, 0.249))
        assertFalse(PkClanPolicy.chooseSolo(bot, 0.25))
        val before = bot.preferences.pk
        val data = com.google.gson.JsonObject()
        session.snapshot().save(data)
        val restored = PkBotScript.Companion.PkData().also { it.load(data) }
        assertTrue(restored.soloClanOuting)
        assertSame(before, bot.preferences.pk)
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertFalse(PkClanPolicy.chooseSolo(bot, 0.0))
        assertFalse(session.wantsToAttack(player(null)))
    }

    @Test fun capeReplenishmentOnlyCreatesTheAssignedMissingCapeAndDoesNotDuplicateOwnedOrNotedOnes() {
        assertTrue(policy.ensureCape()) // Already worn.
        assertEquals(0, bot.bank.computeAmountForId(4315))
        bot.equipment.set(Equipment.CAPE, null)
        assertTrue(policy.ensureCape())
        assertTrue(policy.ensureCape())
        assertEquals(1, bot.bank.computeAmountForId(4315))
        assertTrue(bot.bank.remove(Item(4315)))
        // The shared dump fixture omits note definitions; ownership inspection only needs the occupied slot.
        bot.inventory.set(0, Item(4316))
        assertTrue(policy.ensureCape())
        assertEquals(0, bot.bank.computeAmountForId(4315))
        bot.inventory.clear()
        val fallback = Item(995)
        val bank = spy(bot.bank)
        `when`(bot.bank).thenReturn(bank)
        doReturn(false).`when`(bank).hasSpaceFor(any(Item::class.java) ?: fallback)
        assertFalse(policy.ensureCape())
        assertEquals(0, bank.computeAmountForId(4315))
    }

    @Test fun coordinationPrioritizesAnOnlineClansCurrentTargetWithoutMakingHiddenTargetsEligible() {
        val member = clanBot(4315)
        member.scriptStack.pushTail(InventoryProductionFixtures.active(PkBotScript(member, 5.minutes, false)))
        online = listOf(bot, member)
        val enemy = player(null)
        val other = player(4317)
        `when`(member.combat.target).thenReturn(enemy)
        assertEquals(listOf(enemy, other), session.orderTargets(listOf(other, enemy)))
        assertEquals(listOf(other), session.orderTargets(listOf(other)))
    }
}
