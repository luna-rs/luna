package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;

import java.util.Objects;

/**
 * An {@link Entity} implementation representing an item on a tile.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class GroundItem extends Entity {

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The item amount.
     */
    private final int amount;

    /**
     * Creates a new {@link GroundItem}.
     *
     * @param context The context instance.
     * @param id The item identifier.
     * @param amount The item amount.
     * @param position The position of the item.
     */
    public GroundItem(LunaContext context, int id, int amount, Position position) {
        super(context, EntityType.ITEM);
        this.id = id;
        this.amount = amount;
        setPosition(position);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof GroundItem) {
            GroundItem other = (GroundItem) obj;
            return id == other.id && amount == other.amount;
        }
        return false;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).add("id", id).add("amount", amount).toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, amount);
    }

    @Override
    public int size() {
        return 1;
    }

    /**
     * Returns this ground item as an {@link Item}.
     *
     * @return The converted ground item.
     */
    public Item toItem() {
        return new Item(id, amount);
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The item amount
     */
    public int getAmount() {
        return amount;
    }
}