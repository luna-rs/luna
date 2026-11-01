package io.luna.net.msg.out;

import io.luna.game.model.Position;
import io.luna.game.model.map.DynamicMap;
import io.luna.game.model.map.builder.DynamicMapChunk;
import io.luna.game.model.map.builder.DynamicMapPalette;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * Sends the native revision 377 constructed-map packet. Palette slots are shifted into the player's
 * current 13-by-13 scene window, so region updates near an instance edge retain the same world mapping.
 *
 * @author lare96
 */
public final class DynamicMapMessageWriter extends GameMessageWriter {

    /**
     * Immutable layout snapshot retained until this queued message is encoded.
     */
    private final DynamicMapPalette palette;

    /**
     * Absolute origin of palette slot zero.
     */
    private final Position origin;

    /**
     * Position used by player updating to establish the new scene origin.
     */
    private final Position position;

    /**
     * Captures the active map without retaining mutable allocation state.
     *
     * @param map The allocated map to display.
     * @param position The player's region-update position.
     */
    public DynamicMapMessageWriter(DynamicMap map, Position position) {
        palette = map.getPalette();
        origin = map.getAssignedSpace().abs();
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage message = ByteMessage.message(53, MessageType.VAR_SHORT, buffer);
        message.putShort(position.getChunkX(), ValueType.ADD);
        message.startBitAccess();
        for (int z = 0; z < 4; z++) {
            for (int x = 0; x < 13; x++) {
                for (int y = 0; y < 13; y++) {
                    int slotX = position.getChunkX() - 6 + x - origin.getChunkX();
                    int slotY = position.getChunkY() - 6 + y - origin.getChunkY();
                    DynamicMapChunk chunk = slotX >= 0 && slotX < 13 && slotY >= 0 && slotY < 13 ?
                            palette.getChunk(slotX, slotY, z) : null;
                    message.putBits(1, chunk == null ? 0 : 1);
                    if (chunk != null) {
                        message.putBits(26, chunk.getPlane() << 24 | chunk.getX() << 14 |
                                chunk.getY() << 3 | chunk.getRotation() << 1);
                    }
                }
            }
        }
        message.endBitAccess();
        message.putShort(position.getChunkY(), ValueType.ADD);
        return message;
    }
}
