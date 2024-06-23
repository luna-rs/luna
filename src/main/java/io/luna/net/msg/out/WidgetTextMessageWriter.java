package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays text on a widget.
 *
 * @author lare96
 */
public final class WidgetTextMessageWriter extends GameMessageWriter {

    /**
     * The text.
     */
    private final String text;

    /**
     * The widget identifier.
     */
    private final int id;

    /**
     * Creates a new {@link WidgetTextMessageWriter}.
     *
     * @param text The text.
     * @param id The widget identifier.
     */
    public WidgetTextMessageWriter(String text, int id) {
        this.text = text;
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(232, MessageType.VAR_SHORT);
        msg.putShort(id, ByteOrder.LITTLE, ValueType.ADD);
        msg.putString(text);
        return msg;
    }
}
