package io.luna.game.plugin;

import io.netty.util.internal.StringUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.Subscribe;

/**
 * A listener that handles what happens to {@linkplain DeadEvent dead events}
 * for the {@link com.google.common.eventbus.EventBus} instances.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class DeadPluginEventHandler {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(DeadPluginEventHandler.class);

    @Subscribe
    public void handleDeadEvent(DeadEvent evt) {
        LOGGER.info("No subscribers found for " + StringUtil.simpleClassName(evt.getEvent()) + ".");
    }
}