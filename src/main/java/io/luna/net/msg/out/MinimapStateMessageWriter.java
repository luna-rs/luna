package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that controls the state of the minimap.
 *
 * @author lare96
 */
public final class MinimapStateMessageWriter extends GameMessageWriter {

    /**
     * Represents all possible minimap states.
     */
    public enum State {

        /**
         * The minimap functions as normal.
         */
        NORMAL(0),

        /**
         * The minimap looks normal but becomes unresponsive to clicks.
         */
        DISABLED(1),

        /**
         * The minimap is covered (blacked out) and unresponsive to clicks.
         */
        CONCEALED(2);

        /**
         * The client identifier value.
         */
        private final int clientId;

        /**
         * Creates a new {@link State}.
         *
         * @param clientId
         */
        State(int clientId) {
            this.clientId = clientId;
        }
    }

    /**
     * The state to set the minimap to.
     */
    private final State state;

    /**
     * Creates a new {@link MinimapStateMessageWriter}.
     *
     * @param state The state to set the minimap to.
     */
    public MinimapStateMessageWriter(State state) {
        this.state = state;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(156);
        return msg.put(state.clientId);
    }
}
