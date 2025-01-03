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
 * Represents an animated graphic similar to the {@link Graphic} type, except this type is not linked to
 * a {@link Player}. These graphics can be placed anywhere in the RS2 world as a part of the
 * {@link ChunkManager#sendUpdates(Player, Position, boolean)} function.
 *
 * @author lare96
 */
public final class LocalGraphic extends LocalEntity {

    /**
     * The graphic height.
     */
    private final int height;

    /**
     * The graphic delay.
     */
    private final int delay;

    /**
     * Creates a new {@link LocalGraphic}.
     *
     * @param context The context instance.
     * @param id The id of the graphic.
     * @param height The graphic height.
     * @param delay The graphic delay.
     * @param position The position.
     * @param view Who this graphic is viewable for.
     */
    public LocalGraphic(LunaContext context, int id, int height, int delay, Position position, ChunkUpdatableView view) {
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

    @Override
    public ChunkUpdatableMessage displayMessage(int offset) {
        return new AddGraphicMessageWriter(id, offset, height, delay);
    }
}
