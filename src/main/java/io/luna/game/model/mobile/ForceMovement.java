package io.luna.game.model.mobile;

import io.luna.game.model.Direction;
import io.luna.game.model.Position;

/**
 * A container for the data that will be used to force a {@link Player} to move to a certain destination. For basic
 * movements, only the static factory methods are needed.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ForceMovement {

    // TODO: confirm this works

    /**
     * The {@link Position} the {@link Player} is moving from.
     */
    private final Position startPosition;

    /**
     * The {@link Position} the {@link Player} is moving to.
     */
    private final Position endPosition;

    /**
     * The time in ticks that it will take to move across the {@code X} axis.
     */
    private final int durationX;

    /**
     * The time in ticks that it will take to move across the {@code Y} axis.
     */
    private final int durationY;

    /**
     * The {@link Direction} that the {@link Player} is moving in.
     */
    private final Direction direction;

    /**
     * A static factory method that creates an {@code ForceMovement} instance that will move {@code player} across the {@code
     * X} axis.
     *
     * @param player The {@link Player} whose {@link Position} will be used as the starting {@code Position}.
     * @param movePosition The amount to move the {@code X} position of the {@code player} by.
     * @param duration The amount of time in ticks that it will take for the move to complete.
     * @return A {@code ForcedMovement} instance with these properties.
     */
    public static ForceMovement forceMoveX(Player player, int movePosition, int duration) {
        Position destination = player.getPosition().move(movePosition, 0);
        Direction direction = movePosition < 0 ? Direction.WEST : Direction.EAST;

        return new ForceMovement(player.getPosition(), destination, duration, 0, direction);
    }

    /**
     * A static factory method that creates an {@code ForceMovement} instance that will move {@code player} across the {@code
     * Y} axis.
     *
     * @param player The {@link Player} whose {@link Position} will be used as the starting {@code Position}.
     * @param movePosition The amount to move the {@code Y} position of the {@code player} by.
     * @param duration The amount of time in ticks that it will take for the move to complete.
     * @return A {@code ForcedMovement} instance with these properties.
     */
    public static ForceMovement forceMoveY(Player player, int movePosition, int duration) {
        Position destination = player.getPosition().move(0, movePosition);
        Direction direction = movePosition < 0 ? Direction.SOUTH : Direction.NORTH;

        return new ForceMovement(player.getPosition(), destination, 0, duration, direction);
    }

    /**
     * Creates a new {@link ForceMovement}.
     *
     * @param startPosition The {@link Position} the {@link Player} is moving from.
     * @param endPosition The {@link Position} the {@link Player} is moving to.
     * @param durationX The time in ticks that it will take to move across the {@code X} axis.
     * @param durationY The time in ticks that it will take to move across the {@code Y} axis.
     * @param direction The {@link Direction} that the {@link Player} is moving in.
     */
    public ForceMovement(Position startPosition, Position endPosition, int durationX, int durationY, Direction direction) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.durationX = durationX;
        this.durationY = durationY;
        this.direction = direction;
    }

    /**
     * @return The {@link Position} the {@link Player} is moving from.
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * @return The {@link Position} the {@link Player} is moving to.
     */
    public Position getEndPosition() {
        return endPosition;
    }

    /**
     * @return The time in ticks that it will take to move across the {@code X} axis.
     */
    public int getDurationX() {
        return durationX;
    }

    /**
     * @return The time in ticks that it will take to move across the {@code Y} axis.
     */
    public int getDurationY() {
        return durationY;
    }

    /**
     * @return The {@link Direction} that the {@link Player} is moving in.
     */
    public Direction getDirection() {
        return direction;
    }
}
