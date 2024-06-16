package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that prints a debug message with the opcode and size.
 *
 * @author lare96
 */
public final class DebugMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[DebugMessageReader] Opcode " + msg.getOpcode() + ", size " + msg.getSize());
        }
        return NullEvent.INSTANCE;
    }
}
