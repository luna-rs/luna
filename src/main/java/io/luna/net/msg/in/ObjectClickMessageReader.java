package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.ObjectClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent;
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;
import io.luna.game.model.object.ObjectType;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent object clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ObjectClickMessageReader extends GameMessageReader {

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
     *
     * @param player The player.
     * @param evt The interaction event.
     */
    private void handleClick(Player player, ObjectClickEvent evt) {
        checkState(evt.getX() >= 0, "x coordinate out of range");
        checkState(evt.getY() >= 0, "y coordinate out of range");
        checkState(evt.getId() > 0, "id out of range");

        // TODO Validate that an object really exists at 'position'. This can only be done after cache loading.
        Position position = new Position(evt.getX(), evt.getY(), player.getPosition().getZ());
        GameObject object = new GameObject(player.getContext(), evt.getId(), position, ObjectType.DEFAULT, ObjectDirection.WEST, Optional.empty());
        player.submitAction(new InteractionAction(player, object) {
            @Override
            public void execute() {
                player.getPlugins().post(evt);
            }
        });
    }

    /**
     * Handle an object click for the first index.
     */
    private void firstIndex(Player player, ByteMessage msg) {
        int x = msg.getShort(true, ValueType.ADD, ByteOrder.LITTLE);
        int id = msg.getShort(false);
        int y = msg.getShort(false, ValueType.ADD);
        handleClick(player, new ObjectFirstClickEvent(player, id, x, y));
    }

    /**
     * Handle an object click for the second index.
     */
    private void secondIndex(Player player, ByteMessage msg) {
        int id = msg.getShort(false, ValueType.ADD, ByteOrder.LITTLE);
        int y = msg.getShort(true, ByteOrder.LITTLE);
        int x = msg.getShort(false, ValueType.ADD);
        handleClick(player, new ObjectSecondClickEvent(player, id, x, y));
    }

    /**
     * Handle an object click for the third index.
     */
    private void thirdIndex(Player player, ByteMessage msg) {
        int x = msg.getShort(true, ByteOrder.LITTLE);
        int y = msg.getShort(false);
        int id = msg.getShort(false, ValueType.ADD, ByteOrder.LITTLE);
        handleClick(player, new ObjectThirdClickEvent(player, id, x, y));
    }
}
