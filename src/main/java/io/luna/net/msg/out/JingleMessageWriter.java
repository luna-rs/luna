package io.luna.net.msg.out;

import game.player.Jingles;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessageWriter;

public class JingleMessageWriter extends GameMessageWriter {

    /**
     * The jingle to play.
     */
    private final Jingles jingle;

    /**
     *
     * @param jingle
     */
    public JingleMessageWriter(Jingles jingle) {
        this.jingle = jingle;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(249);
        msg.putShort(jingle.getId(), ByteOrder.LITTLE);
        // todo need a putMedium
        msg.put(-1);
        msg.putShort(-1);
        return msg;
    }
}
