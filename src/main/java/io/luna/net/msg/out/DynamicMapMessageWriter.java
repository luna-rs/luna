package io.luna.net.msg.out;

import io.luna.game.model.map.builder.DynamicMapChunk;
import io.luna.game.model.map.builder.DynamicMapPalette;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * Loads a {@link DynamicMapPalette} for a player instead of the current region.
 *
 * @author lare96
 */
public final class DynamicMapMessageWriter extends GameMessageWriter {

    /**
     * The palette to send.
     */
    private final DynamicMapPalette palette;

    /**
     * Creates a new {@link DynamicMapMessageWriter}.
     *
     * @param palette The palette to send.
     */
    public DynamicMapMessageWriter(DynamicMapPalette palette) {
        this.palette = palette;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(53, MessageType.VAR_SHORT);
        msg.putShort(player.getPosition().getChunkX(), ValueType.ADD);
        msg.startBitAccess();
        palette.forEach((x, y, z) -> {
            DynamicMapChunk tile = palette.getChunk(x, y, z);
            if (tile != null) {
                msg.putBits(1, 1);
                msg.putBits(26, tile.getX() << 14 |
                        tile.getY() << 3 |
                        tile.getPlane() << 24 |
                        tile.getRotation() << 1);
            } else {
                msg.putBits(1, 0);
            }
        });
        msg.endBitAccess();
        msg.putShort(player.getPosition().getChunkY(), ValueType.ADD);
        return msg;
    }
}
