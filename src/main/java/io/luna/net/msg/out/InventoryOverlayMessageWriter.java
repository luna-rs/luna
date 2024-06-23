package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.InventoryOverlayInterface;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that opens an interface and overlays the inventory with
 * an interface. Use {@link InventoryOverlayInterface} instead of using this packet directly.
 *
 * @author lare96
 */
public final class InventoryOverlayMessageWriter extends GameMessageWriter {

    /**
     * The interface identifier.
     */
    private final int interfaceId;

    /**
     * The inventory overlay interface identifier.
     */
    private final int overlayInterfaceId;

    /**
     * Creates a new {@link InventoryOverlayMessageWriter}.
     *
     * @param interfaceId The interface identifier.
     * @param overlayInterfaceId The inventory overlay interface identifier.
     */
    public InventoryOverlayMessageWriter(int interfaceId, int overlayInterfaceId) {
        this.interfaceId = interfaceId;
        this.overlayInterfaceId = overlayInterfaceId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(128);
        msg.putShort(interfaceId, ValueType.ADD);
        msg.putShort(overlayInterfaceId, ByteOrder.LITTLE, ValueType.ADD);
        return msg;
    }
}
