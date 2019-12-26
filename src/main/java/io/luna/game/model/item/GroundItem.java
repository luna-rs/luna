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

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * An {@link Entity} implementation representing an item on a tile.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class GroundItem extends StationaryEntity {

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
    public final int hashCode() {
        return Objects.hash(id, amount, position, getOwner());
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof GroundItem) {
            GroundItem other = (GroundItem) obj;
            return id == other.id &&
                    amount == other.amount &&
                    Objects.equals(position, other.position) &&
                    Objects.equals(getOwner(), other.getOwner());
        }
        return false;
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this).
                add("id", id).
                add("amount", amount).
                add("x", position.getX()).
                add("y", position.getY()).
                add("z", position.getZ()).
                add("owner", getOwner().map(Player::getUsername).orElse("null")).
                toString();
    }

    @Override
    public final int size() {
        return 0;
    }

    @Override
    protected final GameMessageWriter showMessage(int offset) {
        return new AddGroundItemMessageWriter(id, amount, offset);
    }

    @Override
    protected final GameMessageWriter hideMessage(int offset) {
        return new RemoveGroundItemMessageWriter(id, offset);
    }

    /**
     * Sets whether or not this item will expire or not.
     *
     * @param expire The value.
     */
    public final void setExpire(boolean expire) {
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
    public final void setExpireMinutes(int minutes) {
        checkState(isExpire(), "This item does not expire.");
        expireMinutes = OptionalInt.of(minutes);
    }

    /**
     * Retrieves the current expiration minutes for this item. Will throw {@link IllegalStateException} if this item
     * does not expire.
     */
    public final int getExpireMinutes() {
        checkState(isExpire(), "This item does not expire.");
        return expireMinutes.getAsInt();
    }

    /**
     * Determines if this item expires.
     *
     * @return {@code true} if this item expires.
     */
    public final boolean isExpire() {
        return expireMinutes.isPresent();
    }

    /**
     * Retrieves the item definition instance.
     */
    public final ItemDefinition def() {
        return ItemDefinition.ALL.retrieve(id);
    }

    /**
     * @return The item identifier.
     */
    public final int getId() {
        return id;
    }

    /**
     * Hides this item, changes its amount, and then shows it again.
     *
     * @param amount The amount to change to. Cannot be negative.
     */
    public final void setAmount(int amount) {
        checkArgument(amount > 0, "amount cannot be < 0");
        hide();
        this.amount = amount;
        show();
    }

    /**
     * @return The item amount
     */
    public final int getAmount() {
        return amount;
    }

    /**
     * Returns an item instance with this ground item's ID and amount.
     *
     * @return The item instance.
     */
    public final Item toItem() {
        return new Item(id, amount);
    }
}