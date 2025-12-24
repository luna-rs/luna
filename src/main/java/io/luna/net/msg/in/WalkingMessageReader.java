package io.luna.net.msg.in;

import io.luna.Luna;
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

/**
 * A {@link GameMessageReader} implementation that intercepts player movement data when performing minimap, yellow,
 * and red clicks.
 *
 * @author lare96
 */
public final class WalkingMessageReader extends GameMessageReader<WalkingEvent> {

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

        int pathSize = (size - 5) / 2;
        Deque<Position> steps = new ArrayDeque<>(pathSize);

        int firstStepX = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        boolean running = payload.get() == 1;
        int firstStepY = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        steps.add(new Position(firstStepX, firstStepY));
        for (int i = 0; i < pathSize; i++) {
            int offsetX = payload.get();
            int offsetY = payload.get();
            steps.add(new Position(firstStepX + offsetX, firstStepY + offsetY));
        }
        return new WalkingEvent(player, steps, WalkingOrigin.forOpcode(opcode), running);
    }

    @Override
    public boolean validate(Player player, WalkingEvent event) {
        // Don't bother checking in DEVELOPMENT/BETA mode.
        if (Luna.settings().game().betaMode()) {
            return true;
        }

        // Check if we're movement or action locked, or if no steps were recorded.
        if (player.getWalking().isLocked() || player.isLocked() || event.getSteps().isEmpty()) {
            return false;
        }

        // Check if all steps in the path are valid. A path is considered invalid if it paths through non-traversable
        // areas. Disabled in non-production modes so ::noclip can be used.
        CollisionManager collisionManager = player.getWorld().getCollisionManager();
        Position lastStep = player.getPosition();
        for(Position step : event.getSteps()) {
            Direction stepDir = Direction.between(lastStep, step);
            if(stepDir != Direction.NONE && !collisionManager.traversable(lastStep, player.getType(), stepDir)) {
                // A step in the path was not traversable.
                return false;
            }
            lastStep = step;
        }
        return true;
    }

    @Override
    public void handle(Player player, WalkingEvent event) {
        player.getOverlays().closeWindows();
    }
}
