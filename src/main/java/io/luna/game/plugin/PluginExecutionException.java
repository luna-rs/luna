package io.luna.game.plugin;

import io.luna.game.event.EventListener;

/**
 * @author lare96 <http://github.com/lare96>
 */
public class PluginExecutionException extends Exception {

    private final EventListener<?> eventListener;

    public PluginExecutionException(EventListener<?> eventListener, Exception cause) {
        super(eventListener + " failed to execute.", cause);
        this.eventListener = eventListener;
    }

    public EventListener<?> getEventListener() {
        return eventListener;
    }
}