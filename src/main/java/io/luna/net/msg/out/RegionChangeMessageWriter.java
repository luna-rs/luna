package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.MessageWriter;

/**
 * An {@link MessageWriter} implementation that sends a message containing the current region.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionChangeMessageWriter extends MessageWriter {

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(73);
        msg.putShort(player.getPosition().getRegionX() + 6, ByteTransform.A);
        msg.putShort(player.getPosition().getRegionY() + 6);
        return msg;
    }
}
