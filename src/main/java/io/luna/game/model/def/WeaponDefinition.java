package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import io.luna.game.model.mob.combat.Weapon;

import java.util.EnumMap;
import java.util.List;

/**
 * A {@link Definition} describing the combat-related weapon metadata for a specific item id.
 *
 * @author lare96
 */
public final class WeaponDefinition implements Definition {

    /**
     * An immutable lookup of weapon definitions keyed by {@link Weapon} type.
     * <p>
     * This map is rebuilt during {@link #loadAll(List)} after all definitions have been loaded.
     */
    private static volatile ImmutableMap<Weapon, WeaponDefinition> weapons = ImmutableMap.of();

    /**
     * Cached {@link WeaponDefinition} whose {@link #type} is {@link Weapon#UNARMED}.
     * <p>
     * This field is initialized lazily on first access through {@link #getUnarmed()} in order to avoid constructing
     * the fallback definition until it is actually needed.
     */
    private static WeaponDefinition unarmed;

    /**
     * The repository containing all loaded {@link WeaponDefinition} instances, keyed by item id.
     */
    public static final MapDefinitionRepository<WeaponDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * Loads and indexes all weapon definitions.
     * <p>
     * This stores the provided definitions in {@link #ALL}, then builds a secondary immutable map keyed by
     * {@link Weapon} type for direct type-based lookups.
     *
     * @param definitions The weapon definitions to load.
     */
    public static void loadAll(List<WeaponDefinition> definitions) {
        ALL.storeAndLock(definitions);

        EnumMap<Weapon, WeaponDefinition> weapons = new EnumMap<>(Weapon.class);
        for (WeaponDefinition def : definitions) {
            weapons.put(def.type, def);
        }
        WeaponDefinition.weapons = ImmutableMap.copyOf(weapons);
    }

    /**
     * @return The weapon definition map by weapon type.
     */
    public static ImmutableMap<Weapon, WeaponDefinition> getWeapons() {
        return weapons;
    }

    /**
     * Returns the cached {@link WeaponDefinition} for {@link Weapon#UNARMED}.
     * <p>
     * The value is resolved on first access by creating a synthetic definition whose {@link #type} matches
     * {@link Weapon#UNARMED}. The resolved definition is then cached and reused for all subsequent calls.
     *
     * @return The unarmed weapon definition.
     */
    public static WeaponDefinition getUnarmed() {
        if (unarmed == null) {
            unarmed = new WeaponDefinition(-1, Weapon.UNARMED);
        }
        return unarmed;
    }

    /**
     * The item id this weapon definition belongs to.
     */
    private final int id;

    /**
     * The weapon combat type for this item.
     */
    private final Weapon type;

    /**
     * Cached {@link WeaponTypeDefinition} resolved from {@link #type}.
     * <p>
     * This field is populated lazily on first access through {@link #getTypeDef()}.
     */
    private WeaponTypeDefinition typeDef;

    /**
     * Creates a new {@link WeaponDefinition}.
     *
     * @param id The item id this definition belongs to.
     * @param type The weapon type for the item.
     */
    public WeaponDefinition(int id, Weapon type) {
        this.id = id;
        this.type = type;
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
     * @return The cached weapon type definition, or {@code null} if none exists for this type.
     */
    public WeaponTypeDefinition getTypeDef() {
        if (typeDef == null) {
            typeDef = WeaponTypeDefinition.ALL.get(type);
        }
        return typeDef;
    }

    /**
     * @return The model animation definition, or {@code null} if none exists.
     */
    public WeaponModelAnimationDefinition getModel() {
        return null;
    }
}