package io.luna.game.model;

import com.google.common.base.MoreObjects;
import game.player.Sound;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.net.msg.out.AddLocalSoundMessageWriter;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link LocalEntity} implementation that represents a location-based sound effect in the game world.
 * <p>
 * A {@code LocalSound} is anchored to a specific {@link Position} and delivered only to players whose
 * {@link ChunkUpdatableView} can observe that location. This makes it suitable for spatial audio events that should
 * only be heard near their source rather than broadcast globally.
 * <p>
 * Common uses include teleport effects, combat sounds, and environmental ambience.
 * The packet carries a repeat count, not a volume percentage. Each listener's client applies its own effects-volume
 * setting. Default sounds play once; custom repeats must fit the packet's three-bit field.
 *
 * @author lare96
 */
public final class LocalSound extends LocalEntity {

    /**
     * Creates a {@link LocalSound} centered on the given target using default radius and one playback.
     * <p>
     * The sound is emitted from the target's current position, uses {@link ChunkUpdatableView#globalView()}, and
     * defaults to half of {@link Position#VIEWING_DISTANCE} for its audible radius.
     *
     * @param target The entity used to supply the context and origin position.
     * @param sound The sound to play.
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound of(Entity target, Sound sound) {
        return new LocalSound(
                target.getContext(),
                sound.getId(),
                target.getPosition(),
                ChunkUpdatableView.globalView(),
                Position.VIEWING_DISTANCE / 2,
                1
        );
    }

    /**
     * Creates a {@link LocalSound} with default radius and one playback.
     * <p>
     * The default radius is half of {@link Position#VIEWING_DISTANCE}, which keeps the sound local to nearby players
     * while still being broadly audible around its source position.
     *
     * @param context The active {@link LunaContext}.
     * @param sound The sound to play.
     * @param position The world position where the sound originates.
     * @param view The view used to determine which players can receive the update.
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound of(LunaContext context, Sound sound,
                                Position position, ChunkUpdatableView view) {
        return new LocalSound(
                context,
                sound.getId(),
                position,
                view,
                Position.VIEWING_DISTANCE / 2,
                1
        );
    }

    /**
     * Creates a {@link LocalSound} with a custom audible radius and repeat count.
     *
     * @param context The active {@link LunaContext}.
     * @param sound The sound to play.
     * @param position The world position where the sound originates.
     * @param view The view used to determine which players can receive the update.
     * @param radius The audible radius in tiles. Must be greater than {@code 0}.
     * @param loops The repeat count from {@code 1} to {@code 7}, inclusive.
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound of(LunaContext context, Sound sound,
                                Position position, ChunkUpdatableView view,
                                int radius, int loops) {
        return new LocalSound(context, sound.getId(), position, view, radius, loops);
    }

    /**
     * Creates a {@link LocalSound} using a raw sound id instead of a predefined {@link Sound} value.
     * <p>
     * This is useful when a sound is not represented in the {@link Sound} enum or when the caller is working directly
     * with raw cache-defined sound ids.
     *
     * @param context The active {@link LunaContext}.
     * @param soundId The raw sound identifier.
     * @param position The world position where the sound originates.
     * @param view The view used to determine which players can receive the update.
     * @param radius The audible radius in tiles. Must be greater than {@code 0}.
     * @param loops The repeat count from {@code 1} to {@code 7}, inclusive.
     * @return A new {@link LocalSound} instance.
     */
    public static LocalSound ofId(LunaContext context, int soundId,
                                  Position position, ChunkUpdatableView view,
                                  int radius, int loops) {
        return new LocalSound(context, soundId, position, view, radius, loops);
    }

    /**
     * The audible radius of the sound, in tiles.
     */
    private final int radius;

    /**
     * The repeat count of the sound, from {@code 1} to {@code 7}.
     */
    private final int loops;

    /**
     * Creates a new {@link LocalSound}.
     *
     * @param context The active {@link LunaContext}.
     * @param id The raw sound id.
     * @param position The world position where the sound originates.
     * @param view The view used to determine which players can receive the update.
     * @param radius The audible radius in tiles.
     * @param loops The repeat count.
     */
    private LocalSound(LunaContext context, int id, Position position,
                       ChunkUpdatableView view, int radius, int loops) {
        super(context, id, EntityType.SOUND, position, view);

        checkArgument(radius > 0, "Radius must be > 0.");
        checkArgument(loops >= 1 && loops <= 7,
                "Loops must be between 1-7.");

        this.radius = radius;
        this.loops = loops;
    }

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddLocalSoundMessageWriter(id, radius, loops, offset);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("radius", radius)
                .add("loops", loops)
                .toString();
    }

    /**
     * @return The audible radius in tiles.
     */
    public int getRadius() {
        return radius;
    }

    /**
     * @return The repeat count from {@code 1} to {@code 7}.
     */
    public int getLoops() {
        return loops;
    }
}
