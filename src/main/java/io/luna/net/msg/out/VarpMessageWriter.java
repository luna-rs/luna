package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.varp.Varp;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sets the default and current value of a varP (variable player) in
 * the client. Should only be used by {@link Player#sendVarp(Varp)}.
 *
 * @author lare96
 */
public final class VarpMessageWriter extends GameMessageWriter {

    /**
     * The varP to send.
     */
    private final Varp varp;

    /**
     * Creates a new {@link VarpMessageWriter}.
     *
     * @param varpId The identifier.
     * @param state The value.
     */
    public VarpMessageWriter(Varp varp) {
        this.varp = varp;
    }

    @Override
    public ByteMessage write(Player player) {
        boolean isLargeVarp = varp.getValue() > Byte.MAX_VALUE;
        ByteMessage msg;
        if(isLargeVarp) {
            msg = ByteMessage.message(115);
            msg.putInt(varp.getValue(), ByteOrder.INVERSE_MIDDLE);
            msg.putShort(varp.getId(), ByteOrder.LITTLE);
        } else {
            msg = ByteMessage.message(182);
            msg.putShort(varp.getId() & 0xff, ValueType.ADD);
            msg.put(varp.getValue(), ValueType.SUBTRACT);
        }
        return msg;
    }
}