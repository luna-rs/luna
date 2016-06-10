package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginManager;

/**
 * An inbound message handler that decodes all incoming {@link GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundMessageReader {

    /**
     * Read the {@code msg} and return the {@link Event} that will be forwarded to the {@link PluginManager}, if any. This is
     * only used for the decoding, validation, and basic logic stages of an incoming message. All event type logic should be
     * handled within plugins.
     *
     * @param player The player.
     * @param msg The message to read.
     * @return The {@code Event} that will be forwarded to a plugin, {@code null} if no {@code Event} should be forwarded.
     * @throws Exception If any exceptions are thrown. Will later be caught by the session logger.
     */
    public abstract Event decode(Player player, GameMessage msg) throws Exception;
}
