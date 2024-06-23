package io.luna.net.msg.in;

import io.luna.game.event.impl.NpcClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFifthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFourthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcThirdClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on NPC clicks.
 *
 * @author lare96
 */
public final class NpcClickMessageReader extends GameMessageReader<NpcClickEvent> {

    @Override
    public NpcClickEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int opcode = msg.getOpcode();
        ByteMessage payload = msg.getPayload();
        int npcIndex;
        switch (opcode) {
            case 112:
                npcIndex = payload.getShort(ByteOrder.LITTLE);
                return new NpcFirstClickEvent(player, world.getNpcs().get(npcIndex));
            case 13:
                npcIndex = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                return new NpcSecondClickEvent(player, world.getNpcs().get(npcIndex));
            case 67:
                npcIndex = payload.getShort(ValueType.ADD);
                return new NpcThirdClickEvent(player, world.getNpcs().get(npcIndex));
            case 42:
                npcIndex = payload.getShort(false);
                return new NpcFourthClickEvent(player, world.getNpcs().get(npcIndex));
            case 8:
                npcIndex = payload.getShort(false, ByteOrder.LITTLE);
                return new NpcFifthClickEvent(player, world.getNpcs().get(npcIndex));
        }
        throw new IllegalStateException("invalid opcode");
    }

    @Override
    public boolean validate(Player player, NpcClickEvent event) {
        return event.getTargetNpc() != null;
    }
}

