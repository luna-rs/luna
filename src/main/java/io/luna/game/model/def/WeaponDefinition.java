package io.luna.game.model.def;

import io.luna.game.model.mob.combat.Weapon;
import io.luna.game.model.mob.combat.WeaponPoison;

/**
 * A {@link Definition} describing the combat-related weapon metadata for a specific item id.
 * <p>
 * Weapon definitions map an item to its {@link Weapon} type, optional {@link WeaponPoison} state, and the
 * {@link WeaponModelAnimationDefinition} used to determine model animation behavior.
 *
 * @author lare96
 */
public final class WeaponDefinition implements Definition {

    /**
     * The repository containing all loaded {@link WeaponDefinition} instances, keyed by item id.
     */
    public static final MapDefinitionRepository<WeaponDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The item id this weapon definition belongs to.
     */
    private final int id;

    /**
     * The weapon combat type for this item.
     */
    private final Weapon type;

    /**
     * The poison type applied by this weapon.
     */
    private final WeaponPoison poison;

    /**
     * The model and animation metadata associated with this weapon.
     */
    private final WeaponModelAnimationDefinition model;

    /**
     * The cached weapon type def.
     */
    private WeaponTypeDefinition typeDef;

    /**
     * Creates a new {@link WeaponDefinition}.
     *
     * @param id The item id this definition belongs to.
     * @param type The weapon type for the weapon.
     * @param poison The poison type for the weapon.
     * @param model The model and animation metadata for the weapon.
     */
    public WeaponDefinition(int id, Weapon type, WeaponPoison poison, WeaponModelAnimationDefinition model) {
        this.id = id;
        this.type = type;
        this.poison = poison;
        this.model = model;
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
     * @return The cached weapon type def.
     */
    public WeaponTypeDefinition getTypeDef() {
        if (typeDef == null) {
            typeDef = WeaponTypeDefinition.ALL.get(type);
        }
        return typeDef;
    }

    /**
     * @return The poison type.
     */
    public WeaponPoison getPoison() {
        return poison;
    }

    /**
     * @return The model and animation definition.
     */
    public WeaponModelAnimationDefinition getModel() {
        return model;
    }
}