package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that updates the user's friends' list.
 *
 * @author lare96
 */
public final class UpdateFriendsListMessageWriter extends GameMessageWriter {

    /**
     * The hash of the name to update.
     */
    private final long name;

    /**
     * The online status to update with.
     */
    private final boolean online;

    /**
     * Creates a new {@link UpdateFriendsListMessageWriter}.
     *
     * @param name The hash of the name to update.
     * @param online The online status to update with.
     */
    public UpdateFriendsListMessageWriter(long name, boolean online) {
        this.name = name;
        this.online = online;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(78);
        msg.putLong(name);
        msg.put(online ? 10 : 0);
        return msg;
    }
}