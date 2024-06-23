package io.luna.net.msg.in;

import io.luna.game.event.impl.UseSpellEvent.MagicOnNpcEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that generates and submits a {@link MagicOnNpcEvent} when a player attempts to cast
 * any spell onto an npc.
 *
 * @author searledan
 * @author lare96
 * @see io.luna.game.action.ActionManager#submit
 */
public final class MagicOnNpcMessageReader extends GameMessageReader<MagicOnNpcEvent> {

    @Override
    public MagicOnNpcEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int npcIndex = msg.getPayload().getShort(false, ByteOrder.LITTLE, ValueType.ADD);
        int spellId = msg.getPayload().getShort(false, ValueType.ADD);
        return new MagicOnNpcEvent(player, spellId, world.getNpcs().get(npcIndex));
    }

    @Override
    public boolean validate(Player player, MagicOnNpcEvent event) {
        return event.getTargetNpc() != null;
    }
}
