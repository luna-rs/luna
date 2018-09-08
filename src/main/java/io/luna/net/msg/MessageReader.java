package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An abstraction model representing an inbound message handler.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class MessageReader {

    /**
     * The asynchronous logger.
     */
    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * Intercepts data from the incoming message and returns an {@link Event} describing the data.
     */
    public abstract Event read(Player player, GameMessage msg) throws Exception;

    /**
     * Forwards events to plugins and handles buffer reference counts.
     */
    public final void handleInboundMessage(Player player, GameMessage msg) {
        try {
            Event evt = read(player, msg); /* Retrieve returned event. */
            if (evt != null) {
                player.getPlugins().post(evt); /* Forward it to plugins, if possible. */
            }
        } catch (Exception e) {
            LOGGER.catching(e); /* Disconnect player on error. */
            player.logout();
        } finally {
            ByteMessage payload = msg.getPayload(); /* Finally, release pooled buffer reference. */
            payload.release();

            int refCount = payload.refCnt();
            if (refCount >= 1) {
                // Should never be this high, but I've seen the leak detector report a buffer leak here.
                // So if it occurs again we can debug it here and release it.
                LOGGER.warn("Reference count too high [opcode: {}, ref_count: {}]", msg.getOpcode(), refCount);
                payload.release(refCount);
            }
        }
    }
}
