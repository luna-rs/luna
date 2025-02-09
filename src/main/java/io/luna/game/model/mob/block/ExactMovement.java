package io.luna.game.model.mob.block;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * A model representing a route for forced player movement.
 *
 * @author lare96
 */
public final class ExactMovement {

    /**
     * The starting position.
     */
    private final Position startPosition;

    /**
     * The destination position.
     */
    private final Position endPosition;

    /**
     * The start duration.
     */
    private final int durationStart;

    /**
     * The movement duration.
     */
    private final int durationEnd;

    /**
     * The face direction.
     */
    private final Direction direction;

    /**
     * Creates a new {@link ExactMovement} for movement across the x-axis.
     *
     * @param player The player.
     * @param amount The amount to move.
     * @param durationTicks The duration.
     * @return The exact movement instance.
     */
    public static ExactMovement toX(Player player, int amount, int durationTicks) {
        return to(player, amount, 0, durationTicks);
    }

    /**
     * Creates a new {@link ExactMovement} for movement across the y-axis.
     *
     * @param player The player.
     * @param amount The amount to move.
     * @param durationTicks The duration.
     * @return The exact movement instance.
     */
    public static ExactMovement toY(Player player, int amount, int durationTicks) {
        return to(player, 0, amount, durationTicks);
    }

    /**
     * Creates a new {@link ExactMovement} for movement across both the x and y axis.
     *
     * @param player The player.
     * @param amountX The x amount to move.
     * @param amountY The y amount to move.
     * @param durationTicks The duration.
     * @return The exact movement instance.
     */
    public static ExactMovement to(Player player, int amountX, int amountY, int durationTicks) {
        Position position = player.getPosition();
        Position destination = player.getPosition().translate(amountX, amountY);
        durationTicks = (durationTicks * 600) / 30;

        return new ExactMovement(position, destination, 0, durationTicks, Direction.between(position, destination));
    }

    /**
     * Creates a new {@link ExactMovement}.
     *
     * @param startPosition The starting position.
     * @param endPosition The destination position.
     * @param durationStart The start duration.
     * @param durationEnd The movement duration.
     * @param direction The face direction.
     */
    public ExactMovement(Position startPosition, Position endPosition, int durationStart, int durationEnd, Direction direction) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.durationStart = durationStart;
        this.durationEnd = durationEnd;
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
     * @return The start duration.
     */
    public int getDurationStart() {
        return durationStart;
    }

    /**
     * @return The movement duration.
     */
    public int getDurationEnd() {
        return durationEnd;
    }

    /**
     * @return The face direction.
     */
    public Direction getDirection() {
        return direction;
    }
}
