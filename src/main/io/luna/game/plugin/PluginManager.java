package io.luna.game.plugin;

import com.google.common.eventbus.EventBus;

/**
 * An {@link com.google.common.eventbus.EventBus} implementation that manages
 * the publish-subscribe-style communication between plugins (also known as
 * subscribers) and events.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager extends EventBus {

    /**
     * Creates a new {@link io.luna.game.plugin.PluginManager}.
     */
    public PluginManager() {
        super(new PluginExceptionHandler());
        register(new PluginDeadHandler());
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * Not all plugins registered using this function will be submitted to the
     * underlying event bus.
     */
    @Override
    public void register(Object object) {
        // XXX: Not all plugins need to be submitted to the event bus, some need
        // to be submitted elsewhere such as combat strategies,
        // minigames, etc. and that can be done through here.
        super.register(object);
    }
}
