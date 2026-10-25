package io.luna.game.model.def;

import io.luna.game.model.Position;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Spawn-relative combat bounds and absolute escape hitpoints from Lost City's NPC configuration.
 * An attack range of zero selects melee/op bounds; a positive range selects ranged or magic/ap bounds.
 *
 * @author lare96
 */
public record NpcCombatMovement(int maxRange, int attackRange, int retreatHitpoints) {

    /**
     * Lost City defaults: wander range five plus two chase tiles, melee, and no health escape.
     */
    public static final NpcCombatMovement DEFAULT = new NpcCombatMovement(7, 0, -1);

    /**
     * Validates movement configuration before it reaches the combat loop.
     */
    public NpcCombatMovement {
        checkArgument(maxRange >= 0 && maxRange <= 255, "Invalid NPC maximum range: %s", maxRange);
        checkArgument(attackRange >= 0 && attackRange <= 255, "Invalid NPC attack range: %s", attackRange);
        checkArgument(retreatHitpoints >= -1, "Invalid NPC retreat hitpoints: %s", retreatHitpoints);
    }

    /**
     * Tests the target's southwest tile against spawn, including the melee outer-corner exclusion.
     *
     * @param spawn The NPC's spawn position.
     * @param target The target position.
     * @return Whether the target is within this NPC's combat leash.
     */
    public boolean containsTarget(Position spawn, Position target) {
        if (spawn.getZ() != target.getZ()) {
            return false;
        }
        int dx = Math.abs(target.getX() - spawn.getX());
        int dy = Math.abs(target.getY() - spawn.getY());
        int range = maxRange + (attackRange > 0 ? attackRange : 1);
        return Math.max(dx, dy) <= range && (attackRange > 0 || dx != range || dy != range);
    }
}
