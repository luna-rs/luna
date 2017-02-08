package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.WalkingEvent;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.WalkingQueue;
import io.luna.game.model.mobile.WalkingQueue.Step;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

/**
 * A {@link MessageReader} implementation that intercepts player movement data.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class WalkingMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        int size = msg.getSize();
        ByteMessage payload = msg.getPayload();

        if (opcode == 248) { // Minimap click.
            size -= 14;
            player.interruptAction();
        } else if (opcode == 164) { // Yellow <x> click.
            player.interruptAction();
        } else if (opcode == 98) { // Red <x> click.
            // impl
        }

        WalkingQueue walkingQueue = player.getWalkingQueue();
        if (walkingQueue.isLocked()) {
            return null;
        }

        int pathSize = (size - 5) / 2;
        int[][] path = new int[pathSize][2];

        int x = payload.getShort(false, ByteTransform.A, ByteOrder.LITTLE);
        for (int i = 0; i < pathSize; i++) {
            path[i][0] = payload.get();
            path[i][1] = payload.get();
        }
        int y = payload.getShort(false, ByteOrder.LITTLE);
        boolean running = payload.get(false, ByteTransform.S) == 1;

        walkingQueue.setRunningPath(running);
        walkingQueue.clear();
        Step[] steps = new Step[pathSize + 1];
        walkingQueue.addFirst(steps[0] = new Step(x, y));
        for (int i = 0; i < pathSize; i++) {
            walkingQueue.add(steps[i + 1] = new Step(path[i][0] + x, path[i][1] + y));
        }
        return new WalkingEvent(player, steps, running);
    }
}
