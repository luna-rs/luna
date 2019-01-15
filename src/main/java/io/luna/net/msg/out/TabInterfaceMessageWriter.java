package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.GameTabSet.TabIndex;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays an interface on a sidebar tab.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class TabInterfaceMessageWriter extends GameMessageWriter {

    /**
     * The tab index.
     */
    private final TabIndex index;

    /**
     * The interface identifier.
     */
    private final int id;

    /**
     * Creates a new {@link TabInterfaceMessageWriter}.
     *
     * @param index The tab index.
     * @param id The interface identifier.
     */
    public TabInterfaceMessageWriter(TabIndex index, int id) {
        this.index = index;
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(71);
        msg.putShort(id);
        msg.put(index.getId(), ValueType.ADD);
        return msg;
    }
}
