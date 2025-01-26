package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that intercepts data sent for when a player uses magic on an inventory item.
 *
 * @author lare96
 */
public final class MagicOnItemMessageReader extends GameMessageReader<MagicOnItemEvent> {

    @Override
    public MagicOnItemEvent decode(Player player, GameMessage msg) {
        int spellId = msg.getPayload().getShort(false);
        int itemInterfaceId = msg.getPayload().getShort(false, ValueType.ADD);
        int itemIndex = msg.getPayload().getShort(false, ValueType.ADD);
        int itemId = msg.getPayload().getShort(false, ValueType.ADD);
        return new MagicOnItemEvent(player, spellId, itemInterfaceId, itemIndex, itemId);
    }

    @Override
    public boolean validate(Player player, MagicOnItemEvent event) {
        if (event.getTargetItemInterface() == 3214) {
            return player.getInventory().contains(event.getTargetItemIndex(), event.getTargetItemId());
        }
        return false;
    }

    @Override
    public void handle(Player player, MagicOnItemEvent event) {
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[MagicOnItemEvent]: spellId: " + event.getSpellId() + ", widget: " +
                    event.getTargetItemInterface() + ", index: " + event.getTargetItemIndex() + ", id: " + event.getTargetItemId());
        }
    }
}
