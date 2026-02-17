package io.luna.game.model.item;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.StationaryEntity;
import io.luna.game.model.chunk.ChunkUpdatableMessage;
import io.luna.game.model.chunk.ChunkUpdatableRequest;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.attr.AttributeMap;
import io.luna.net.msg.out.AddGroundItemMessageWriter;
import io.luna.net.msg.out.RemoveGroundItemMessageWriter;
import io.luna.net.msg.out.UpdateGroundItemMessageWriter;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link StationaryEntity} representing an item lying on a world tile (a "ground item").
 * <p>
 * Ground items are rendered through the chunk update system and are visible to players according to their
 * {@link ChunkUpdatableView}.
 * <p>
 * <b>Stack rules:</b> This class enforces that non-stackable item definitions may only exist as single units on the
 * ground (amount must be {@code 1}). Any "multiple" non-stackable drop must be represented as multiple
 * {@link GroundItem} entities (one per unit).
 * <p>
 * <b>Expiration:</b> Expiration is controlled by an internal tick counter:
 * <ul>
 *   <li>{@code expireTicks == -1} means the item does not expire</li>
 *   <li>{@code expireTicks >= 0} means the item is expiring and the value represents elapsed ticks</li>
 * </ul>
 * <p>
 * <b>Identity equality:</b> {@link #equals(Object)} and {@link #hashCode()} use identity semantics (this instance
 * only). If you need content-based equality, use {@link #isIdentical(GroundItem)}.
 *
 * @author lare96
 */
public class GroundItem extends StationaryEntity {

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The item amount. Always {@code 1} for non-stackable item definitions.
     */
    private int amount;

    /**
     * Expiration tick counter.
     * <p>
     * Value meanings:
     * <ul>
     *   <li>{@code -1} => not expiring</li>
     *   <li>{@code 0+} => expiring; counts elapsed ticks since expiration started</li>
     * </ul>
     */
    private int expireTicks;

    /**
     * Creates a new {@link GroundItem}.
     *
     * @param context The active {@link LunaContext} instance.
     * @param id The item identifier.
     * @param amount The item amount.
     * @param position The world position of the item.
     * @param view The visibility rules for this item.
     */
    public GroundItem(LunaContext context, int id, int amount, Position position, ChunkUpdatableView view) {
        super(context, position, EntityType.ITEM, view);
        checkArgument(ItemDefinition.isIdValid(id), "Invalid item identifier.");
        checkArgument(amount > 0, "Amount must be above 0.");

        // Non-stackable ground items must be represented one-by-one.
        ItemDefinition def = ItemDefinition.ALL.retrieve(id);
        checkArgument(def.isStackable() || amount == 1,
                "Non-stackable ground items [" + def.getName() + "] have a maximum amount of 1.");

        this.id = id;
        this.amount = amount;
    }

    /**
     * Creates a new {@link GroundItem}.
     * <p>
     * If {@code item} is a {@link DynamicItem}, its attributes are copied onto this ground item via
     * {@link #setAttributes(AttributeMap)}.
     *
     * @param context The active {@link LunaContext} instance.
     * @param item The item stack to place on the ground.
     * @param position The world position of the item.
     * @param view The visibility rules for this item.
     */
    public GroundItem(LunaContext context, Item item, Position position, ChunkUpdatableView view) {
        this(context, item.getId(), item.getAmount(), position, view);
        if (item.isDynamic()) {
            setAttributes(item.asDynamic().attributes());
        }
    }

    /**
     * Uses identity-based hashing.
     * <p>
     * Ground items represent physical drop entities in the world. Even if two drops share the same id, amount,
     * position, and view, they are still separate entities.
     * <p>
     * For example, two {@code abyssal whip} drops on the same tile may have identical data, but they are distinct
     * world objects and must be treated independently.
     * <p>
     * Identity-based hashing ensures each drop remains uniquely tracked regardless of field equality.
     */
    @Override
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * Uses identity-based equality.
     *
     * @see #hashCode()
     */
    @Override
    public final boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public final String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("amount", amount)
                .add("x", position.getX())
                .add("y", position.getY())
                .add("z", position.getZ())
                .add("view", getView())
                .toString();
    }

    /**
     * Ground items do not occupy collision size in the world model.
     *
     * @return Always {@code 0}.
     */
    @Override
    public final int size() {
        return 0;
    }

    /**
     * Ground items do not occupy collision size in the world model.
     *
     * @return Always {@code 0}.
     */
    @Override
    public final int sizeX() {
        return 0;
    }

    /**
     * Ground items do not occupy collision size in the world model.
     *
     * @return Always {@code 0}.
     */
    @Override
    public final int sizeY() {
        return 0;
    }

    @Override
    protected final ChunkUpdatableMessage showMessage(int offset) {
        return new AddGroundItemMessageWriter(id, amount, offset);
    }

    @Override
    protected final ChunkUpdatableMessage hideMessage(int offset) {
        return new RemoveGroundItemMessageWriter(id, offset);
    }

    /**
     * Field-based comparison for "content equality" of ground items.
     * <p>
     * Two ground items are considered identical if:
     * <ul>
     *   <li>their {@link #id} matches</li>
     *   <li>their {@link #amount} matches</li>
     *   <li>their {@link #position} matches</li>
     *   <li>their {@link #getView()} matches</li>
     * </ul>
     * <p>
     * This method ignores entity identity and state (active/inactive).
     *
     * @param other The other ground item to compare with.
     * @return {@code true} if the two ground items represent the same "drop" data.
     */
    public boolean isIdentical(GroundItem other) {
        return id == other.id
                && amount == other.amount
                && Objects.equals(position, other.position)
                && Objects.equals(getView(), other.getView());
    }

    /**
     * Enables or disables expiration for this ground item.
     * <p>
     * When enabling expiration:
     * <ul>
     *   <li>if expiration was previously disabled, tick counter becomes {@code 0}</li>
     * </ul>
     * <p>
     * When disabling expiration:
     * <ul>
     *   <li>tick counter becomes {@code -1}</li>
     * </ul>
     *
     * @param expire {@code true} to enable expiration, false to disable.
     */
    public final void setExpire(boolean expire) {
        if (expire && expireTicks == -1) {
            expireTicks = 0;
        } else if (!expire && expireTicks >= 0) {
            expireTicks = -1;
        }
    }

    /**
     * Increments the expiration tick counter by 1.
     *
     * @return The updated expiration tick count.
     */
    public final int addExpireTick() {
        int newValue = getExpireTicks() + 1;
        setExpireTicks(newValue);
        return newValue;
    }

    /**
     * Sets the current expiration tick counter.
     *
     * @param ticks The new tick counter value.
     */
    public final void setExpireTicks(int ticks) {
        expireTicks = ticks;
    }

    /**
     * @return The current expiration tick counter value ({@code -1} if not expiring).
     */
    public final int getExpireTicks() {
        return expireTicks;
    }

    /**
     * @return {@code true} if this item is configured to expire.
     */
    public final boolean isExpiring() {
        return expireTicks >= 0;
    }

    /**
     * Retrieves the {@link ItemDefinition} for this ground item's {@link #id}.
     *
     * @return The item definition for this ground item.
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
     * Updates the visible amount of this ground item in-place.
     * <p>
     * This sends an {@link UpdateGroundItemMessageWriter} to relevant viewers via the chunk update system, then
     * updates {@link #amount} server-side.
     * <p>
     * Constraints:
     * <ul>
     *   <li>{@code value > 0}</li>
     *   <li>if the item definition is non-stackable, the amount must remain {@code 1}</li>
     * </ul>
     *
     * @param value The new amount.
     * @throws IllegalArgumentException if {@code value <= 0} or violates non-stackable constraints.
     */
    public final void updateAmount(int value) {
        checkArgument(value > 0, "amount cannot be < 0");
        checkArgument(def().isStackable() || value == 1,
                "Non-stackable ground items have a maximum amount of 1.");

        int offset = getChunk().offset(position);
        UpdateGroundItemMessageWriter msg = new UpdateGroundItemMessageWriter(offset, id, amount, value);
        chunkRepository.queueUpdate(new ChunkUpdatableRequest(this, msg, false));
        amount = value;
    }

    /**
     * @return The current item amount.
     */
    public final int getAmount() {
        return amount;
    }

    /**
     * Converts this ground item back into an {@link Item}.
     * <p>
     * If this ground item has attributes (dynamic data), a {@link DynamicItem} is created using the stored attributes.
     * Otherwise, a normal {@link Item} is created using this ground item's id and amount.
     *
     * @return A new item instance representing this ground item.
     */
    public final Item toItem() {
        return hasAttributes() ? new DynamicItem(id, attributes()) : new Item(id, amount);
    }
}
