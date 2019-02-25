package io.luna.game.event;


import io.luna.game.plugin.Script;
import org.junit.Test;

/**
 * A test that ensures that functions within {@link EventListener} are working correctly.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EventListenerTest {

    /**
     * Ensures that a script cannot be set twice.
     */
    @Test(expected = IllegalStateException.class)
    public void testSetScript() {
        var eventListener = new EventListener(null, null);
        eventListener.setScript(new Script(null, null, null));
        eventListener.setScript(new Script(null, null, null));
    }

    /**
     * Ensures that a script cannot retrieved when there is no script set.
     */
    @Test(expected = IllegalStateException.class)
    public void testGetScript() {
        var eventListener = new EventListener(null, null);
        eventListener.getScript();
    }
}