package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.OutboundMessageWriter;

/**
 * @author lare96 <http://github.org/lare96>
 */
public class InventoryOverlayMessageWriter extends OutboundMessageWriter {

    /**
     * The identifier of the interface to open.
     */
    private final int interfaceId;

    /**
     * The identifier of the interface to overlay the inventory space.
     */
    private final int overlayInterfaceId;

    public InventoryOverlayMessageWriter(int interfaceId, int overlayInterfaceId) {
        this.interfaceId = interfaceId;
        this.overlayInterfaceId = overlayInterfaceId;
    }

    /**
     * Builds a {@link ByteMessage} containing the data for this message.
     *
     * @param player The player.
     * @return The buffer containing the data.
     */
    @Override
    public ByteMessage write(Player player) {
        return null;
    }
}
