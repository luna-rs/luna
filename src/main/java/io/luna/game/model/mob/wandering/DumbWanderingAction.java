package io.luna.game.model.mob.wandering;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.area.Area;
import io.luna.game.model.mob.Mob;
import io.luna.util.RandomUtils;
import io.luna.util.Rational;

/**
 * A very cheap "random-walk" wandering {@link Action} for mobs.
 * <p>
 * This implementation:
 * <ul>
 *     <li>Rolls a {@link WanderingFrequency} chance each cycle.</li>
 *     <li>If successful, attempts up to 5 times to pick a random direction.</li>
 *     <li>Moves exactly 1 tile if the destination is inside the wander {@link Area} and is traversable.</li>
 * </ul>
 * <p>
 * This style of wandering is best suited for small wander areas/radii (e.g., tight cages, small rooms, short idle NPCs),
 * because it can look jittery/aimless over large ranges and does not "path to a goal".
 * </p>
 *
 * @author lare96
 */
public final class DumbWanderingAction extends Action<Mob> {

    /**
     * The allowed wander area. The mob will never intentionally step outside this area.
     */
    private final Area area;

    /**
     * Controls how often this action attempts to take a step.
     */
    private final WanderingFrequency frequency;

    /**
     * Creates a new {@link DumbWanderingAction}.
     *
     * @param mob The mob that will wander.
     * @param area The bounds of the wander behavior.
     * @param frequency How often to attempt movement.
     */
    public DumbWanderingAction(Mob mob, Area area, WanderingFrequency frequency) {
        super(mob, ActionType.WEAK, true, 3);
        this.area = area;
        this.frequency = frequency;
    }

    @Override
    public boolean run() {
        Rational chance = frequency.getChance();
        if (!mob.isLocked() && RandomUtils.roll(chance)) {
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
        return false;
    }
}
