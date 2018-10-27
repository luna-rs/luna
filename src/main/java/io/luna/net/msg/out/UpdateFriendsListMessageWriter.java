package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * @author lare96 <http://github.com/lare96>
 */
public final class UpdateFriendsListMessageWriter extends GameMessageWriter {

    private final long name;
    private final boolean online;

    public UpdateFriendsListMessageWriter(long name, boolean online) {
        this.name = name;
        this.online = online;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(50);
        msg.putLong(name);
        msg.put(online ? 10 : 0);
        return msg;
    }
}