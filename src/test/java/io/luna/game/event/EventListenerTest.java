package io.luna.game.event;

import io.luna.game.plugin.RuntimeScript;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link EventListener}.
 *
 * @author lare96 <http://github.org/lare96>
 */
final class EventListenerTest {

    private static RuntimeScript script;

    private EventListener<?> eventListener;

    @BeforeAll
    static void initScript() {
        script = new RuntimeScript(null, null);
    }

    @BeforeEach
    void initEventListener() {
        eventListener = new EventListener<>(null, null);
    }

    @Test
    void testSetScript() {
        eventListener.setScript(script);
        assertThrows(IllegalStateException.class, () -> eventListener.setScript(script));
    }
}