package io.luna.game.plugin;

import io.luna.LunaContext;
import plugin.events.AsyncPluginEvent;
import plugin.events.DeadEventHandler;
import plugin.events.GamePluginEvent;

import com.google.common.eventbus.EventBus;

/**
 * An {@link EventBus} implementation that manages the publish-subscribe-style
 * communication between plugins (also known as subscribers) and events.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginManager extends EventBus {

    /**
     * The context this {@code PluginManager} is under.
     */
    private final LunaContext context;

    /**
     * Creates a new {@link PluginManager}.
     */
    public PluginManager(LunaContext context) {
        super(new PluginExceptionHandler());
        this.context = context;
        register(new DeadEventHandler());
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>
     * {@link AsyncPluginEvent}s are posted asynchronously while all other
     * events are posted synchronously.
     */
    @Override
    public void post(Object event) {
        if (event instanceof AsyncPluginEvent) {
            context.getService().execute(() -> super.post(event));
        } else if (event instanceof GamePluginEvent) {
            GamePluginEvent evt = (GamePluginEvent) event;

            if (evt.context() == null) {
                evt.context_$eq(context);
            }
            super.post(evt);
        }
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
