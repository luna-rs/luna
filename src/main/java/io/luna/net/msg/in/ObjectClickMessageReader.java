package io.luna.net.msg.in;

import io.luna.game.action.DistancedAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ObjectFirstClickEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that decodes data sent when a player clicks an object.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ObjectClickMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
        case 132:
            firstIndex(player, msg.getPayload());
            break;
        case 252:
            secondIndex(player, msg.getPayload());
            break;
        case 70:
            thirdIndex(player, msg.getPayload());
            break;
        }
        return null;
    }

    /**
     * Handle an object click for any index.
     */
    private void handleClick(Player player, int id, int x, int y) {
        checkState(x >= 0, "x coordinate out of range");
        checkState(y >= 0, "y coordinate out of range");
        checkState(id > 0, "id out of range");

        // TODO: Size calculations
        // TODO: Make sure object really exists
        Position position = new Position(x, y, player.getPosition().getZ());
        player.submitAction(new DistancedAction<Player>(player, position, 1, true) {
            @Override
            protected void execute() {
                player.face(position);
                player.getWalkingQueue().clear();

                PluginManager plugins = player.getPlugins();
                plugins.post(new ObjectFirstClickEvent(id, x, y), player);
            }
        });
    }

    /**
     * Handle an object click for the first index.
     */
    private void firstIndex(Player player, ByteMessage msg) {
        int x = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int id = msg.getShort(false);
        int y = msg.getShort(false, ByteTransform.A);
        handleClick(player, id, x, y);
    }

    /**
     * Handle an object click for the second index.
     */
    private void secondIndex(Player player, ByteMessage msg) {
        int x = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int id = msg.getShort(false);
        int y = msg.getShort(false, ByteTransform.A);
        handleClick(player, id, x, y);
    }

    /**
     * Handle an object click for the third index.
     */
    private void thirdIndex(Player player, ByteMessage msg) {
        int x = msg.getShort(true, ByteTransform.A, ByteOrder.LITTLE);
        int id = msg.getShort(false);
        int y = msg.getShort(false, ByteTransform.A);
        handleClick(player, id, x, y);
    }
}
