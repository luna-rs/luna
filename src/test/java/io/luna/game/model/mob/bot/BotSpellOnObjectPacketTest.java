package io.luna.game.model.mob.bot;

import game.skill.magic.chargeOrb.ChargeOrbType;
import io.luna.game.model.Position;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.mob.bot.io.BotClient;
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.in.MagicOnObjectMessageReader;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotSpellOnObjectPacketTest {
    @Test void allOrbSpellPacketsRoundTripThroughThePlayerReader() {
        for (ChargeOrbType type : ChargeOrbType.values()) {
            Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
            BotClient client = mock(BotClient.class);
            when(client.getBot()).thenReturn(bot);
            BotOutputMessageHandler output = new BotOutputMessageHandler(client);
            GameObject target = mock(GameObject.class);
            Position position = new Position(3087, 3571, 3);
            when(target.getId()).thenReturn(type.getObjectId());
            when(target.getPosition()).thenReturn(position);
            when(target.isVisibleTo(bot)).thenReturn(true);
            GameObject wrongId = mock(GameObject.class);
            when(wrongId.getId()).thenReturn(type.getObjectId() + 100);
            GameObject invisible = mock(GameObject.class);
            when(invisible.getId()).thenReturn(type.getObjectId());
            when(invisible.isVisibleTo(bot)).thenReturn(false);
            when(bot.getWorld().getObjects().findAll(new Position(position.getX(), position.getY())))
                    .thenAnswer(invocation -> Stream.of(wrongId, invisible, target));

            assertTrue(output.useSpellOnObject(type.getSpellId(), target));
            ArgumentCaptor<GameMessage> packet = ArgumentCaptor.forClass(GameMessage.class);
            verify(client).queueSimulated(packet.capture());
            GameMessage message = packet.getValue();
            try {
                assertEquals(210, message.getOpcode());
                assertEquals(8, message.getPayload().getBuffer().readableBytes());
                MagicOnObjectMessageReader reader = new MagicOnObjectMessageReader();
                var event = reader.decode(bot, message);
                assertEquals(type.getSpellId(), event.getSpellId());
                assertSame(target, event.getTargetObject());
                assertSame(target, event.target());
                assertTrue(reader.validate(bot, event));
                assertEquals(0, message.getPayload().getBuffer().readableBytes());
            } finally {
                message.getPayload().getBuffer().release();
            }
        }
    }

    @Test void missingOrInvisibleObjectsDoNotQueuePackets() {
        Bot bot = mock(Bot.class);
        BotClient client = mock(BotClient.class);
        when(client.getBot()).thenReturn(bot);
        BotOutputMessageHandler output = new BotOutputMessageHandler(client);
        assertFalse(output.useSpellOnObject(1179, null));
        GameObject target = mock(GameObject.class);
        when(target.isVisibleTo(bot)).thenReturn(false);
        assertFalse(output.useSpellOnObject(1179, target));
        verify(client, never()).queueSimulated(any());
    }

    @Test void objectsThatDisappearAfterQueuingAreRejectedByTheNormalReader() {
        Bot bot = mock(Bot.class, RETURNS_DEEP_STUBS);
        BotClient client = mock(BotClient.class);
        when(client.getBot()).thenReturn(bot);
        BotOutputMessageHandler output = new BotOutputMessageHandler(client);
        GameObject target = mock(GameObject.class);
        when(target.getId()).thenReturn(2151);
        when(target.getPosition()).thenReturn(new Position(3001, 3301));
        when(target.isVisibleTo(bot)).thenReturn(true);
        assertTrue(output.useSpellOnObject(1179, target));
        when(target.isVisibleTo(bot)).thenReturn(false);
        when(bot.getWorld().getObjects().findAll(new Position(3001, 3301)))
                .thenAnswer(invocation -> Stream.of(target));
        ArgumentCaptor<GameMessage> packet = ArgumentCaptor.forClass(GameMessage.class);
        verify(client).queueSimulated(packet.capture());
        GameMessage message = packet.getValue();
        try {
            MagicOnObjectMessageReader reader = new MagicOnObjectMessageReader();
            var event = reader.decode(bot, message);
            assertNull(event.getTargetObject());
            assertFalse(reader.validate(bot, event));
        } finally {
            message.getPayload().getBuffer().release();
        }
    }
}
