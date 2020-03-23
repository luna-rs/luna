package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * @author lare96 <http://github.com/lare96>
 */
public class AddGlobalGroundItemMessageWriter extends GameMessageWriter {
// TODO utilize
    // 84 = change ground item amount
    // 215 = make item global for other players, not yourself. this i

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(215);
        msg.putShort(4151, ValueType.ADD);
        msg.put(0, ValueType.SUBTRACT);
        msg.putShort(player.getIndex(), ValueType.ADD);
        msg.putShort(1);
        return msg;
    }
}