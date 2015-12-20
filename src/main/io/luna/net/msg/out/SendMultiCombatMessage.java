package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that either displays or removes the multi-combat sign.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendMultiCombatMessage extends OutboundGameMessage {

    /**
     * If the multi-combat sign should be displayed or removed.
     */
    private final boolean display;

    /**
     * Creates a new {@link SendMultiCombatMessage}.
     *
     * @param display If the multi-combat sign should be displayed or removed.
     */
    public SendMultiCombatMessage(boolean display) {
        this.display = display;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.create();
        msg.message(61);
        msg.put(display ? 1 : 0);
        return msg;
    }
}