package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that displays an interface on a sidebar tab.
 *
 * @author lare96
 */
public final class TabInterfaceMessageWriter extends GameMessageWriter {

    /**
     * The tab index.
     */
    private final TabIndex tab;

    /**
     * The interface identifier.
     */
    private final int id;

    /**
     * Creates a new {@link TabInterfaceMessageWriter}.
     *
     * @param tab The tab index.
     * @param id The interface identifier.
     */
    public TabInterfaceMessageWriter(TabIndex tab, int id) {
        this.tab = tab;
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(10, buffer);
        msg.put(tab.getIndex(), ValueType.SUBTRACT);
        msg.putShort(id, ValueType.ADD);
        return msg;
    }
}
