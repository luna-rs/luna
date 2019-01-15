package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

import java.util.OptionalInt;

/**
 * A {@link GameMessageWriter} implementation that writes a Player or NPC head model on a widget.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class WidgetMobModelMessageWriter extends GameMessageWriter {

    /**
     * The widget identifier.
     */
    private final int widgetId;

    /**
     * The NPC identifier.
     */
    private final OptionalInt npcId;

    /**
     * Creates a new {@link WidgetMobModelMessageWriter} that will display an NPC head model on a widget.
     *
     * @param widgetId The widget identifier.
     * @param npcId The NPC identifier.
     */
    public WidgetMobModelMessageWriter(int widgetId, int npcId) {
        this.widgetId = widgetId;
        this.npcId = OptionalInt.of(npcId);
    }

    /**
     * Creates a new {@link WidgetMobModelMessageWriter} that will display the Player's head model on a widget.
     *
     * @param widgetId The widget identifier.
     */
    public WidgetMobModelMessageWriter(int widgetId) {
        this.widgetId = widgetId;
        this.npcId = OptionalInt.empty();
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg;
        if (npcId.isPresent()) {
            msg = ByteMessage.message(75);
            msg.putShort(npcId.getAsInt(), ValueType.ADD, ByteOrder.LITTLE);
            msg.putShort(widgetId, ValueType.ADD, ByteOrder.LITTLE);
        } else {
            msg = ByteMessage.message(185);
            msg.putShort(widgetId, ValueType.ADD, ByteOrder.LITTLE);
        }
        return msg;
    }
}