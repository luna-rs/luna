package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that displays text on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WidgetTextMessageWriter extends MessageWriter {

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
        ByteMessage msg = ByteMessage.message(126, MessageType.VAR_SHORT);
        msg.putString(text);
        msg.putShort(id, ByteTransform.A);
        return msg;
    }
}
