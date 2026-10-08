package io.luna.game.model.mob.bot.io;

import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.luna.net.msg.out.UpdateRunEnergyMessageWriter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.luna.game.model.mob.bot.io.BotInputMessageHandler.MAX_MESSAGES_PER_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BotInputMessageHandlerTest {

    private final BotInputMessageHandler handler = new BotInputMessageHandler(null);

    @Test
    void groupsByMessageType() {
        handler.add(message(new GameChatboxMessageWriter("hello")));
        handler.add(message(new UpdateRunEnergyMessageWriter(50)));
        handler.add(message(new GameChatboxMessageWriter("world")));

        assertEquals(2, handler.getReceived().get(GameChatboxMessageWriter.class).size());
        assertEquals(1, handler.getReceived().get(UpdateRunEnergyMessageWriter.class).size());
        assertTrue(handler.getReceived().get(BotMessage.class).isEmpty());
    }

    @Test
    void keepsNewestMessagesPerType() {
        int total = MAX_MESSAGES_PER_TYPE + 50;
        for (int i = 0; i < total; i++) {
            handler.add(message(new GameChatboxMessageWriter("line " + i)));
        }
        handler.add(message(new UpdateRunEnergyMessageWriter(50)));

        List<BotMessage<?>> chat = List.copyOf(handler.getReceived().get(GameChatboxMessageWriter.class));
        assertEquals(MAX_MESSAGES_PER_TYPE, chat.size());
        assertEquals("line 50", text(chat.get(0)));
        assertEquals("line " + (total - 1), text(chat.get(chat.size() - 1)));
        assertEquals(1, handler.getReceived().get(UpdateRunEnergyMessageWriter.class).size());
    }

    private static BotMessage<?> message(GameMessageWriter writer) {
        return new BotMessage<>(writer, Instant.now());
    }

    private static Object text(BotMessage<?> msg) {
        return ((GameChatboxMessageWriter) msg.getMessage()).getMessage();
    }
}
