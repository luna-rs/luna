package io.luna.net.msg;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

import static org.apache.logging.log4j.util.Unbox.box;

/**
 * An abstraction model listener that posts events after reading data from decoded game messages.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class GameMessageReader {

    /**
     * The asynchronous logger.
     */
    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * The opcode.
     */
    private final int opcode;

    /**
     * The size.
     */
    private final int size;

    /**
     * Creates a new {@link GameMessageReader}.
     */
    public GameMessageReader() {
        // These values are injected using reflection.
        opcode = 0;
        size = 0;
    }

    /**
     * Reads a decoded game message and posts an {@link Event} containing the decoded data. A return
     * value of {@code null} indicates that no event needs to be posted.
     *
     * @param player The player.
     * @param msg The decoded message.
     * @throws Exception If any errors occur.
     */
    public abstract Event read(Player player, GameMessage msg) throws Exception;

    /**
     * Handles a decoded game message and posts its returned {@link Event}.
     *
     * @param player The player.
     * @param msg The decoded game message.
     */
    public final void postEvent(Player player, GameMessage msg) {
        try {

            // Retrieve event and post it, if possible.
            Event event = read(player, msg);
            if (event != null) {
                player.getPlugins().post(event);
            }
        } catch (Exception e) {

            // Disconnect on exception.
            LOGGER.error(new ParameterizedMessage("{} failed in reading game message.", player, e));
            player.logout();
        } finally {

            // Release pooled buffer.
            ByteMessage payload = msg.getPayload();
            payload.release();

            // Netty has shown at (seemingly random) times that there is a buffer leak occurring here.
            // I'm not sure how that's possible yet, so hopefully this debug message will help determine if there
            // is and where it's coming from.
            int refCount = payload.refCnt();
            if (refCount >= 1) {
                LOGGER.warn("Buffer reference count too high [opcode: {}, ref_count: {}]",
                        box(msg.getOpcode()), box(refCount));
                payload.release(refCount);
            }
        }
    }

    /**
     * @return The opcode.
     */
    public final int getOpcode() {
        return opcode;
    }

    /**
     * @return The size.
     */
    public final int getSize() {
        return size;
    }
}
