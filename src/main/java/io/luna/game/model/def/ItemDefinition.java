package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.luna.util.StringUtils;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A cache-backed definition describing an item.
 * <p>
 * Item definitions provide immutable metadata used for:
 * <ul>
 *     <li>display/examine strings (name/description)</li>
 *     <li>inventory and ground context menu actions</li>
 *     <li>stackability, tradeability, value, membership restrictions</li>
 *     <li>client model rendering parameters (model id, zoom, rotations, offsets, scales)</li>
 *     <li>note to unnote relationships (banknotes)</li>
 *     <li>optional “team” metadata (used by some items/capes in some revisions)</li>
 *     <li>weight metadata (used by weight/run-energy systems if enabled)</li>
 * </ul>
 * <p>
 * <b>Notes:</b>
 * Items may have a noted/unnoted relationship:
 * <ul>
 *     <li>{@link #unnotedId} stores the unnoted id for a noted item.</li>
 *     <li>{@link #notedId} stores the noted id for an unnoted item (populated via {@link #toNote(ItemDefinition)}).</li>
 * </ul>
 * This class provides {@link #isNoted()} to identify note definitions.
 * <p>
 * <b>Immutability note:</b> Most fields are final, but {@link #notedId} is intentionally mutable to allow linking
 * an unnoted definition to its noted partner after construction (see {@link #toNote(ItemDefinition)}).
 *
 * @author lare96
 */
public final class ItemDefinition implements Definition {

    /**
     * Total number of item definitions expected for this cache.
     */
    public static final int SIZE = 7956;

    /**
     * Repository of all {@link ItemDefinition}s, indexed by item id.
     */
    public static final DefinitionRepository<ItemDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The item id.
     */
    private final int id;

    /**
     * The item display name.
     */
    private final String name;

    /**
     * The examine/description text.
     */
    private final String description;

    /**
     * The model id used for rendering this item in the client.
     */
    private final int modelId;

    /**
     * The model zoom value used for rendering this item in the client.
     */
    private final int modelZoom;

    /**
     * The model rotation around the X axis.
     */
    private final int modelRotationX;

    /**
     * The model rotation around the Y axis.
     */
    private final int modelRotationY;

    /**
     * The model rotation around the Z axis.
     */
    private final int modelRotationZ;

    /**
     * The model X offset.
     */
    private final int modelOffsetX;

    /**
     * The model Y offset.
     */
    private final int modelOffsetY;

    /**
     * Whether this item stacks in containers.
     */
    private final boolean stackable;

    /**
     * Base value (often used for shops or examine value, depending on revision).
     */
    private final int value;

    /**
     * Whether this item is members-only.
     */
    private final boolean members;

    /**
     * Inventory context menu actions (client order).
     */
    private final ImmutableList<String> inventoryActions;

    /**
     * Ground context menu actions (client order).
     */
    private final ImmutableList<String> groundActions;

    /**
     * Unnoted id for this item, if this item is a note.
     */
    private final OptionalInt unnotedId;

    /**
     * Noted id for this item, if this item has a note variant.
     * <p>
     * This starts empty and may be populated after construction via {@link #toNote(ItemDefinition)}.
     */
    private OptionalInt notedId = OptionalInt.empty();

    /**
     * Model scale on the X axis.
     */
    private final int modelScaleX;

    /**
     * Model scale on the Y axis.
     */
    private final int modelScaleY;

    /**
     * Model scale on the Z axis.
     */
    private final int modelScaleZ;

    /**
     * Team id for this item, if present.
     */
    private final OptionalInt teamId;

    /**
     * Weight value for this item.
     */
    private final double weight;

    /**
     * Whether this item is tradeable.
     */
    private final boolean tradeable;

    /**
     * Creates a new {@link ItemDefinition}.
     *
     * @param id The item id.
     * @param name The item name.
     * @param description The examine/description text.
     * @param modelId The model id.
     * @param modelZoom The model zoom.
     * @param modelRotationX The model X rotation.
     * @param modelRotationY The model Y rotation.
     * @param modelRotationZ The model Z rotation.
     * @param modelOffsetX The model X offset.
     * @param modelOffsetY The model Y offset.
     * @param stackable Whether the item stacks.
     * @param value The base value.
     * @param members Whether the item is members-only.
     * @param inventoryActions Inventory actions (client order).
     * @param groundActions Ground actions (client order).
     * @param unnotedId Unnoted id if this item is a note.
     * @param modelScaleX Model X scale.
     * @param modelScaleY Model Y scale.
     * @param modelScaleZ Model Z scale.
     * @param teamId Optional team id.
     * @param weight Weight value.
     * @param tradeable Whether the item is tradeable.
     */
    public ItemDefinition(int id, String name, String description, int modelId, int modelZoom, int modelRotationX,
                          int modelRotationY, int modelRotationZ, int modelOffsetX, int modelOffsetY, boolean stackable,
                          int value, boolean members, String[] inventoryActions, String[] groundActions,
                          OptionalInt unnotedId, int modelScaleX, int modelScaleY,
                          int modelScaleZ, OptionalInt teamId, double weight, boolean tradeable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.modelId = modelId;
        this.modelZoom = modelZoom;
        this.modelRotationX = modelRotationX;
        this.modelRotationY = modelRotationY;
        this.modelRotationZ = modelRotationZ;
        this.modelOffsetX = modelOffsetX;
        this.modelOffsetY = modelOffsetY;
        this.stackable = stackable;
        this.value = value;
        this.members = members;
        this.inventoryActions = ImmutableList.copyOf(inventoryActions);
        this.groundActions = ImmutableList.copyOf(groundActions);
        this.unnotedId = unnotedId;
        this.modelScaleX = modelScaleX;
        this.modelScaleY = modelScaleY;
        this.modelScaleZ = modelScaleZ;
        this.teamId = teamId;
        this.weight = weight;
        this.tradeable = tradeable;

        /*
         * If this definition already has an unnoted id, it is a note definition (its noted id is itself).
         */
        if (unnotedId.isPresent()) {
            notedId = OptionalInt.of(id);
        }
    }

    /**
     * Returns {@code true} if the given id is within the valid item id bounds for this cache.
     *
     * @param id The id to test.
     * @return {@code true} if {@code id} is within {@code (0, SIZE)}.
     */
    public static boolean isIdValid(int id) {
        return id > 0 && id < SIZE;
    }

    /**
     * Returns {@code true} if the inventory action at {@code index} equals {@code action}.
     *
     * @param index The action index (client order).
     * @param action The action text to compare (case-sensitive).
     * @return {@code true} if the action matches.
     */
    public boolean hasInventoryAction(int index, String action) {
        return Objects.equals(action, inventoryActions.get(index));
    }

    /**
     * Returns {@code true} if the ground action at {@code index} equals {@code action}.
     *
     * @param index The action index (client order).
     * @param action The action text to compare (case-sensitive).
     * @return {@code true} if the action matches.
     */
    public boolean hasGroundAction(int index, String action) {
        return action.equals(groundActions.get(index));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .add("description", description)
                .add("modelId", modelId)
                .add("modelZoom", modelZoom)
                .add("modelRotationX", modelRotationX)
                .add("modelRotationY", modelRotationY)
                .add("modelRotationZ", modelRotationZ)
                .add("modelOffset1", modelOffsetX)
                .add("modelOffset2", modelOffsetY)
                .add("stackable", stackable)
                .add("value", value)
                .add("members", members)
                .add("inventoryActions", inventoryActions)
                .add("groundActions", groundActions)
                .add("unnotedId", unnotedId)
                .add("notedId", notedId)
                .add("modelScaleX", modelScaleX)
                .add("modelScaleY", modelScaleY)
                .add("modelScaleZ", modelScaleZ)
                .add("teamId", teamId)
                .add("weight", weight)
                .add("tradeable", tradeable)
                .toString();
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * Builds a noted version of an unnoted definition and links the pair together.
     * <p>
     * This method is intended to be called on a definition that represents the <b>note</b> (the “paper” item), passing
     * the corresponding unnoted definition.
     * <p>
     * Side effects:
     * <ul>
     *     <li>mutates {@code unnotedDef.notedId} to reference this note id</li>
     *     <li>returns a new {@link ItemDefinition} representing the noted item using the unnoted name/value and a
     *     banknote-style description</li>
     * </ul>
     *
     * @param unnotedDef The unnoted item definition that this note represents.
     * @return A newly constructed noted {@link ItemDefinition}.
     */
    public ItemDefinition toNote(ItemDefinition unnotedDef) {
        unnotedDef.notedId = OptionalInt.of(id);
        String description = "Swap this note at any bank for " + StringUtils.addArticle(unnotedDef.name) + ".";
        return new ItemDefinition(
                id,
                unnotedDef.name,
                description,
                modelId,
                modelZoom,
                modelRotationX,
                modelRotationY,
                modelRotationZ,
                modelOffsetX,
                modelOffsetY,
                true,
                unnotedDef.value,
                unnotedDef.members,
                Iterables.toArray(inventoryActions, String.class),
                Iterables.toArray(groundActions, String.class),
                unnotedId,
                modelScaleX,
                modelScaleY,
                modelScaleZ,
                teamId,
                weight,
                true
        );
    }

    /**
     * Returns {@code true} if this definition represents a noted item.
     * <p>
     * In this model, an item is considered “noted” when it has both:
     * <ul>
     *     <li>an {@link #unnotedId} (it points to the real item)</li>
     *     <li>a {@link #notedId} (it is itself, or has been linked)</li>
     * </ul>
     *
     * @return {@code true} if noted.
     */
    public boolean isNoted() {
        return unnotedId.isPresent() && notedId.isPresent();
    }

    /**
     * Returns the item name.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the examine/description text.
     *
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the model id.
     *
     * @return The model id.
     */
    public int getModelId() {
        return modelId;
    }

    /**
     * Returns the model zoom.
     *
     * @return The zoom.
     */
    public int getModelZoom() {
        return modelZoom;
    }

    /**
     * Returns the model X rotation.
     *
     * @return The X rotation.
     */
    public int getModelRotationX() {
        return modelRotationX;
    }

    /**
     * Returns the model Y rotation.
     *
     * @return The Y rotation.
     */
    public int getModelRotationY() {
        return modelRotationY;
    }

    /**
     * Returns the model Z rotation.
     *
     * @return The Z rotation.
     */
    public int getModelRotationZ() {
        return modelRotationZ;
    }

    /**
     * Returns the model X offset.
     *
     * @return The X offset.
     */
    public int getModelOffsetX() {
        return modelOffsetX;
    }

    /**
     * Returns the model Y offset.
     *
     * @return The Y offset.
     */
    public int getModelOffsetY() {
        return modelOffsetY;
    }

    /**
     * Returns whether this item is stackable.
     *
     * @return {@code true} if stackable.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * Returns the base value.
     *
     * @return The value.
     */
    public int getValue() {
        return value;
    }

    /**
     * Returns whether this item is members-only.
     *
     * @return {@code true} if members-only.
     */
    public boolean isMembers() {
        return members;
    }

    /**
     * Returns inventory actions in client order.
     *
     * @return The inventory actions list.
     */
    public ImmutableList<String> getInventoryActions() {
        return inventoryActions;
    }

    /**
     * Returns ground actions in client order.
     *
     * @return The ground actions list.
     */
    public ImmutableList<String> getGroundActions() {
        return groundActions;
    }

    /**
     * Returns the unnoted id for this item, if this item is a note.
     *
     * @return The unnoted id.
     */
    public OptionalInt getUnnotedId() {
        return unnotedId;
    }

    /**
     * Returns the noted id for this item, if this item has a noted variant.
     *
     * @return The noted id.
     */
    public OptionalInt getNotedId() {
        return notedId;
    }

    /**
     * Returns the model X scale.
     *
     * @return The X scale.
     */
    public int getModelScaleX() {
        return modelScaleX;
    }

    /**
     * Returns the model Y scale.
     *
     * @return The Y scale.
     */
    public int getModelScaleY() {
        return modelScaleY;
    }

    /**
     * Returns the model Z scale.
     *
     * @return The Z scale.
     */
    public int getModelScaleZ() {
        return modelScaleZ;
    }

    /**
     * Returns the team id, if present.
     *
     * @return The team id.
     */
    public OptionalInt getTeamId() {
        return teamId;
    }

    /**
     * Returns the weight.
     *
     * @return The weight.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Returns whether this item is tradeable.
     *
     * @return {@code true} if tradeable.
     */
    public boolean isTradeable() {
        return tradeable;
    }
}
