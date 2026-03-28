package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import io.luna.game.model.mob.combat.Weapon;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link Definition} describing the interface and combat style metadata for a {@link Weapon} type.
 * <p>
 * Weapon type definitions map a weapon category to its interface id, attack style line, available
 * {@link CombatStyleDefinition} entries, and an optional {@link WeaponSpecialBarDefinition}.
 *
 * @author lare96
 */
public final class WeaponTypeDefinition implements Definition {

    /**
     * Cached {@link WeaponTypeDefinition} for {@link Weapon#UNARMED}.
     * <p>
     * This field is initialized lazily on first access through {@link #getUnarmed()} in order to avoid resolving
     * the definition until it is actually needed.
     */
    private static WeaponTypeDefinition unarmed;

    /**
     * Returns the cached {@link WeaponTypeDefinition} for {@link Weapon#UNARMED}.
     * <p>
     * The value is resolved from {@link #ALL} on first access and then reused for all subsequent calls.
     *
     * @return The unarmed weapon type definition.
     */
    public static WeaponTypeDefinition getUnarmed() {
        if (unarmed == null) {
            unarmed = ALL.get(Weapon.UNARMED);
        }
        return unarmed;
    }

    /**
     * Adds a pending {@link WeaponTypeDefinition} to be published when {@link #lock()} is invoked.
     * <p>
     * This also registers all child {@link CombatStyleDefinition} instances belonging to the weapon type.
     *
     * @param def The definition to add.
     */
    public static void addWeaponType(WeaponTypeDefinition def) {
        pending.put(def.type, def);
        for (CombatStyleDefinition style : def.getStyles()) {
            CombatStyleDefinition.addWeaponStyle(style);
        }
    }

    /**
     * Publishes all pending weapon type definitions into {@link #ALL}.
     * <p>
     * This method only performs the publication step once, ignoring subsequent calls after the immutable map has been
     * initialized.
     */
    public static void lock() {
        if (ALL.isEmpty()) {
            ALL = ImmutableMap.copyOf(pending);
            pending.clear();
            CombatStyleDefinition.lock();
        }
    }

    /**
     * All published {@link WeaponTypeDefinition} instances, keyed by {@link Weapon}.
     */
    public static volatile ImmutableMap<Weapon, WeaponTypeDefinition> ALL = ImmutableMap.of();

    /**
     * Pending weapon type definitions awaiting publication into {@link #ALL}.
     */
    private static final Map<Weapon, WeaponTypeDefinition> pending = new ConcurrentHashMap<>();

    /**
     * The weapon type this definition belongs to.
     */
    private final Weapon type;

    /**
     * The interface id for this weapon type definition.
     */
    private final int id;

    /**
     * The interface line used to display the selected combat style for this weapon type.
     */
    private final int line;

    /**
     * The selectable combat styles for this weapon type.
     */
    private final List<CombatStyleDefinition> styles;

    /**
     * The special attack bar metadata for this weapon type, or {@code null} if none exists.
     */
    private final WeaponSpecialBarDefinition special;

    /**
     * Creates a new {@link WeaponTypeDefinition}.
     *
     * @param type The weapon type this definition belongs to.
     * @param id The interface id for this weapon type.
     * @param line The interface line used for combat style display.
     * @param styles The selectable combat styles for this weapon type.
     * @param special The special attack bar metadata, or {@code null} if none exists.
     */
    public WeaponTypeDefinition(Weapon type, int id, int line, List<CombatStyleDefinition> styles, WeaponSpecialBarDefinition special) {
        this.type = type;
        this.id = id;
        this.line = line;
        this.styles = styles;
        this.special = special;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The weapon type.
     */
    public Weapon getType() {
        return type;
    }

    /**
     * @return The interface line.
     */
    public int getLine() {
        return line;
    }

    /**
     * @return The combat styles.
     */
    public List<CombatStyleDefinition> getStyles() {
        return styles;
    }

    /**
     * @return The special attack bar metadata, or {@code null} if none exists.
     */
    public WeaponSpecialBarDefinition getSpecial() {
        return special;
    }
}