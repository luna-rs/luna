package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginManager;
import io.luna.net.codec.ByteMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An inbound message handler that decodes all inbound {@link GameMessage}s.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundMessageReader {

    /**
     * The asynchronous logger.
     */
    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * Read the {@code msg} and return the {@link Event} that will be forwarded to the {@link PluginManager}, if any. This is
     * only used for the validation and basic logic stages of an incoming message. All event type logic should be handled
     * within plugins.
     *
     * @param player The player.
     * @param msg The message to read.
     * @return The {@code Event} that will be forwarded to a plugin, {@code null} if no {@code Event} should be forwarded.
     * @throws Exception If any exceptions are thrown. Will later be caught by the session logger.
     */
    public abstract Event read(Player player, GameMessage msg) throws Exception;

    /**
     * Reads the payload from the inbound {@code msg}, and notifies all listeners of any events constructed from the
     * operation. Return the buffer used to hold the payload back to it's buffer pool, if applicable.
     *
     * @param player The player.
     * @param msg The message to read.
     */
    public final void handleInboundMessage(Player player, GameMessage msg) {
        try {
            Event evt = read(player, msg);

            if (evt != null) {
                player.getPlugins().post(evt, player);
            }
        } catch (Exception e) {
            LOGGER.catching(e);
            player.logout();
        } finally {
            ByteMessage payload = msg.getPayload();

            if (payload.refCnt() > 0) {
                payload.release();
            }
        }
    }
}
