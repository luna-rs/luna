package io.luna.game.model;

import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.net.msg.out.AddLocalSoundMessageWriter;
import world.player.Sounds;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link LocalEntity} type representing an area-based sound within the game world.
 *
 * @author lare96
 */
public final class LocalSound extends LocalEntity {

    /**
     * The radius of the sound.
     */
    private final int radius;

    /**
     * The sound volume, between 0-100.
     */
    private final int volume;

    public LocalSound(LunaContext context, int id, Position position, ChunkUpdatableView view, int radius, int volume) {
        super(context, id, EntityType.SOUND, position, view);
        checkState(volume >= 0 && volume <= 100, "Volume must be between 0-100.");
        this.radius = radius;
        this.volume = volume;
    }
    public LocalSound(LunaContext context, Sounds sound, Position position, ChunkUpdatableView view, int radius, int volume) {
        super(context, sound.getId(), EntityType.SOUND, position, view);
        checkState(volume >= 0 && volume <= 100, "Volume must be between 0-100.");
        this.radius = radius;
        this.volume = volume;
    }
    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddLocalSoundMessageWriter(id, radius, volume, offset);
    }

    /**
     * @return The radius of the sound.
     */
    public int getRadius() {
        return radius;
    }

    /**
     * @return The sound volume, between 0-100.
     */
    public int getVolume() {
        return volume;
    }
}
