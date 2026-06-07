package io.luna.game.model.item;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.mob.Mob;

/**
 * A {@link GroundItem} created as the result of a {@link Mob} dropping an item on death.
 * <p>
 * This item keeps track of the mob that produced the drop. This is useful for bot logic, debugging, loot attribution,
 * drop reactions, rare drop announcements, or any system that needs to know where a death-dropped ground item came from.
 *
 * @author lare96
 */
public final class DeathGroundItem extends GroundItem {

    /**
     * The mob that produced this ground item.
     */
    private final Mob origin;

    /**
     * Creates a new {@link DeathGroundItem}.
     *
     * @param context The context.
     * @param id The item id.
     * @param amount The amount of the item.
     * @param position The position where the item was dropped.
     * @param view The chunk view responsible for tracking and updating this ground item.
     * @param origin The mob that produced this drop.
     */
    public DeathGroundItem(LunaContext context, int id, int amount, Position position, ChunkUpdatableView view, Mob origin) {
        super(context, id, amount, position, view);
        this.origin = origin;
    }

    /**
     * @return The mob that produced this drop.
     */
    public Mob getOrigin() {
        return origin;
    }
}