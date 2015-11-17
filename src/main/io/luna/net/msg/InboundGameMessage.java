package io.luna.net.msg;

import io.luna.game.model.mobile.Player;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import plugin.Plugin;

import static com.google.common.base.Preconditions.checkState;

/**
 * An inbound logic handler for {@link GameMessage}s that also contains a static array of message sizes and logic handlers.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundGameMessage {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(InboundGameMessage.class);

    /**
     * An array representation of the sizes of all the inbound messages.
     */
    public static final int SIZES[] = new int[257];

    /**
     * An array representation of all of the inbound {@link GameMessage} handlers.
     */
    public static final InboundGameMessage[] HANDLERS = new InboundGameMessage[257];

    /**
     * Read the {@code msg} and return the {@code Object} event that will be forwarded to the {@link PluginManager}. This is
     * only used for the decoding and validation stages of an incoming message. All logic should be handled within {@link
     * Plugin}s.
     *
     * @param player The player.
     * @param msg The message to read.
     * @return The {@code Object} that will be forwarded to a {@link Plugin}.
     * @throws Exception If any exceptions are thrown. Will later be caught by the session logger.
     */
    public abstract Object readMessage(Player player, GameMessage msg) throws Exception;

    /**
     * Adds a handler to the static array of logic handlers.
     *
     * @param opcode The opcode of the logic handler.
     * @param clazz The {@link Class} Object of the logic handler.
     */
    public static void addInboundMessage(int opcode, int size, Class<? extends InboundGameMessage> clazz) {
        checkState(HANDLERS[opcode] == null, "Opcode [" + opcode + "] already taken");

        try {
            HANDLERS[opcode] = clazz.newInstance();
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }
}
