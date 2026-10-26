package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} that changes the colour of a text widget.
 * <p>
 * The client only receives 5 bits per channel, so the colour is reduced from 24-bit RGB before it is sent. Pure colours
 * such as {@code 0xFF0000} and {@code 0xFFFFFF} survive this, apart from the lowest 3 bits of each channel.
 *
 * @author Hydrozoa
 */
public final class WidgetColourMessageWriter extends GameMessageWriter {

    /**
     * The text widget id.
     */
    private final int widgetId;

    /**
     * The colour, as 24-bit RGB.
     */
    private final int rgb;

    /**
     * Creates a new {@link WidgetColourMessageWriter}.
     *
     * @param widgetId The text widget id.
     * @param rgb The colour, as 24-bit RGB (for example {@code 0xFF0000} for red).
     */
    public WidgetColourMessageWriter(int widgetId, int rgb) {
        this.widgetId = widgetId;
        this.rgb = rgb;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        int red = (rgb >> 19) & 0x1f;
        int green = (rgb >> 11) & 0x1f;
        int blue = (rgb >> 3) & 0x1f;
        ByteMessage msg = ByteMessage.message(218, buffer);
        msg.putShort(widgetId);
        msg.putShort((red << 10) | (green << 5) | blue, ValueType.ADD);
        return msg;
    }
}
