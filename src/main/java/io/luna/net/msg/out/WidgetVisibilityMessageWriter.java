package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that sets a widget to be hidden until hovered over.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetVisibilityMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * If the widget should be hidden.
     */
    private final boolean hide;

    /**
     * Creates a new {@link WidgetVisibilityMessageWriter}.
     *
     * @param id The widget identifier.
     * @param hide If the widget should be hidden.
     */
    public WidgetVisibilityMessageWriter(int id, boolean hide) {
        this.id = id;
        this.hide = hide;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(171);
        msg.put(hide ? 1 : 0);
        msg.putShort(id);
        return msg;
    }
}
