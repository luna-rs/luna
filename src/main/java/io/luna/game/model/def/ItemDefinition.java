package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import io.luna.util.StringUtils;

import java.util.OptionalInt;

/**
 * A definition model describing an item.
 *
 * @author lare96
 */
public final class ItemDefinition implements Definition {

    /**
     * The definition count.
     */
    public static final int SIZE = 7956;

    /**
     * The item definition repository.
     */
    public static final DefinitionRepository<ItemDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The item name.
     */
    private final String name;

    /**
     * The item description.
     */
    private final String description;

    /**
     * The item model identifier.
     */
    private final int modelId;

    /**
     * The item model zoom.
     */
    private final int modelZoom;

    /**
     * The item model {@code x} rotation.
     */
    private final int modelRotationX;

    /**
     * The item model {@code y} rotation.
     */
    private final int modelRotationY;

    /**
     * The item model rotation {@code z} coordinate.
     */
    private final int modelRotationZ;

    /**
     * The item model {@code x} offset.
     */
    private final int modelOffsetX;

    /**
     * The item model {@code y} offset.
     */
    private final int modelOffsetY;

    /**
     * If the item is stackable.
     */
    private final boolean stackable;

    /**
     * The item value.
     */
    private final int value;

    /**
     * If the item is members only.
     */
    private final boolean members;

    /**
     * The item inventory actions.
     */
    private final ImmutableList<String> inventoryActions;

    /**
     * The item ground actions.
     */
    private final ImmutableList<String> groundActions;

    /**
     * The item's unnoted identifier, if it has one.
     */
    private final OptionalInt unnotedId;

    /**
     * The item's noted identifier, if it has one.
     */
    private OptionalInt notedId = OptionalInt.empty();

    /**
     * The item model {@code x} scale.
     */
    private final int modelScaleX;

    /**
     * The item model {@code y} scale.
     */
    private final int modelScaleY;

    /**
     * The item model {@code z} scale.
     */
    private final int modelScaleZ;

    /**
     * The team id of this item, if it has one.
     */
    private final OptionalInt teamId;

    /**
     * The weight of this item.
     */
    private final double weight;

    /**
     * If this item is tradeable.
     */
    private final boolean tradeable;

    /**
     * Creates a new {@link ItemDefinition}.
     *
     * @param id The item identifier.
     * @param name The item name.
     * @param description The item description.
     * @param modelId The item mode identifier.
     * @param modelZoom The item model zoom.
     * @param modelRotationX The item model {@code x} rotation.
     * @param modelRotationY The item model {@code y} rotation.
     * @param modelRotationZ The item model {@code z} rotation.
     * @param modelOffsetX The item model {@code x} offset.
     * @param modelOffsetY The item model {@code y} offset.
     * @param stackable If the item is stackable.
     * @param value The item value.
     * @param members If the item is members only.
     * @param inventoryActions The item inventory actions.
     * @param groundActions The item ground actions.
     * @param unnotedId The item's unnoted identifier, if it has one.
     * @param modelScaleX The item model {@code x} scale.
     * @param modelScaleY The item model {@code y} scale.
     * @param modelScaleZ The item model {@code z} scale.
     * @param teamId The team id of this item, if it has one.
     * @param weight The weight of this item.
     * @param tradeable If this item is tradeable.
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

        if (unnotedId.isPresent()) {
            notedId = OptionalInt.of(id);
        }
    }

    /**
     * Determines if {@code id} is valid.
     *
     * @param id The identifier.
     * @return {@code true} if the identifier is valid.
     */
    public static boolean isIdValid(int id) {
        return id > 0 && id < SIZE;
    }

    /**
     * Determines if the inventory action at {@code index} is equal to {@code action}.
     *
     * @param index The action index.
     * @param action The action to compare.
     * @return {@code true} if the actions are equal.
     */
    public boolean hasInventoryAction(int index, String action) {
        return action.equals(inventoryActions.get(index));
    }

    /**
     * Determines if the ground action at {@code index} is equal to {@code action}.
     *
     * @param index The action index.
     * @param action The action to compare.
     * @return {@code true} if the actions are equal.
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
     * Converts this definition into a proper noted definition, and modifies the unnoted definition to reference it.
     *
     * @param unnotedDef The unnoted item definition data.
     * @return The new noted definition.
     */
    public ItemDefinition toNote(ItemDefinition unnotedDef) {
        unnotedDef.notedId = OptionalInt.of(id);
        String description = "Swap this note at any bank for " + StringUtils.addArticle(unnotedDef.name) + ".";
        return new ItemDefinition(id, unnotedDef.name, description,
                modelId, modelZoom, modelRotationX, modelRotationY,
                modelRotationZ, modelOffsetX, modelOffsetY, true, unnotedDef.value, unnotedDef.members,
                Iterables.toArray(inventoryActions, String.class), Iterables.toArray(groundActions, String.class),
                unnotedId, modelScaleX, modelScaleY, modelScaleZ, teamId, weight, true);
    }

    /**
     * @return {@code true} if this definition is noted.
     */
    public boolean isNoted() {
        return unnotedId.isPresent() && notedId.isPresent();
    }

    /**
     * @return The item name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The item description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The item model identifier.
     */
    public int getModelId() {
        return modelId;
    }

    /**
     * @return The item model zoom.
     */
    public int getModelZoom() {
        return modelZoom;
    }

    /**
     * @return The item model {@code x} rotation.
     */
    public int getModelRotationX() {
        return modelRotationX;
    }

    /**
     * @return The item model {@code y} rotation.
     */
    public int getModelRotationY() {
        return modelRotationY;
    }

    /**
     * @return The item model {@code z} rotation.
     */
    public int getModelRotationZ() {
        return modelRotationZ;
    }

    /**
     * @return The item model {@code x} offset.
     */
    public int getModelOffsetX() {
        return modelOffsetX;
    }

    /**
     * @return The item model {@code y} offset.
     */
    public int getModelOffsetY() {
        return modelOffsetY;
    }

    /**
     * @return If the item is stackable.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * @return The item value.
     */
    public int getValue() {
        return value;
    }

    /**
     * @return If the item is members only.
     */
    public boolean isMembers() {
        return members;
    }

    /**
     * @return The item inventory actions.
     */
    public ImmutableList<String> getInventoryActions() {
        return inventoryActions;
    }

    /**
     * @return The item ground actions.
     */
    public ImmutableList<String> getGroundActions() {
        return groundActions;
    }

    /**
     * @return The item's unnoted identifier, if it has one.
     */
    public OptionalInt getUnnotedId() {
        return unnotedId;
    }

    /**
     * @return The item's noted identifier, if it has one.
     */
    public OptionalInt getNotedId() {
        return notedId;
    }

    /**
     * @return The item model {@code x} scale.
     */
    public int getModelScaleX() {
        return modelScaleX;
    }

    /**
     * @return The item model {@code y} scale.
     */
    public int getModelScaleY() {
        return modelScaleY;
    }

    /**
     * @return The item model {@code z} scale.
     */
    public int getModelScaleZ() {
        return modelScaleZ;
    }

    /**
     * @return The team id of this item, if it has one.
     */
    public OptionalInt getTeamId() {
        return teamId;
    }

    /**
     * @return The weight of this item.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * @return If this item is tradeable.
     */
    public boolean isTradeable() {
        return tradeable;
    }
}
