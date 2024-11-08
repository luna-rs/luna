package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.net.msg.out.AddLocalSoundMessageWriter;

/**
 * A {@link LocalEntity} type representing an area-based sound within the game world.
 *
 * @author lare96
 */
public final class LocalSound extends LocalEntity {
// TODO test?? sound radius? sound type?
    /**
     * The radius of the sound.
     */
    private final int radius;

    /**
     * The sound type.
     */
    private final int type;

    public LocalSound(LunaContext context, int id, Position position, ChunkUpdatableView view, int radius, int type) {
        super(context, id, position, view);
        this.radius = radius;
        this.type = type;
    }

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddLocalSoundMessageWriter(id, radius, type, offset);
    }
}
