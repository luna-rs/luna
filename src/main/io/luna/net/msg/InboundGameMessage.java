package io.luna.net.msg;

import static com.google.common.base.Preconditions.checkState;
import io.luna.game.model.mobile.Player;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An inbound logic handler for {@link GameMessage}s that also contains a static
 * array of message sizes and logic handlers.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InboundGameMessage {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(InboundGameMessage.class);

    /**
     * An array representation of all of the sizes of incoming messages.
     */
    public static final int SIZES[] = { 0, 0, 0, 1, -1, 0, 0, 0, 0, 0, // 0
            0, 0, 0, 0, 8, 0, 6, 2, 2, 0, // 10
            0, 2, 0, 6, 0, 12, 0, 0, 0, 0, // 20
            0, 0, 0, 0, 0, 8, 4, 0, 0, 2, // 30
            2, 6, 0, 6, 0, -1, 0, 0, 0, 0, // 40
            0, 0, 0, 12, 0, 0, 0, 0, 8, 0, // 50
            0, 8, 0, 0, 0, 0, 0, 0, 0, 0, // 60
            6, 0, 2, 2, 8, 6, 0, -1, 0, 6, // 70
            0, 0, 0, 0, 0, 1, 4, 6, 0, 0, // 80
            0, 0, 0, 0, 0, 3, 0, 0, -1, 0, // 90
            0, 13, 0, -1, 0, 0, 0, 0, 0, 0,// 100
            0, 0, 0, 0, 0, 0, 0, 6, 0, 0, // 110
            1, 0, 6, 0, 0, 0, -1, 0, 2, 6, // 120
            0, 4, 6, 8, 0, 6, 0, 0, 0, 2, // 130
            0, 0, 0, 0, 0, 6, 0, 0, 0, 0, // 140
            0, 0, 1, 2, 0, 2, 6, 0, 0, 0, // 150
            0, 0, 0, 0, -1, -1, 0, 0, 0, 0,// 160
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, // 170
            0, 8, 0, 3, 0, 2, 0, 0, 8, 1, // 180
            0, 0, 12, 0, 0, 0, 0, 0, 0, 0, // 190
            2, 0, 0, 0, 0, 0, 0, 0, 4, 0, // 200
            4, 0, 0, 0, 7, 8, 0, 0, 10, 0, // 210
            0, 0, 0, 0, 0, 0, -1, 0, 6, 0, // 220
            1, 0, 0, 0, 6, 0, 6, 8, 1, 0, // 230
            0, 4, 0, 0, 0, 0, -1, 0, -1, 4,// 240
            0, 0, 6, 6, 0, 0, 0 // 250
    };

    /**
     * An array representation of all of the inbound {@link GameMessage}
     * handlers.
     */
    public static final InboundGameMessage[] HANDLERS = new InboundGameMessage[257];

    /**
     * Add all of the inbound {@link GameMessage} handlers.
     */
    static {}

    /**
     * Read the {@code msg} and handle logic for it.
     * 
     * @param player The player.
     * @param msg The message to read.
     * @throws Exception If any exceptions are thrown. Will later be caught by
     *         the session logger.
     */
    public abstract void readMessage(Player player, GameMessage msg) throws Exception;

    /**
     * Adds a handler to the static array of logic handlers.
     * 
     * @param opcode The opcode of the logic handler.
     * @param clazz The {@link Class} Object of the logic handler.
     */
    private static void addHandler(int opcode, Class<? extends InboundGameMessage> clazz) {
        checkState(HANDLERS[opcode] == null, "Opcode [" + opcode + "] already taken");

        try {
            HANDLERS[opcode] = clazz.newInstance();
        } catch (Exception e) {
            LOGGER.catching(Level.WARN, e);
        }
    }
}
