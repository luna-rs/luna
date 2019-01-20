package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.entity.player.WalkingEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue.Step;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts player movement data.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WalkingMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        int size = msg.getSize();
        var payload = msg.getPayload();
        var walkingQueue = player.getWalking();
        
        if (walkingQueue.isLocked()) {
            return null;
        }

        if (opcode == 248) { // Minimap click.
            size -= 14;
            player.interruptAction();
            player.resetInteractingWith();
        } else if (opcode == 164) { // Yellow <x> click.
            player.interruptAction();
            player.resetInteractingWith();
        } else if (opcode == 98) { // Red <x> click.
            // impl
        }
        
        player.getInterfaces().applyActionClose();

        int pathSize = (size - 5) / 2;
        int[][] path = new int[pathSize][2];

        int x = payload.getShort(false, ValueType.ADD, ByteOrder.LITTLE);
        
        for (int i = 0; i < pathSize; i++) {
            path[i][0] = payload.get();
            path[i][1] = payload.get();
        }
        
        int y = payload.getShort(false, ByteOrder.LITTLE);
        var isRunning = payload.get(false, ValueType.SUBTRACT) == 1;

        walkingQueue.setRunningPath(isRunning);
        walkingQueue.clear();
        
        var steps = new Step[pathSize + 1];
        
        walkingQueue.addFirst(steps[0] = new Step(x, y));
        
        for (int i = 0; i < pathSize; i++) {
            walkingQueue.add(steps[i + 1] = new Step(path[i][0] + x, path[i][1] + y));
        }
        
        return new WalkingEvent(player, steps, isRunning);
    }
}
