package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerContextMenuOption;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays or hides player context menu options.
 *
 * @author lare96
 */
public final class ContextMenuOptionMessageWriter extends GameMessageWriter {

    /**
     * The menu option.
     */
    private final PlayerContextMenuOption option;

    /**
     * Creates a new {@link ContextMenuOptionMessageWriter}.
     *
     * @param option The interaction.
     */
    public ContextMenuOptionMessageWriter(PlayerContextMenuOption option) {
        this.option = option;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(157, MessageType.VAR, buffer);
        msg.put(option.getIndex(), ValueType.NEGATE);
        msg.putString(option.getName());
        msg.put(option.isPinned() ? 1 : 0, ValueType.ADD);
        return msg;
    }
}