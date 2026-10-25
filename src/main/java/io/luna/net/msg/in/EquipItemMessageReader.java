package io.luna.net.msg.in;

import io.luna.game.event.impl.EquipItemEvent;
import io.luna.game.event.impl.ItemClickEvent.ItemSecondClickEvent;
import io.luna.game.event.impl.PlayerEvent;
import io.luna.game.model.def.EquipmentDefinition;
import io.luna.game.model.def.ItemDefinition;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts the second inventory option, including equipping items.
 *
 * @author lare96
 */
public final class EquipItemMessageReader extends GameMessageReader<PlayerEvent> {

    @Override
    public PlayerEvent decode(Player player, GameMessage msg) {
        int interfaceId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int itemId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int index = msg.getPayload().getShort(false, ValueType.ADD);
        if (EquipmentDefinition.ALL.get(itemId).isPresent()) {
            return new EquipItemEvent(player, index, itemId, interfaceId);
        }
        return new ItemSecondClickEvent(player, itemId, index, interfaceId);
    }

    @Override
    public boolean validate(Player player, PlayerEvent event) {
        int itemId;
        int index;
        int interfaceId;
        if (event instanceof EquipItemEvent equip) {
            itemId = equip.getItemId();
            index = equip.getIndex();
            interfaceId = equip.getInterfaceId();
        } else if (event instanceof ItemSecondClickEvent click) {
            itemId = click.getId();
            index = click.getIndex();
            interfaceId = click.getInterfaceId();
        } else {
            return false;
        }
        return ItemDefinition.isIdValid(itemId) && index >= 0 && interfaceId == 3214 &&
                player.getInventory().contains(index, itemId);
    }

    @Override
    public void handle(Player player, PlayerEvent event) {
        if (event instanceof ItemSecondClickEvent) {
            player.getOverlays().closeWindows();
        }
    }
}
