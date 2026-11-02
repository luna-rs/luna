package io.luna.game.model.mob;

import io.luna.game.model.EntityState;
import io.luna.game.model.EntityType;
import io.luna.game.model.World;

/**
 * Identifies a registered mob without owning its object or session. Slot reuse cannot redirect the reference to a
 * different mob, even when the replacement player has the same username.
 *
 * @param world The world containing the indexed mob.
 * @param type The mob's list type.
 * @param index The slot occupied when this reference was created.
 * @param incarnation The unique identity of that mob instance.
 * @author lare96
 */
public record MobReference(World world, EntityType type, int index, long incarnation) {

    /**
     * Returns the mob's cached reference. Unregistered mobs have no world identity to resolve.
     *
     * @param mob The mob to reference, or {@code null}.
     * @return Its cached reference, or {@code null}.
     */
    public static MobReference capture(Mob mob) {
        return mob == null ? null : mob.getReference();
    }

    /**
     * Resolves this reference only while the original mob occupies its slot and remains active.
     *
     * @return The original live mob, or {@code null} after removal or slot replacement.
     */
    public Mob resolve() {
        Mob mob = type == EntityType.NPC ? world.getNpcs().get(index) : world.getPlayers().get(index);
        return mob != null && mob.getIndex() == index && mob.getIncarnation() == incarnation &&
                mob.getState() == EntityState.ACTIVE ? mob : null;
    }
}
