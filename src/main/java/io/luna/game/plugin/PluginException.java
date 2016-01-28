package io.luna.game.plugin;

import plugin.Plugin;

/**
 * A {@link RuntimeException} implementation wraps thrown {@link Exception}s from {@link Plugin} code.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class PluginException extends RuntimeException {

    /**
     * Creates a new {@link PluginException}.
     *
     * @param exception The exception to wrap within this exception.
     */
    public PluginException(Exception exception) {
        super(exception);
    }
}
