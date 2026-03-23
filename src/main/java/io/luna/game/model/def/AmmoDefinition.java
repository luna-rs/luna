package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;
import io.luna.game.model.mob.combat.AmmoType;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Defines a ranged ammunition profile, including its logical ammo type, ranged strength bonus, projectile graphics,
 * projectile factory, and the item ids that represent both the ammunition and its compatible weapons.
 * <p>
 * These definitions are also indexed into static lookup tables for fast combat resolution at runtime.
 *
 * @author lare96
 */
public final class AmmoDefinition {

    /**
     * All loaded ammo definitions keyed by their logical {@link AmmoType}.
     */
    public static volatile ImmutableMap<AmmoType, AmmoDefinition> ALL = ImmutableMap.of();

    /**
     * Lookup table of ammo item id to ammo definition for ammunition that requires a compatible weapon.
     */
    public static volatile ImmutableMap<Integer, AmmoDefinition> AMMO_REQUIRING_WEAPONS = ImmutableMap.of();

    /**
     * Lookup table of weapon item id to ammo definition for weapons that do not consume or require separate ammunition.
     */
    public static volatile ImmutableMap<Integer, AmmoDefinition> NO_AMMO_WEAPONS = ImmutableMap.of();

    /**
     * Loads all ammo definitions and rebuilds the static lookup tables used by ranged combat.
     * <p>
     * Definitions are indexed in three ways:
     * <ul>
     *   <li>Grouped by {@link AmmoType} in {@link #ALL}.</li>
     *   <li>By ammo item id in {@link #AMMO_REQUIRING_WEAPONS} when both ammo and compatible weapons are defined.</li>
     *   <li>By weapon item id in {@link #NO_AMMO_WEAPONS} when the weapon does not require separate ammo.</li>
     * </ul>
     *
     * @param definitions The ammo definitions to load.
     * @throws IllegalStateException If a definition has ammo ids but no compatible weapon ids.
     */
    public static void loadAll(List<AmmoDefinition> definitions) {
        ImmutableMap.Builder<AmmoType, AmmoDefinition> all = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, AmmoDefinition> normal = ImmutableMap.builder();
        ImmutableMap.Builder<Integer, AmmoDefinition> noAmmo = ImmutableMap.builder();

        for (AmmoDefinition def : definitions) {
            all.put(def.type, def);

            if (!def.ammo.isEmpty() && !def.weapons.isEmpty()) {
                // Standard ammo that must be paired with a compatible weapon.
                def.ammo.forEach(it -> normal.put(it, def));
            } else if (def.ammo.isEmpty() && !def.weapons.isEmpty()) {
                // Weapons that do not require separate ammo.
                def.weapons.forEach(it -> noAmmo.put(it, def));
            } else if (!def.ammo.isEmpty()) {
                // Ammo placed in the ammo slot must always have at least one
                // compatible weapon definition.
                throw new IllegalStateException("Ammo equipped to the ammo slot always requires a weapon.");
            }
        }

        ALL = all.build();
        AMMO_REQUIRING_WEAPONS = normal.build();
        NO_AMMO_WEAPONS = noAmmo.build();
    }

    /**
     * The logical ammo category.
     */
    private final AmmoType type;

    /**
     * The ranged strength bonus granted by this ammo definition.
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
     * Factory used to create the travelling projectile for this ammo definition.
     * <p>
     * The first argument is the firing mob and the second argument is the target mob.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectile;

    /**
     * The item ids that represent this ammunition.
     */
    public final ImmutableSet<Integer> ammo;

    /**
     * The compatible weapon item ids for this ammunition definition.
     */
    private final ImmutableSet<Integer> weapons;

    /**
     * Creates a new {@link AmmoDefinition}.
     *
     * @param type The logical ammo category.
     * @param strength The ranged strength bonus granted by this ammo definition.
     * @param startGraphic The graphic displayed when this ammo is fired.
     * @param endGraphic The graphic displayed when this ammo reaches or hits its target.
     * @param projectile The factory used to create the travelling projectile.
     * @param ammo The item ids that represent this ammunition.
     * @param weapons The compatible weapon item ids.
     */
    public AmmoDefinition(AmmoType type, int strength, Graphic startGraphic, Graphic endGraphic,
                          BiFunction<Mob, Mob, LocalProjectile> projectile, ImmutableSet<Integer> ammo,
                          ImmutableSet<Integer> weapons) {
        this.ammo = ammo;
        this.type = type;
        this.strength = strength;
        this.startGraphic = startGraphic;
        this.endGraphic = endGraphic;
        this.projectile = projectile;
        this.weapons = weapons;
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
     * @return The graphic displayed when the projectile is fired.
     */
    public Graphic getStartGraphic() {
        return startGraphic;
    }

    /**
     * @return The graphic displayed when the projectile reaches or hits its target.
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
     * @return The ammo item ids.
     */
    public ImmutableSet<Integer> getAmmo() {
        return ammo;
    }

    /**
     * @return The compatible weapon item ids.
     */
    public ImmutableSet<Integer> getWeapons() {
        return weapons;
    }
}