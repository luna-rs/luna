package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that displays text on a widget.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendWidgetTextMessage extends OutboundGameMessage {

    /**
     * The text to display on the widget.
     */
    private final String text;

    /**
     * The identifier for the widget that the text will be displayed on.
     */
    private final int id;

    /**
     * Creates a new {@link SendWidgetTextMessage}.
     *
     * @param text The text to display on the widget.
     * @param id The identifier for the widget that the text will be displayed on.
     */
    public SendWidgetTextMessage(String text, int id) {
        this.text = text;
        this.id = id;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        ByteMessage msg = ByteMessage.message(126, MessageType.VARIABLE_SHORT);
        msg.putString(text);
        msg.putShort(id, ByteTransform.A);
        return msg;
    }
}
