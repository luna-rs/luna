package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that updates the loading status for the friends list.
 *
 * @author lare96 
 */
public final class FriendsListStatusMessageWriter extends GameMessageWriter {

    /**
     * The status code.
     * <p>
     * {@code 0} for Loading.
     * <p>
     * {@code 1} for Connecting.
     * <p>
     * {@code 2} for Loaded.
     */
    private final int status;

    /**
     * Creates a new {@link FriendsListStatusMessageWriter}.
     *
     * @param status The status code.
     */
    public FriendsListStatusMessageWriter(int status) {
        this.status = status;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(251);
        msg.put(status);
        return msg;
    }
}