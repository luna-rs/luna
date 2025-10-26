package io.luna.net.msg.in;

import io.luna.game.event.impl.WalkingEvent;
import io.luna.game.event.impl.WalkingEvent.WalkingOrigin;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

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
        return new WalkingEvent(player, WalkingOrigin.forOpcode(opcode), firstStepX, firstStepY, path, pathSize,
                running);
    }

    @Override
    public boolean validate(Player player, WalkingEvent event) {
        return !player.getWalking().isLocked() && !player.isLocked();
    }

    @Override
    public void handle(Player player, WalkingEvent event) {
        player.getInterfaces().close();
    }
}
