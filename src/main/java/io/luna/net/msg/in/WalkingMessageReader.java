package io.luna.net.msg.in;

import io.luna.game.event.impl.WalkingEvent;
import io.luna.game.event.impl.WalkingEvent.WalkingOrigin;
import io.luna.game.model.Direction;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.ArrayDeque;
import java.util.Deque;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A {@link GameMessageReader} implementation that intercepts player movement data when performing minimap, yellow,
 * and red clicks.
 *
 * @author lare96
 */
public final class WalkingMessageReader extends GameMessageReader<WalkingEvent> {

    /**
     * The maximum number of compressed route points sent by the native client.
     */
    private static final int MAX_WAYPOINTS = 25;

    /**
     * The width and length of the client's loaded scene, in tiles.
     */
    private static final int SCENE_SIZE = 104;

    /**
     * The maximum expanded route length, including movement retraced within the loaded scene.
     */
    private static final int MAX_ROUTE_STEPS = SCENE_SIZE * SCENE_SIZE;

    @Override
    public WalkingEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        int size = msg.getSize();
        ByteMessage payload = msg.getPayload();

        if (opcode == 213) { // Minimap click.
            size -= 14;
        } else if (opcode == 28) { // Yellow <x> click.
        } else if (opcode == 247) { // Red <x> click.
        }

        checkArgument(size >= 5 && (size - 5) % 2 == 0 && size <= MAX_WAYPOINTS * 2 + 3,
                "Invalid walking packet size: %s", size);
        int pathSize = (size - 5) / 2;
        Deque<Position> steps = new ArrayDeque<>(pathSize + 1);
        int plane = player.getZ();

        int firstStepX = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        boolean running = payload.get() == 1;
        int firstStepY = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        steps.add(new Position(firstStepX, firstStepY, plane));
        for (int i = 0; i < pathSize; i++) {
            int offsetX = payload.get();
            int offsetY = payload.get();
            steps.add(new Position(firstStepX + offsetX, firstStepY + offsetY, plane));
        }
        return new WalkingEvent(player, steps, WalkingOrigin.forOpcode(opcode), running);
    }

    @Override
    public boolean validate(Player player, WalkingEvent event) {
        // Check if we're movement or action locked, or if no steps were recorded.
        if (player.isActionLocked() || player.getWalking().isLocked() || event.getSteps().isEmpty() ||
                event.getSteps().size() > MAX_WAYPOINTS) {
            return false;
        }

        // If we're immobilized, short-circuit here.
        if (player.getStatus().isImmobilized()) {
            player.sendMessage("A magical force prevents you from moving.");
            if(event.getOrigin() != WalkingOrigin.INTERACTION) {
                return false;
            }
            // Clear steps either way, so InteractionAction can proceed (if we're in range).
            event.getSteps().clear();
        }

        // A packet contains compressed corners, not the client's starting position. After a teleport its first
        // corner can still belong to the old scene. Never join that route through unloaded tiles or walls.
        Position region = player.getLastRegion();
        Position start = player.getPosition();
        if (region == null || !insideScene(start, region)) {
            return false;
        }
        CollisionManager collisionManager = player.getWorld().getCollisionManager();
        boolean noclip = player.isDebugNoclipEnabled();
        int plane = player.getZ();
        for (Position waypoint : event.getSteps()) {
            if (waypoint.getZ() != plane || !insideScene(waypoint, region)) {
                return false;
            }
        }
        var planned = player.getWalking().planPath(event.getSteps(), noclip, MAX_ROUTE_STEPS, (from, to) ->
                insideScene(to, region) && (noclip || collisionManager.traversable(from, player.getType(),
                        Direction.between(from, to), player.size(), player.getRouteStrategy())));
        if (planned.isEmpty()) {
            return false;
        }
        event.setPlannedPath(planned.get());
        return true;
    }

    /**
     * Checks that a route point belongs to the scene most recently sent to the client.
     *
     * @param position The route point.
     * @param region The client's scene base.
     * @return Whether the point is inside the loaded scene.
     */
    private static boolean insideScene(Position position, Position region) {
        int x = position.getLocalX(region);
        int y = position.getLocalY(region);
        return x >= 0 && x < SCENE_SIZE && y >= 0 && y < SCENE_SIZE;
    }

    @Override
    public void handle(Player player, WalkingEvent event) {
        // Only interrupt actions for non-interaction clicks.
        player.getOverlays().closeWindows(event.getOrigin() != WalkingOrigin.INTERACTION);
    }
}
