package io.luna.game.model.mob.block;

import com.google.common.base.Objects;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;

/**
 * Represents a forced (exact) movement update for a player.
 * <p>
 * Exact movement is used by the 317 protocol to move a player to a specific coordinate using an interpolated
 * slide animation, rather than the normal tile-by-tile walking mechanics. This is used for:
 * </p>
 *
 * <ul>
 *     <li>Pushing or pulling a player (e.g., agility obstacles).</li>
 *     <li>Cutscenes or scripted movements.</li>
 *     <li>Forced directional movement such as telekinetic effects.</li>
 * </ul>
 *
 * @author lare96
 */
public final class ExactMovement {

    /**
     * The last region-base position for the player when this movement was created.
     * <p>
     * Exact movement coordinates are encoded relative to the client's current region base. Storing {@code lastRegion}
     * allows the server to translate {@link #startPosition} and {@link #endPosition} into the correct local offsets
     * for the 317 protocol, even if the player has recently crossed region boundaries.
     * </p>
     */
    private final Position lastRegion;

    /**
     * The starting position before the forced movement begins.
     */
    private final Position startPosition;

    /**
     * The end position where the player is forced to move.
     */
    private final Position endPosition;

    /**
     * Delay before the movement begins (in ticks).
     */
    private final int durationStart;

    /**
     * Duration of the forced movement itself (in ticks).
     */
    private final int durationEnd;

    /**
     * The direction the player should face while moving.
     */
    private final Direction direction;

    /**
     * Creates an {@link ExactMovement} moving along the x-axis.
     *
     * @param player The player being moved.
     * @param amount The x-offset.
     * @param durationTicks The number of ticks the movement should take.
     * @return The resulting forced movement.
     */
    public static ExactMovement toX(Player player, int amount, int durationTicks) {
        return to(player, amount, 0, durationTicks);
    }

    /**
     * Creates an {@link ExactMovement} moving along the y-axis.
     *
     * @param player The player being moved.
     * @param amount The y-offset.
     * @param durationTicks The number of ticks the movement should take.
     * @return The resulting forced movement.
     */
    public static ExactMovement toY(Player player, int amount, int durationTicks) {
        return to(player, 0, amount, durationTicks);
    }

    /**
     * Creates an {@link ExactMovement} along both axes.
     *
     * <p>
     * Duration is scaled by protocol requirements (600ms units vs 30-tick cycles).
     * </p>
     *
     * @param player The player being moved.
     * @param amountX X-offset.
     * @param amountY Y-offset.
     * @param durationTicks Movement duration (ticks).
     * @return The resulting forced movement.
     */
    public static ExactMovement to(Player player, int amountX, int amountY, int durationTicks) {
        Position pos = player.getPosition();
        Position dest = pos.translate(amountX, amountY);
        durationTicks = (durationTicks * 600) / 30; // Convert to client’s expected movement units.
        return new ExactMovement(player.getLastRegion(),
                pos, dest, 0, durationTicks, Direction.between(pos, dest));
    }

    /**
     * Creates forced movement to a position using movement distance as duration.
     *
     * @param player The player.
     * @param destination The destination.
     * @return The resulting forced movement.
     */
    public static ExactMovement to(Player player, Position destination) {
        Position pos = player.getPosition();
        int durationTicks = pos.computeLongestDistance(destination);
        durationTicks = (durationTicks * 600) / 30;
        return new ExactMovement(player.getLastRegion(),
                pos, destination, 0, durationTicks, Direction.between(pos, destination));
    }

    /**
     * Creates forced movement to a position with a provided duration.
     *
     * @param player The player.
     * @param destination The end position.
     * @param durationTicks The duration.
     * @return The resulting forced movement.
     */
    public static ExactMovement to(Player player, Position destination, int durationTicks) {
        Position pos = player.getPosition();
        return new ExactMovement(player.getLastRegion(),
                pos, destination, 0, durationTicks, Direction.between(pos, destination));
    }

    /**
     * Creates a new {@link ExactMovement}.
     *
     * @param lastRegion The last region-base position for the player.
     * @param startPosition Starting position.
     * @param endPosition Destination.
     * @param durationStart Start delay.
     * @param durationEnd Movement duration.
     * @param direction Facing direction.
     */
    public ExactMovement(Position lastRegion, Position startPosition, Position endPosition,
                         int durationStart, int durationEnd, Direction direction) {
        this.lastRegion = lastRegion;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.durationStart = durationStart;
        this.durationEnd = durationEnd;
        this.direction = direction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExactMovement)) return false;
        ExactMovement m = (ExactMovement) o;
        return durationStart == m.durationStart &&
                durationEnd == m.durationEnd &&
                Objects.equal(startPosition, m.startPosition) &&
                Objects.equal(endPosition, m.endPosition) &&
                direction == m.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(startPosition, endPosition, durationStart, durationEnd, direction);
    }

    /**
     * Returns the region-base position used when this forced movement was created.
     *
     * @return The last region-base {@link Position} for the player.
     */
    public Position getLastRegion() {
        return lastRegion;
    }

    /**
     * @return The start position.
     */
    public Position getStartPosition() {
        return startPosition;
    }

    /**
     * @return The end position.
     */
    public Position getEndPosition() {
        return endPosition;
    }

    /**
     * @return Delay before movement begins.
     */
    public int getDurationStart() {
        return durationStart;
    }

    /**
     * @return Duration of the forced movement.
     */
    public int getDurationEnd() {
        return durationEnd;
    }

    /**
     * @return Direction the player faces during movement.
     */
    public Direction getDirection() {
        return direction;
    }
}
