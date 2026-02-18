package io.luna.game.model;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.chunk.ChunkManager;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.Graphic;
import io.luna.net.msg.out.AddGraphicMessageWriter;

/**
 * A {@link LocalEntity} implementation representing a world-based animated graphic that is not attached to a
 * specific {@link Player}.
 * <p>
 * Unlike {@link Graphic}, which is typically applied directly to a mobile entity (e.g., a player or NPC),
 * {@code LocalGraphic} represents a positional effect that exists independently within the game world.
 * <p>
 * These graphics are dispatched during chunk update cycles via {@link ChunkManager#sendUpdates(Player, Position, boolean)}
 * and are only visible to players contained within the graphic's {@link ChunkUpdatableView}.
 * <p>
 * Typical use cases include:
 * <ul>
 *     <li>Spell impact effects on the ground</li>
 *     <li>Environmental visuals (explosions, magical effects)</li>
 * </ul>
 *
 * @author lare96
 */
public final class LocalGraphic extends LocalEntity {

    /**
     * The vertical height offset applied to this graphic.
     * <p>
     * Determines how high above the base tile the animation is rendered.
     */
    private final int height;

    /**
     * The delay (in client ticks) before the graphic animation begins.
     */
    private final int delay;

    /**
     * Creates a new {@link LocalGraphic}.
     *
     * @param context The active {@link LunaContext}.
     * @param id The graphic identifier.
     * @param height The vertical height offset.
     * @param delay The animation delay.
     * @param position The world position of the graphic.
     * @param view The visibility view defining which players can see it.
     */
    public LocalGraphic(LunaContext context, int id, int height, int delay,
                        Position position, ChunkUpdatableView view) {
        super(context, id, EntityType.GRAPHIC, position, view);
        this.height = height;
        this.delay = delay;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("position", position)
                .add("height", height)
                .add("delay", delay)
                .toString();
    }

    /**
     * Produces the network message responsible for displaying this graphic
     * to nearby players.
     *
     * @param offset The chunk-relative position offset.
     * @return The encoded {@link ChunkUpdatableMessage}.
     */
    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddGraphicMessageWriter(id, offset, height, delay);
    }
}
