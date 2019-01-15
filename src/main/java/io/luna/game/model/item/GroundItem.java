package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessageWriter;
import io.luna.net.msg.out.AddGroundItemMessageWriter;
import io.luna.net.msg.out.RemoveGroundItemMessageWriter;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An {@link Entity} implementation representing an item on a tile.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GroundItem extends StationaryEntity {

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
    public GroundItem(LunaContext context, int id, int amount, Position position, Optional<Player> player) {
        super(context, position, EntityType.ITEM, player);
        checkArgument(ItemDefinition.isIdValid(id), "Invalid item identifier.");
        checkArgument(amount > 0, "Amount must be above 0.");

        // Non-stackable ground items are placed one by one.
        ItemDefinition def = ItemDefinition.ALL.retrieve(id);
        checkArgument(def.isStackable() || amount == 1,
                "Non-stackable ground items have a maximum amount of 1.");

        this.id = id;
        this.amount = amount;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("amount", amount).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).toString();
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    protected GameMessageWriter showMessage(int offset) {
        return new AddGroundItemMessageWriter(id, amount, offset);
    }

    @Override
    protected GameMessageWriter hideMessage(int offset) {
        return new RemoveGroundItemMessageWriter(id, offset);
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