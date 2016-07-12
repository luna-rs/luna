package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.MessageWriter;

/**
 * A {@link MessageWriter} implementation that opens an interface and overlays the inventory with an interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class InventoryOverlayMessageWriter extends MessageWriter {

    /**
     * The identifier of the interface to open.
     */
    private final int interfaceId;

    /**
     * The identifier of the interface to overlay the inventory space.
     */
    private final int overlayInterfaceId;

    /**
     * Creates a new {@link InventoryOverlayMessageWriter}.
     *
     * @param interfaceId The identifier of the interface to open.
     * @param overlayInterfaceId The identifier of the interface to overlay the inventory space.
     */
    public InventoryOverlayMessageWriter(int interfaceId, int overlayInterfaceId) {
        this.interfaceId = interfaceId;
        this.overlayInterfaceId = overlayInterfaceId;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(248);
        msg.putShort(interfaceId, ByteTransform.A);
        msg.putShort(overlayInterfaceId);
        return msg;
    }
}
