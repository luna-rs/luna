package io.luna.game.model.mob.movement.wandering;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

/**
 * A very cheap "random-walk" wandering {@link WanderingAction} for mobs.
 * <p>
 * This implementation:
 * <ul>
 *     <li>Rolls a {@link WanderingFrequency} chance each cycle.</li>
 *     <li>If successful, attempts up to 5 times to pick a random direction.</li>
 *     <li>Moves exactly 1 tile if the destination is inside the wander {@link Area} and is traversable.</li>
 * </ul>
 * <p>
 * This style of wandering is best suited for small wander areas (e.g., tight cages, small rooms, short idle NPCs),
 * because it can look jittery/aimless over large ranges and does not "path to a goal".
 * </p>
 *
 * @author lare96
 */
public final class DumbWanderingAction extends WanderingAction {

    /**
     * The allowed wander area. Displaced NPCs step back toward this area before resuming local wandering.
     */
    private final Area area;

    /**
     * Creates a new {@link DumbWanderingAction}.
     *
     * @param mob The mob that will wander.
     * @param area The bounds of the wander behavior.
     * @param frequency How often to attempt movement.
     */
    public DumbWanderingAction(Mob mob, Area area, WanderingFrequency frequency) {
        super(mob, frequency);
        this.area = area;
    }

    @Override
    public void wander() {
        Rational chance = frequency.getChance();
        if (RandomUtils.roll(chance)) {
            if (mob instanceof Npc && !area.contains(mob.getPosition())) {
                returnToWanderArea();
                return;
            }
            // Try a few times to find a valid 1-tile step without over-spending CPU.
            for (int loop = 0; loop < 5; loop++) {
                Direction nextDirection = Direction.random();
                Position nextPosition = mob.getPosition().translate(1, nextDirection);

                // Stay inside wander bounds and respect collision/pathing rules.
                if (area.contains(nextPosition) && mob.getNavigator().step(nextDirection)) {
                    break;
                }
            }
        }
    }

    /**
     * Takes one collision-checked step toward a spawn-area destination, following Lost City's wander recovery.
     * A displaced NPC must be allowed to cross tiles outside its wander area to reach it again.
     */
    private void returnToWanderArea() {
        Position destination = area.randomPosition();
        Position position = mob.getPosition();
        if (position.getZ() != destination.getZ()) {
            return;
        }
        Direction direction = Direction.between(position, destination);
        if (mob.getNavigator().step(direction) || !direction.isDiagonal()) {
            return;
        }
        // Match naive NPC movement: try the horizontal component before the vertical component.
        Direction horizontal = direction.getTranslateX() > 0 ? Direction.EAST : Direction.WEST;
        Direction vertical = direction.getTranslateY() > 0 ? Direction.NORTH : Direction.SOUTH;
        if (!mob.getNavigator().step(horizontal)) {
            mob.getNavigator().step(vertical);
        }
    }
}
