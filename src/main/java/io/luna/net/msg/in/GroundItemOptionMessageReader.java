package io.luna.net.msg.in;

import io.luna.game.event.impl.GroundItemOptionEvent;
import io.luna.game.model.Position;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts the first (opcode 77) and second (opcode 100) ground
 * options of items that define their own ground options.
 *
 * @author TheLining
 */
public final class GroundItemOptionMessageReader extends GameMessageReader<GroundItemOptionEvent> {

    @Override
    public GroundItemOptionEvent decode(Player player, GameMessage msg) {
        int opcode = msg.getOpcode();
        int itemX;
        int itemY;
        int option;
        switch (opcode) {
            case 77:
                itemX = msg.getPayload().getShort(false, ValueType.ADD);
                itemY = msg.getPayload().getShort(false);
                option = 0;
                break;
            case 100:
                itemX = msg.getPayload().getShort(false);
                itemY = msg.getPayload().getShort(false, ValueType.ADD);
                option = 1;
                break;
            default:
                throw new IllegalStateException("Invalid opcode [" + opcode + "]");
        }
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        return new GroundItemOptionEvent(player, findItem(player, itemId, itemX, itemY), option);
    }

    @Override
    public boolean validate(Player player, GroundItemOptionEvent event) {
        return event.getGroundItem() != null;
    }

    /**
     * Finds the first visible {@link GroundItem} on the specified coordinates matching {@code id}.
     *
     * @return The ground item found, {@code null} if nothing was found.
     */
    private GroundItem findItem(Player player, int id, int x, int y) {
        Position itemPosition = new Position(x, y, player.getPosition().getZ());
        return player.getWorld().getItems().findAll(itemPosition).
                filter(item -> item.getId() == id && item.isVisibleTo(player)).findFirst().orElse(null);
    }
}
