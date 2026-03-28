package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.model.item.Equipment.EquipmentBonus;
import io.luna.game.model.mob.combat.CombatStance;
import io.luna.game.model.mob.combat.CombatStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A sub-definition within a {@link WeaponTypeDefinition} describing a single selectable combat style.
 * <p>
 * combat style definitions map a {@link CombatStyle} to the attack speed, animation, interface config, accuracy bonus,
 * stance, and experience distribution used when that style is selected.
 *
 * @author lare96
 */
public final class CombatStyleDefinition {

    /**
     * Adds a pending {@link CombatStyleDefinition} to be published when {@link #lock()} is invoked.
     *
     * @param def The definition to add.
     */
    static void addWeaponStyle(CombatStyleDefinition def) {
        pending.put(def.type, def);
    }

    /**
     * Publishes all pending combat style definitions into {@link #ALL}.
     * <p>
     * This method only performs the publication step once, ignoring subsequent calls after the immutable map has
     * been initialized.
     */
    static void lock() {
        if (ALL.isEmpty()) {
            ALL = ImmutableMap.copyOf(pending);
            pending.clear();
        }
    }

    /**
     * All published {@link CombatStyleDefinition} instances, keyed by {@link CombatStyle}.
     */
    public static volatile ImmutableMap<CombatStyle, CombatStyleDefinition> ALL = ImmutableMap.of();

    /**
     * Pending combat style definitions awaiting publication into {@link #ALL}.
     */
    private static final Map<CombatStyle, CombatStyleDefinition> pending = new ConcurrentHashMap<>();

    /**
     * The combat style this definition belongs to.
     */
    private final CombatStyle type;

    /**
     * The attack speed, in ticks, for this combat style.
     */
    private final int speed;

    /**
     * The attack animation id used by this combat style.
     */
    private final int animation;

    /**
     * The interface config value used to display this combat style.
     */
    private final int config;

    /**
     * The equipment bonus used for accuracy calculations with this combat style.
     */
    private final EquipmentBonus bonus;

    /**
     * The button used by this combat style.
     */
    private final int button;

    /**
     * The combat stance used by this combat style.
     */
    private final CombatStance stance;

    /**
     * The effective range of this combat style.
     */
    private final int range;

    /**
     * The skill ids that receive experience from this combat style.
     */
    private final ImmutableList<Integer> exp;

    /**
     * Creates a new {@link CombatStyleDefinition}.
     *
     * @param type The combat style this definition belongs to.
     * @param speed The attack speed, in ticks.
     * @param animation The attack animation id.
     * @param config The interface config value.
     * @param bonus The equipment bonus used for accuracy calculations.
     * @param button The button used by this combat style.
     * @param stance The combat stance.
     * @param range The effective range of this combat style.
     * @param exp The skill ids that receive experience.
     */
    public CombatStyleDefinition(CombatStyle type, int speed, int animation, int config, EquipmentBonus bonus, int button,
                                 CombatStance stance, int range, ImmutableList<Integer> exp) {
        this.type = type;
        this.speed = speed;
        this.animation = animation;
        this.config = config;
        this.bonus = bonus;
        this.button = button;
        this.stance = stance;
        this.range = range;
        this.exp = exp;
    }

    /**
     * @return The combat style.
     */
    public CombatStyle getType() {
        return type;
    }

    /**
     * @return The attack speed.
     */
    public int getSpeed() {
        return speed;
    }

    /**
     * @return The attack animation id.
     */
    public int getAnimation() {
        return animation;
    }

    /**
     * @return The interface config value.
     */
    public int getConfig() {
        return config;
    }

    /**
     * @return The equipment bonus.
     */
    public EquipmentBonus getBonus() {
        return bonus;
    }

    /**
     * @return The button used by this combat style.
     */
    public int getButton() {
        return button;
    }

    /**
     * @return The combat stance.
     */
    public CombatStance getStance() {
        return stance;
    }

    /**
     * @return The effective range.
     */
    public int getRange() {
        return range;
    }

    /**
     * @return The experience skill ids.
     */
    public ImmutableList<Integer> getExp() {
        return exp;
    }
}