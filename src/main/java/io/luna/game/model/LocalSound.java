package io.luna.game.model;

import com.google.common.base.MoreObjects;
import game.player.Sounds;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.net.msg.out.AddLocalSoundMessageWriter;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link LocalEntity} implementation representing an area-based sound effect within the game world.
 * <p>
 * {@code LocalSound} instances are spatially bound sound events that are sent to players within a specific
 * {@link #radius}. Unlike global audio effects, local sounds are tied to a {@link Position} and only delivered to players
 * whose {@link ChunkUpdatableView} includes that position.
 * <p>
 * This entity type is typically used for:
 * <ul>
 *     <li>Teleporting and other magic-related sounds</li>
 *     <li>Combat-related sound effects</li>
 *     <li>Area ambience or environmental effects</li>
 * </ul>
 *
 * @author lare96
 */
public final class LocalSound extends LocalEntity {

    /**
     * Creates a {@link LocalSound} with default radius and maximum volume.
     * <p>
     * The default radius is half of {@link Position#VIEWING_DISTANCE}, ensuring the sound remains localized but
     * audible to nearby players.
     *
     * @param context The active {@link LunaContext}.
     * @param sound The {@link Sounds} enum value representing the sound.
     * @param position The world position where the sound originates.
     * @param view The chunk update view used to determine visibility.
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound of(LunaContext context, Sounds sound,
                                Position position, ChunkUpdatableView view) {
        return new LocalSound(
                context,
                sound.getId(),
                position,
                view,
                Position.VIEWING_DISTANCE / 2,
                100
        );
    }

    /**
     * Creates a {@link LocalSound} with a custom radius and volume.
     *
     * @param context The active {@link LunaContext}.
     * @param sound The {@link Sounds} enum value representing the sound.
     * @param position The world position where the sound originates.
     * @param view The chunk update view used to determine visibility.
     * @param radius The audible radius (must be > 0).
     * @param volume The playback volume (0–100 inclusive).
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound of(LunaContext context, Sounds sound,
                                Position position, ChunkUpdatableView view,
                                int radius, int volume) {
        return new LocalSound(context, sound.getId(), position, view, radius, volume);
    }

    /**
     * Creates a {@link LocalSound} using a raw sound id.
     * <p>
     * This method is useful when the sound id does not correspond to a predefined {@link Sounds} enum value.
     *
     * @param context The active {@link LunaContext}.
     * @param soundId The raw sound identifier.
     * @param position The world position where the sound originates.
     * @param view The chunk update view used to determine visibility.
     * @param radius The audible radius (must be > 0).
     * @param volume The playback volume (0–100 inclusive).
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound ofId(LunaContext context, int soundId,
                                  Position position, ChunkUpdatableView view,
                                  int radius, int volume) {
        return new LocalSound(context, soundId, position, view, radius, volume);
    }

    /**
     * The audible radius of the sound.
     */
    private final int radius;

    /**
     * The playback volume of the sound (0–100 inclusive).
     */
    private final int volume;

    /**
     * Creates a new {@link LocalSound}.
     *
     * @param context The {@link LunaContext}.
     * @param id The sound id.
     * @param position The world position.
     * @param view The chunk visibility view.
     * @param radius The audible radius.
     * @param volume The playback volume.
     */
    private LocalSound(LunaContext context, int id, Position position,
                       ChunkUpdatableView view, int radius, int volume) {
        super(context, id, EntityType.SOUND, position, view);

        checkArgument(radius > 0, "Radius must be > 0.");
        checkArgument(volume >= 0 && volume <= 100,
                "Volume must be between 0-100.");

        this.radius = radius;
        this.volume = volume;
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
     * Returns the audible radius of this sound.
     *
     * @return The radius in tiles.
     */
    public int getRadius() {
        return radius;
    }

    /**
     * Returns the playback volume of this sound.
     *
     * @return The volume (0–100).
     */
    public int getVolume() {
        return volume;
    }
}
