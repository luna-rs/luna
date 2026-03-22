package io.luna.game.model.def;

import com.google.common.collect.ImmutableSet;
import io.luna.game.model.LocalProjectile;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.block.Graphic;

import java.util.function.BiFunction;

public class AmmoDefinition implements Definition {
    public static final MapDefinitionRepository<AmmoDefinition> ALL = new MapDefinitionRepository<>();

    private final int id;
    /**
     * The strength of this ranged ammo.
     */
    private final int strength;

    private final Graphic startGraphic;
    private final Graphic endGraphic;
    /**
     * The projectile identification for this ranged ammo.
     */
    private final BiFunction<Mob, Mob, LocalProjectile> projectile;


    private final ImmutableSet<Integer> bows;

    public AmmoDefinition(int id, int strength, Graphic startGraphic, Graphic endGraphic,
                          BiFunction<Mob, Mob, LocalProjectile> projectile, ImmutableSet<Integer> bows) {
        this.id = id;
        this.strength = strength;
        this.startGraphic = startGraphic;
        this.endGraphic = endGraphic;
        this.projectile = projectile;
        this.bows = bows;
    }

    @Override
    public int getId() {
        return id;
    }

    public int getStrength() {
        return strength;
    }

    public Graphic getStartGraphic() {
        return startGraphic;
    }

    public Graphic getEndGraphic() {
        return endGraphic;
    }

    public BiFunction<Mob, Mob, LocalProjectile> getProjectile() {
        return projectile;
    }

    public ImmutableSet<Integer> getBows() {
        return bows;
    }
}
