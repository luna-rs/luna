package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.GameTabSet.TabIndex;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that makes a {@link TabIndex} flash. The tab must be open for this
 * packet to work (use {@link ForceTabMessageWriter}).
 *
 * @author lare96
 */
public final class FlashTabMessageWriter extends GameMessageWriter {

    /**
     * The tab to flash.
     */
    private final TabIndex tab;

    /**
     * Creates a new {@link FlashTabMessageWriter}.
     *
     * @param tab The tab to flash.
     */
    public FlashTabMessageWriter(TabIndex tab) {
        this.tab = tab;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(24);
        msg.put(tab.getIndex(), ValueType.SUBTRACT);
        return msg;
    }
}
