package io.luna.game.model;

import com.google.common.base.MoreObjects;
import game.player.Sounds;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.net.msg.out.AddLocalSoundMessageWriter;

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

    // todo builder...
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

    public LocalSound(LunaContext context, Sounds sound, Position position, ChunkUpdatableView view) {
        this(context, sound, position, view, Position.VIEWING_DISTANCE / 2, 100);
    }

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddLocalSoundMessageWriter(id, radius, volume, offset);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("radius", radius)
                .add("volume", volume)
                .toString();
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
