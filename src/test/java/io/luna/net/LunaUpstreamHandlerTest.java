package io.luna.net;

import io.luna.net.client.Client;
import io.luna.net.client.GameClient;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Ensures disconnect exceptions reach the client hook and close the channel.
 *
 * @author Codex
 */
final class LunaUpstreamHandlerTest {

    static Stream<Throwable> disconnectExceptions() {
        return Stream.of(ReadTimeoutException.INSTANCE, new IOException(),
                new IOException("An existing connection was forcibly closed by the remote host"));
    }

    @ParameterizedTest
    @MethodSource("disconnectExceptions")
    void handlesExceptionsWithoutMessages(Throwable exception) {
        var handler = new LunaUpstreamHandler();
        var channel = new EmbeddedChannel(handler);
        var client = mock(GameClient.class);
        channel.attr(Client.KEY).set(client);
        try {
            assertDoesNotThrow(() -> handler.exceptionCaught(channel.pipeline().context(handler), exception));
            verify(client).onException(exception);
            verify(client).onInactive();
            assertFalse(channel.isActive());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
