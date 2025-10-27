package io.luna.net.msg.in;

import io.luna.game.event.impl.GroundItemClickEvent;
import io.luna.game.event.impl.GroundItemClickEvent.GroundItemSecondClickEvent;
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent;
import io.luna.game.model.Position;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on ground item clicks.
 *
 * @author lare96
 */
public final class GroundItemClickMessageReader extends GameMessageReader<GroundItemClickEvent> {

    @Override
    public GroundItemClickEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        int itemId;
        int itemX;
        int itemY;
        switch (opcode) {
            case 54:
                itemId = msg.getPayload().getShort(false, ValueType.ADD);
                itemY = msg.getPayload().getShort(false, ByteOrder.LITTLE);
                itemX = msg.getPayload().getShort(false);
                return new GroundItemSecondClickEvent(player, findItem(player, itemId, itemX, itemY));
            case 71:
                itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
                itemX = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
                itemY = msg.getPayload().getShort(false, ValueType.ADD);
                return new PickupItemEvent(player, findItem(player, itemId, itemX, itemY));
        }
        throw new IllegalStateException("Invalid opcode [" + opcode + "]");
    }

    @Override
    public boolean validate(Player player, GroundItemClickEvent event) {
        return event.getGroundItem() != null;
    }

    /**
     * Finds the first available {@link GroundItem} on the specified coordinates matching {@code id}.
     *
     * @return The ground item found, {@code null} if nothing was found.
     */
    private GroundItem findItem(Player player, int id, int x, int y) {
        Position itemPosition = new Position(x, y, player.getPosition().getZ());
        return player.getWorld().getItems().findAll(itemPosition).
                filter(item -> item.getId() == id &&
                        item.isVisibleTo(player)).findFirst().orElse(null);
    }
}
