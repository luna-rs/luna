package api.bot.action

import game.bot.scripts.combat.PkBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
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
}
