package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.luna.util.StringUtils;

public final class UpdateWelcomeMessageWriter extends GameMessageWriter {
    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(76); // last password change
        msg.putShort(720, ByteOrder.LITTLE);

        // junk
        msg.putShort(0);
        msg.putShort(0);
        msg.putShort(0);

        msg.putShort(9, ByteOrder.LITTLE); //
        msg.putShort(0, ValueType.ADD); // Unread message count in message centre.
        msg.putShort(7, ValueType.ADD); // How many days since last login.
        msg.putShort(0); // Member credit days remaining.
        msg.putInt(StringUtils.packIpAddress(player.getLastIp()), ByteOrder.LITTLE);

        msg.putShort(0, ByteOrder.LITTLE, ValueType.ADD); // recovery questions set time

        msg.put(0, ValueType.ADD);  // junk
        return msg;
    }
}
