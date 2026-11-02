package io.luna.net.msg.out;

import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Attaches an object's model to a player for a bounded client-cycle window, temporarily replacing its scene model.
 * The #377 client restores the scene object and releases the attached model when the window ends.
 *
 * @author lare96
 */
public final class MergeGameObjectMessageWriter extends GameMessageWriter implements ChunkUpdatableMessage {

    private final int objectId;
    private final int type;
    private final int direction;
    private final int offset;
    private final int playerIndex;
    private final int startCycle;
    private final int endCycle;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;

    /**
     * Creates a temporary model merge. Bounds are inclusive tile offsets from the object's placement.
     *
     * @param objectId The object definition identifier.
     * @param type The object shape.
     * @param direction The object rotation.
     * @param offset The object's packed offset within its chunk.
     * @param playerIndex The server index of the player carrying the model, for every viewer.
     * @param startCycle Client cycles until the merge starts.
     * @param endCycle Client cycles until the merge ends.
     * @param minX The western bound relative to the object.
     * @param maxX The eastern bound relative to the object.
     * @param minY The southern bound relative to the object.
     * @param maxY The northern bound relative to the object.
     */
    public MergeGameObjectMessageWriter(int objectId, int type, int direction, int offset, int playerIndex,
                                       int startCycle, int endCycle, int minX, int maxX, int minY, int maxY) {
        checkArgument(objectId >= 0 && objectId <= 0xffff, "Invalid object identifier.");
        checkArgument(type >= 0 && type <= 22 && direction >= 0 && direction <= 3, "Invalid object shape or rotation.");
        checkArgument((offset & ~0x77) == 0, "Invalid chunk offset.");
        checkArgument(playerIndex >= 0 && playerIndex < 2048, "Invalid player index.");
        checkArgument(startCycle >= 0 && endCycle >= startCycle && endCycle <= 0xffff, "Invalid merge timing.");
        checkArgument(minX >= Byte.MIN_VALUE && minX <= maxX && maxX <= Byte.MAX_VALUE &&
                minY >= Byte.MIN_VALUE && minY <= maxY && maxY <= Byte.MAX_VALUE, "Invalid merge bounds.");
        this.objectId = objectId;
        this.type = type;
        this.direction = direction;
        this.offset = offset;
        this.playerIndex = playerIndex;
        this.startCycle = startCycle;
        this.endCycle = endCycle;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(203, buffer);
        msg.putShort(objectId);
        msg.put((type << 2) | direction);
        msg.put(maxX, ValueType.NEGATE);
        msg.put(offset, ValueType.ADD);
        msg.put(minX, ValueType.ADD);
        msg.putShort(endCycle, ValueType.ADD);
        msg.putShort(playerIndex, ByteOrder.LITTLE);
        msg.put(maxY);
        msg.put(minY, ValueType.ADD);
        msg.putShort(startCycle);
        return msg;
    }
}
