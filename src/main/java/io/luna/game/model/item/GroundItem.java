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

import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

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
    private int amount;

    /**
     * The current amount of expiration minutes.
     */
    private OptionalInt expireMinutes = OptionalInt.of(0);

    /**
     * Creates a new {@link GroundItem}.
     *
     * @param context The context instance.
     * @param id The item identifier.
     * @param amount The item amount.
     * @param position The position of the item.
     */
    public GroundItem(LunaContext context, int id, int amount, Position position, Player player) {
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
        return this == obj;
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
        return 0;
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
     * Sets whether or not this item will expire or not.
     *
     * @param expire The value.
     */
    public void setExpire(boolean expire) {
        if (expire && expireMinutes.isEmpty()) {
            expireMinutes = OptionalInt.of(0);
        } else if (!expire && expireMinutes.isPresent()) {
            expireMinutes = OptionalInt.empty();
        }
    }

    /**
     * Sets the current expiration minutes for this item. Will throw {@link IllegalStateException} if this item
     * does not expire.
     */
    public void setExpireMinutes(int minutes) {
        checkState(isExpire(), "This item does not expire.");
        expireMinutes = OptionalInt.of(minutes);
    }

    /**
     * Retrieves the current expiration minutes for this item. Will throw {@link IllegalStateException} if this item
     * does not expire.
     */
    public int getExpireMinutes() {
        return expireMinutes.getAsInt();
    }

    /**
     * Determines if this item expires.
     *
     * @return {@code true} if this item expires.
     */
    public boolean isExpire() {
        return expireMinutes.isPresent();
    }

    /**
     * Retrieves the item definition instance.
     */
    public ItemDefinition def() {
        return ItemDefinition.ALL.retrieve(id);
    }

    /**
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    public void setAmount(int amount) {
        hide();
        this.amount = amount;
        show();
    }

    /**
     * @return The item amount
     */
    public int getAmount() {
        return amount;
    }
}
