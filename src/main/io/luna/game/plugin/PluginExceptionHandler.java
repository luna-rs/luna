package io.luna.game.plugin;

import io.netty.util.internal.StringUtil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;

/**
 * A {@link SubscriberExceptionHandler} implementation that handles what happens
 * an exception is thrown by plugins.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginExceptionHandler implements SubscriberExceptionHandler {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(PluginExceptionHandler.class);

    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
        String className = StringUtil.simpleClassName(context.getEvent());
        LOGGER.info("Exception in Plugin (" + className + ")", exception);
    }
}
