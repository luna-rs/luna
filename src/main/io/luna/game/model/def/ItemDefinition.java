package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import io.luna.game.model.item.Item;

/**
 * A cached definition that describes a specific {@link Item}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ItemDefinition {

    /**
     * An array of the cached {@code ItemDefinition}s.
     */
    public static final ItemDefinition[] DEFINITIONS = new ItemDefinition[7956];

    /**
     * The identifier for this {@code ItemDefinition}.
     */
    private final int id;

    /**
     * The name of this {@code ItemDefinition}.
     */
    private final String name;

    /**
     * The description of this {@code ItemDefinition}.
     */
    private final String examine;

    /**
     * The equipment slot of this {@code ItemDefinition}.
     */
    private final int equipmentSlot;

    /**
     * The flag that determines if this {@code ItemDefinition} is noteable.
     */
    private final boolean noteable;

    /**
     * The flag that determines if this {@code ItemDefinition} is stackable.
     */
    private final boolean stackable;

    /**
     * The special store value of this {@code ItemDefinition}.
     */
    private final int specialValue;

    /**
     * The general store value of this {@code ItemDefinition}.
     */
    private final int generalValue;

    /**
     * The low alchemy value of this {@code ItemDefinition}.
     */
    private final int lowAlchValue;

    /**
     * The high alchemy value of this {@code ItemDefinition}.
     */
    private final int highAlchValue;

    /**
     * The weight value of this {@code ItemDefinition}.
     */
    private final double weight;

    /**
     * The {@link ImmutableList} of bonuses for this {@code ItemDefinition}.
     */
    private final ImmutableList<Integer> bonus;

    /**
     * The flag that determines if this {@code ItemDefinition} is two-handed.
     */
    private final boolean twoHanded;

    /**
     * The flag that determines if this {@code ItemDefinition} is a full helmet.
     */
    private final boolean fullHelm;

    /**
     * The flag that determines if this {@code ItemDefinition} is a platebody.
     */
    private final boolean platebody;

    /**
     * The flag that determines if this {@code ItemDefinition} is tradeable.
     */
    private final boolean tradeable;

    /**
     * Creates a new {@link ItemDefinition}.
     *
     * @param id The identifier for this {@code ItemDefinition}.
     * @param name The name of this {@code ItemDefinition}.
     * @param examine The description of this {@code ItemDefinition}.
     * @param equipmentSlot The equipment slot of this {@code ItemDefinition}.
     * @param noteable The flag that determines if this {@code ItemDefinition} is noteable.
     * @param stackable The flag that determines if this {@code ItemDefinition} is stackable.
     * @param specialValue The special store value of this {@code ItemDefinition}.
     * @param generalValue The general store value of this {@code ItemDefinition}.
     * @param lowAlchValue The low alchemy value of this {@code ItemDefinition}.
     * @param highAlchValue The high alchemy value of this {@code ItemDefinition}.
     * @param weight The weight value of this {@code ItemDefinition}.
     * @param bonus The array of bonuses for this {@code ItemDefinition}.
     * @param twoHanded The flag that determines if this {@code ItemDefinition} is two-handed.
     * @param fullHelm The flag that determines if this {@code ItemDefinition} is a full helmet.
     * @param platebody The flag that determines if this {@code ItemDefinition} is a platebody.
     * @param tradeable The flag that determines if this {@code ItemDefinition} is tradeable.
     */
    public ItemDefinition(int id, String name, String examine, int equipmentSlot, boolean noteable, boolean stackable, int specialValue, int generalValue, int lowAlchValue, int highAlchValue, double weight, int[] bonus, boolean twoHanded, boolean fullHelm, boolean platebody, boolean tradeable) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.equipmentSlot = equipmentSlot;
        this.noteable = noteable;
        this.stackable = stackable;
        this.specialValue = specialValue;
        this.generalValue = generalValue;
        this.lowAlchValue = lowAlchValue;
        this.highAlchValue = highAlchValue;
        this.weight = weight;
        this.bonus = ImmutableList.copyOf(Ints.asList(bonus));
        this.twoHanded = twoHanded;
        this.fullHelm = fullHelm;
        this.platebody = platebody;
        this.tradeable = tradeable;
    }

    /**
     * @return The identifier for {@code ItemDefinition}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The name of {@code ItemDefinition}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of {@code ItemDefinition}.
     */
    public String getExamine() {
        return examine;
    }

    /**
     * @return The equipment slot of {@code ItemDefinition}.
     */
    public int getEquipmentSlot() {
        return equipmentSlot;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is noteable, {@code false} otherwise.
     */
    public boolean isNoteable() {
        return noteable;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is stackable, {@code false} otherwise.
     */
    public boolean isStackable() {
        return stackable;
    }

    /**
     * @return The special store value of {@code ItemDefinition}.
     */
    public int getSpecialValue() {
        return specialValue;
    }

    /**
     * @return The general store value of {@code ItemDefinition}.
     */
    public int getGeneralValue() {
        return generalValue;
    }

    /**
     * @return The low alchemy value of {@code ItemDefinition}.
     */
    public int getLowAlchValue() {
        return lowAlchValue;
    }

    /**
     * @return The high alchemy value of {@code ItemDefinition}.
     */
    public int getHighAlchValue() {
        return highAlchValue;
    }

    /**
     * @return The weight value of {@code ItemDefinition}.
     */
    public double getWeight() {
        return weight;
    }

    /**
     * @return The {@link ImmutableList} of bonuses for {@this {@code ItemDefinition}.
     */
    public ImmutableList<Integer> getBonus() {
        return bonus;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is two-handed, {@code false} otherwise.
     */
    public boolean isTwoHanded() {
        return twoHanded;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is a full helmet, {@code false} otherwise.
     */
    public boolean isFullHelm() {
        return fullHelm;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is a platebody, {@code false} otherwise.
     */
    public boolean isPlatebody() {
        return platebody;
    }

    /**
     * @return {@code true} if {@code ItemDefinition} is tradable, {@code false} otherwise.
     */
    public boolean isTradeable() {
        return tradeable;
    }
}
