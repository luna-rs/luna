package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.UseSpellEvent.MagicOnObjectEvent;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that intercepts data sent for when a player uses magic on a {@link GameObject}.
 *
 * @author lare96
 */
public final class MagicOnObjectMessageReader extends GameMessageReader<MagicOnObjectEvent> {

    @Override
    public MagicOnObjectEvent decode(Player player, GameMessage msg) {
        int spellId = msg.getPayload().getShort(false);
        int objectId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        int objectX = msg.getPayload().getShort(false, ValueType.ADD);
        int objectY = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        GameObject gameObject = player.getWorld().getObjects().findAll(new Position(objectX, objectY)).
                filter(object -> object.getId() == objectId && object.isVisibleTo(player)).
                findFirst().orElse(null);
        return new MagicOnObjectEvent(player, spellId, gameObject);
    }

    @Override
    public boolean validate(Player player, MagicOnObjectEvent event) {
        return event.getTargetObject() != null;
    }

    @Override
    public void handle(Player player, MagicOnObjectEvent event) {
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[MagicOnObjectMessageReader]: spellId: " + event.getSpellId());
        }
    }
}
