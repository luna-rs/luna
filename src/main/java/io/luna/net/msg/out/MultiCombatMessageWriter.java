package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays or removes the multi-combat sign.
 *
 * @author lare96
 */
public final class MultiCombatMessageWriter extends GameMessageWriter {

    /**
     * If the multi-combat sign should be displayed.
     */
    private final boolean display;

    /**
     * Creates a new {@link MultiCombatMessageWriter}.
     *
     * @param display If the multi-combat sign should be displayed.
     */
    public MultiCombatMessageWriter(boolean display) {
        this.display = display;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(233, buffer);
        msg.put(display ? 1 : 0);
        return msg;
    }
}