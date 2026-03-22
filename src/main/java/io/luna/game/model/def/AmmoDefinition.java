package io.luna.game.model.def;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.AmmoType;

import java.util.function.BiFunction;

/**
 * A {@link Definition} describing a ranged ammunition type.
 * <p>
 * Ammo definitions centralize all behavior and metadata needed for ranged combat resolution. Definitions are
 * registered in {@link #ALL} and are expected to be looked up by item id.
 *
 * @author lare96
 */
public final class AmmoDefinition implements Definition {

    /**
     * A dummy {@link AmmoDefinition} used for deserialization purposes.
     */
    public static final AmmoDefinition NULL = new AmmoDefinition(-1, null, -1, null, null, null, ImmutableSet.of());

    /**
     * Global repository containing every loaded {@link AmmoDefinition}, keyed by ammo item id.
     */
    public static final MapDefinitionRepository<AmmoDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The backing item id for this ammo definition.
     */
    private final int id;

    /**
     * The logical ammo category.
     */
    private final AmmoType type;

    /**
     * The ranged strength bonus provided by this ammo.
     */
    private final int strength;

    /**
     * The graphic displayed when this ammo is fired.
     */
    private final Graphic startGraphic;

    /**
     * The graphic displayed when this ammo reaches or hits its target.
     */
    private final Graphic endGraphic;

    /**
     * Factory used to create the travelling projectile for this ammo.
     * <p>
     * The first argument is the firing mob and the second argument is the target mob.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectile;

    /**
     * The set of bow item ids that can fire this ammo.
     */
    private final ImmutableSet<Integer> bows;

    /**
     * Creates a new {@link AmmoDefinition}.
     *
     * @param id The backing item id for this ammo.
     * @param type The logical ammo category.
     * @param strength The ranged strength bonus provided by this ammo.
     * @param startGraphic The graphic displayed when this ammo is fired.
     * @param endGraphic The graphic displayed when this ammo reaches or hits its target.
     * @param projectile The projectile factory used to create the travelling projectile.
     * @param bows The set of compatible bow item ids.
     */
    public AmmoDefinition(int id, AmmoType type, int strength, Graphic startGraphic, Graphic endGraphic,
                          BiFunction<Mob, Mob, LocalProjectile> projectile, ImmutableSet<Integer> bows) {
        this.id = id;
        this.type = type;
        this.strength = strength;
        this.startGraphic = startGraphic;
        this.endGraphic = endGraphic;
        this.projectile = projectile;
        this.bows = bows;
    }

    /**
     * @return The ammo item id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The ammo type.
     */
    public AmmoType getType() {
        return type;
    }

    /**
     * @return The ranged strength bonus.
     */
    public int getStrength() {
        return strength;
    }

    /**
     * @return The starting projectile graphic.
     */
    public Graphic getStartGraphic() {
        return startGraphic;
    }

    /**
     * @return The ending projectile graphic.
     */
    public Graphic getEndGraphic() {
        return endGraphic;
    }

    /**
     * @return The projectile factory.
     */
    public BiFunction<Mob, Mob, LocalProjectile> getProjectile() {
        return projectile;
    }

    /**
     * @return The compatible bow ids.
     */
    public ImmutableSet<Integer> getBows() {
        return bows;
    }
}