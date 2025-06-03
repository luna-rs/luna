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

/**
 * Loads a {@link DynamicMapPalette} for a player instead of the current region.
 *
 * @author lare96
 */
public final class DynamicMapMessageWriter extends GameMessageWriter {
 // todo play around with the way data is sent
    /**
     * The palette to send.
     */
    private final DynamicMap map;
    private final Position position;

    /**
     * Creates a new {@link DynamicMapMessageWriter}.
     *
     * @param palette The palette to send.
     */
    public DynamicMapMessageWriter(DynamicMap map, Position position) {
        this.map = map;
        this.position = position;
    }

    @Override
    public ByteMessage write(Player player) {


        ByteMessage msg = ByteMessage.message(53, MessageType.VAR_SHORT);
        msg.putShort(position.getChunkX(), ValueType.ADD);
        msg.startBitAccess();
        map.getPalette().forEach((x, y, z) -> {
            DynamicMapChunk tile = map.getPalette().getChunk(x, y, z);
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
        /*          int chunkX = position.getChunkX() - 6;
        int chunkY = position.getChunkY() - 6;
        for (int plane = 0; plane < 4; plane++) {
            for (int x = chunkX; x <= chunkX + 6; x++) { // calcs
                for (int y = chunkY; y <= chunkY + 6; y++) { // calcs
                    Region loadRegion = new Region((x >> 3) << 8 | (y >> 3));
                    if (map.getAssignedSpace().getAllRegions().contains(loadRegion)) {
                        System.out.println(loadRegion.getId());
                        System.out.println(x);
                        System.out.println(loadRegion.getX());
                        System.out.println(x - (loadRegion.getX() << 3));
                        DynamicMapChunk tile = map.getPalette().getChunk(x - (loadRegion.getX() << 3), y - (loadRegion.getY() << 3), plane);
                        if (tile != null) {
                            msg.putBits(1, 1);
                            msg.putBits(26, tile.getX() << 14 |
                                    tile.getY() << 3 |
                                    tile.getPlane() << 24 |
                                    tile.getRotation() << 1);
                        }
                        continue;
                    }
                    msg.putBits(1, 0);
                }
            }
        }*/
        msg.endBitAccess();
        msg.putShort(position.getChunkY(), ValueType.ADD);
        return msg;
    }
}
