package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.GameTabSet.TabIndex;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} that forces a certain tab to open.
 *
 * @author lare96
 */
public final class ForceTabMessageWriter extends GameMessageWriter {

    /**
     * The tab to force open.
     */
    private final TabIndex tab;

    /**
     * Creates a new {@link ForceTabMessageWriter}.
     *
     * @param tab The tab to force open.
     */
    public ForceTabMessageWriter(TabIndex tab) {
        this.tab = tab;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(252);
        msg.put(tab.getIndex(), ValueType.NEGATE);
        return msg;
    }
}
