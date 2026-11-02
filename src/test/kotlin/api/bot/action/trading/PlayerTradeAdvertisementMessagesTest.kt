package api.bot.action.trading

import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.bot.Bot
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.StringUtils
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.util.Optional

/**
 * Checks live-session privacy and packed counteroffer delivery without starting a world or network.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementMessagesTest {

    private val world = mock(World::class.java)
    private val bot = mock(Bot::class.java)
    private val recipient = mock(Player::class.java)
    private val senderHash = 101L
    private val recipientHash = 202L
    private val text = "i can offer 30 gp each for 100 flax"
    private val senderIgnores = HashSet<Long>()
    private val recipientIgnores = HashSet<Long>()
    private val senderFriends = linkedSetOf(303L)
    private val recipientFriends = linkedSetOf(404L)

    @BeforeEach fun initialize() {
        `when`(bot.isBot).thenReturn(true)
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        `when`(bot.usernameHash).thenReturn(senderHash)
        `when`(bot.username).thenReturn("merchant")
        `when`(bot.rights).thenReturn(PlayerRights.PLAYER)
        `when`(bot.world).thenReturn(world)
        `when`(bot.ignores).thenReturn(senderIgnores)
        `when`(bot.friends).thenReturn(senderFriends)
        `when`(world.getPlayer(senderHash)).thenReturn(Optional.of<Player>(bot))
        human(recipient)
    }

    private fun human(player: Player): Player {
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.usernameHash).thenReturn(recipientHash)
        `when`(player.username).thenReturn("seller")
        `when`(player.world).thenReturn(world)
        `when`(player.ignores).thenReturn(recipientIgnores)
        `when`(player.friends).thenReturn(recipientFriends)
        `when`(player.privacyOptions).thenReturn(PlayerPrivacy())
        `when`(player.rights).thenReturn(PlayerRights.PLAYER)
        `when`(player.newPrivateMessageId()).thenReturn(42)
        `when`(world.getPlayer(recipientHash)).thenReturn(Optional.of(player))
        return player
    }

    private fun assertNoMessage(player: Player = recipient) {
        verify(player, never()).queue(any(GameMessageWriter::class.java))
        verify(bot, never()).queue(any(GameMessageWriter::class.java))
    }

    private fun queuedMessage(): PrivateChatMessageWriter {
        val captured = ArgumentCaptor.forClass(PrivateChatMessageWriter::class.java)
        verify(recipient, times(1)).queue(captured.capture())
        return captured.value
    }

    private fun packetRights(writer: PrivateChatMessageWriter): Int {
        val buffer = Unpooled.buffer()
        try {
            writer.write(recipient, buffer)
            buffer.readLong()
            buffer.readInt()
            return buffer.readUnsignedByte().toInt()
        } finally {
            if (buffer.refCnt() > 0) buffer.release(buffer.refCnt())
        }
    }

    @Test fun publicPrivateChatQueuesOneStandardMessageWithCopiedPriceText() {
        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))
        val captured = ArgumentCaptor.forClass(PrivateChatMessageWriter::class.java)
        verify(recipient, times(1)).queue(captured.capture())
        val buffer = Unpooled.buffer()
        try {
            val packet = captured.value.write(recipient, buffer)
            assertEquals(135, packet.opcode)
            assertEquals(senderHash, buffer.readLong())
            assertEquals(42, buffer.readInt())
            assertEquals(PlayerRights.PLAYER.clientValue, buffer.readUnsignedByte().toInt())
            val bytes = ByteArray(buffer.readableBytes())
            buffer.readBytes(bytes)
            assertEquals(text, StringUtils.unpackText(bytes).trimEnd())
        } finally {
            if (buffer.refCnt() > 0) buffer.release(buffer.refCnt())
        }
    }

    @Test fun friendsOnlyOffAndHiddenPrivateChatAlwaysDenyCounteroffers() {
        for (mode in listOf(PrivacyMode.FRIENDS, PrivacyMode.OFF, PrivacyMode.HIDE)) {
            `when`(recipient.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, mode, PrivacyMode.ON))

            assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text), mode.name)
        }
        assertNoMessage()
    }

    @Test fun beingFriendsDoesNotBypassThePublicPrivateChatRequirement() {
        recipientFriends += senderHash
        senderFriends += recipientHash
        `when`(recipient.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, PrivacyMode.FRIENDS, PrivacyMode.ON))

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
        assertEquals(setOf(303L, recipientHash), senderFriends)
        assertEquals(setOf(404L, senderHash), recipientFriends)
    }

    @Test fun senderIgnorePreventsDelivery() {
        senderIgnores += recipientHash

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun recipientIgnorePreventsDelivery() {
        recipientIgnores += senderHash

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun inactiveOrUnregisteredSendersCannotSend() {
        for (state in listOf(EntityState.NEW, EntityState.INACTIVE)) {
            `when`(bot.state).thenReturn(state)
            assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text), state.name)
        }
        assertNoMessage()
    }

    @Test fun inactiveOrUnregisteredRecipientsCannotReceive() {
        for (state in listOf(EntityState.NEW, EntityState.INACTIVE)) {
            `when`(recipient.state).thenReturn(state)
            assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text), state.name)
        }
        assertNoMessage()
    }

    @Test fun senderMustStillBeTheWorldsCurrentSession() {
        val replacement = mock(Player::class.java)
        `when`(replacement.usernameHash).thenReturn(senderHash)
        `when`(world.getPlayer(senderHash)).thenReturn(Optional.of(replacement))

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun recipientMustStillBeTheWorldsCurrentSession() {
        val replacement = mock(Player::class.java)
        `when`(replacement.usernameHash).thenReturn(recipientHash)
        `when`(world.getPlayer(recipientHash)).thenReturn(Optional.of(replacement))

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun aMissingCurrentSenderCannotSend() {
        `when`(world.getPlayer(senderHash)).thenReturn(Optional.empty())

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun aMissingCurrentRecipientCannotReceive() {
        `when`(world.getPlayer(recipientHash)).thenReturn(Optional.empty())

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun connectedHumanWrappersCannotSendAutonomousCounteroffers() {
        `when`(bot.isBot).thenReturn(false)

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun autonomousBotsAreNotCounterofferRecipients() {
        val other = human(mock(Bot::class.java)) as Bot
        `when`(other.isBot).thenReturn(true)

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, other, text))

        assertNoMessage(other)
    }

    @Test fun connectedHumanWrappersMayReceiveCounteroffers() {
        val connected = human(mock(Bot::class.java)) as Bot
        `when`(connected.isBot).thenReturn(false)

        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, connected, text))

        verify(connected, times(1)).queue(any(PrivateChatMessageWriter::class.java))
    }

    @Test fun selfAndSameAccountMessagesAreRejected() {
        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, bot, text))
        `when`(recipient.usernameHash).thenReturn(senderHash)
        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertNoMessage()
    }

    @Test fun blankAndOverlongMessagesAreNotSentOrSilentlyTruncated() {
        for (invalid in listOf("", " ", "\t\n", "a".repeat(81))) {
            assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, invalid))
        }
        assertNoMessage()
    }

    @Test fun exactlyEightyCharactersAreAllowed() {
        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, "a".repeat(80)))

        verify(recipient, times(1)).queue(any(PrivateChatMessageWriter::class.java))
    }

    @Test fun privacyIsRecheckedForEachImmediateDelivery() {
        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))
        `when`(recipient.privacyOptions).thenReturn(PlayerPrivacy(PrivacyMode.ON, PrivacyMode.OFF, PrivacyMode.ON))

        assertFalse(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        verify(recipient, times(1)).queue(any(PrivateChatMessageWriter::class.java))
    }

    @Test fun sendingDoesNotReadOrMutateFriendships() {
        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        verify(bot, never()).friends
        verify(recipient, never()).friends
        assertEquals(setOf(303L), senderFriends)
        assertEquals(setOf(404L), recipientFriends)
    }

    @Test fun regularCounteroffersDoNotInheritTheRecipientsDeveloperCrown() {
        `when`(recipient.rights).thenReturn(PlayerRights.DEVELOPER)

        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertEquals(0, packetRights(queuedMessage()))
    }

    @Test fun counteroffersUseTheSendersModeratorCrownForARegularRecipient() {
        `when`(bot.rights).thenReturn(PlayerRights.MODERATOR)

        assertTrue(PlayerTradeAdvertisementMessages.counteroffer(bot, recipient, text))

        assertEquals(1, packetRights(queuedMessage()))
    }

    @Test fun legacyPrivateMessageConstructionDefaultsToNoSenderCrown() {
        `when`(recipient.rights).thenReturn(PlayerRights.DEVELOPER)
        val writer = PrivateChatMessageWriter(senderHash, byteArrayOf())

        assertEquals(0, packetRights(writer))
    }
}
