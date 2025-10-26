package io.luna.net.msg.in;

import io.luna.game.event.impl.WalkingEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.WalkingQueue;
import io.luna.game.model.mob.WalkingQueue.Step;
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
        int[][] path = new int[pathSize][2];

        int firstStepX = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        boolean running = payload.get() == 1;
        int firstStepY = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
        for (int i = 0; i < pathSize; i++) {
            path[i][0] = payload.get();
            path[i][1] = payload.get();
        }

        Deque<Step> steps = new ArrayDeque<>(pathSize + 1);
        steps.add(new Step(firstStepX, firstStepY));
        for (int i = 0; i < pathSize; i++) {
            steps.add(new Step(path[i][0] + firstStepX, path[i][1] + firstStepY));
        }
        return new WalkingEvent(player, steps, running, pathSize, opcode);
    }

    @Override
    public boolean validate(Player player, WalkingEvent event) {
        return !player.getWalking().isLocked() && !player.isLocked();
    }
// todo engine plugin

    @Override
    public void handle(Player player, WalkingEvent event) {
        player.getInterfaces().close(); // todo when slayer shop is opened through dialogues, shop interface doesnt close
        // when clicking to move with minimap
        switch (event.getOpcode()) {
            case 213:
            case 28:
                player.resetInteractingWith();
                player.resetInteractionTask();
                break;
        }
        WalkingQueue walking = player.getWalking();
        walking.clear();
        walking.addPath(event.getPath());
        walking.setRunningPath(event.isRunning() || player.isRunning());
    }
}
