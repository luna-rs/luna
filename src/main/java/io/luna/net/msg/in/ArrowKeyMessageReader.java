package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that is sent when a user presses an arrow key.
 *
 * @author lare96
 */
public final class ArrowKeyMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        int roll = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int yaw = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[ArrowKeyMessageReader] roll: " + roll + ", yaw: " + yaw);
        }
        return NullEvent.INSTANCE;
    }
}
