package io.luna.game.model.mob.block;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * A model representing a route for forced player movement.
 *
 * @author lare96
 */
public final class ForcedMovement {

    /**
     * The starting position.
     */
    private final Position startPosition;

    /**
     * The destination position.
     */
    private final Position endPosition;

    /**
     * The movement duration along the x-axis.
     */
    private final int durationX;

    /**
     * The movement duration along the y-axis.
     */
    private final int durationY;

    /**
     * The movement direction.
     */
    private final Direction direction;

    /**
     * Creates a new {@link ForcedMovement} for movement across the x-axis.
     *
     * @param player The player.
     * @param amount The amount to move.
     * @param duration The duration.
     * @return The forced movement instance.
     */
    public static ForcedMovement forceMoveX(Player player, int amount, int duration) {
        Position destination = player.getPosition().translate(amount, 0);
        Direction direction = amount < 0 ? Direction.WEST : Direction.EAST;

        return new ForcedMovement(player.getPosition(), destination, duration, 0, direction);
    }

    /**
     * Creates a new {@link ForcedMovement} for movement across the y-axis.
     *
     * @param player The player.
     * @param amount The amount to move.
     * @param duration The duration.
     * @return The forced movement instance.
     */
    public static ForcedMovement forceMoveY(Player player, int amount, int duration) {
        Position destination = player.getPosition().translate(0, amount);
        Direction direction = amount < 0 ? Direction.SOUTH : Direction.NORTH;

        return new ForcedMovement(player.getPosition(), destination, 0, duration, direction);
    }

    /**
     * Creates a new {@link ForcedMovement}.
     *
     * @param startPosition The starting position.
     * @param endPosition The destination position.
     * @param durationX The movement duration along the x-axis.
     * @param durationY The movement duration along the y-axis.
     * @param direction The movement direction.
     */
    public ForcedMovement(Position startPosition, Position endPosition, int durationX, int durationY,
        Direction direction) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.durationX = durationX;
        this.durationY = durationY;
        this.direction = direction;
    }

    /**
     * @return The starting position.
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * @return The destination position.
     */
    public Position getEndPosition() {
        return endPosition;
    }

    /**
     * @return The movement duration along the x-axis..
     */
    public int getDurationX() {
        return durationX;
    }

    /**
     * @return The movement duration along the y-axis.
     */
    public int getDurationY() {
        return durationY;
    }

    /**
     * @return The movement direction.
     */
    public Direction getDirection() {
        return direction;
    }
}
