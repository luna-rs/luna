package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.NpcFifthClickEvent;
import io.luna.game.event.impl.NpcFirstClickEvent;
import io.luna.game.event.impl.NpcFourthClickEvent;
import io.luna.game.event.impl.NpcSecondClickEvent;
import io.luna.game.event.impl.NpcThirdClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link MessageReader} implementation that decodes data sent when a player clicks an npc.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcClickMessageReader extends MessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int opcode = msg.getOpcode();
        switch (opcode) {
        case 155:
            return firstIndex(player, msg.getPayload());
        case 72:
            return secondIndex(player, msg.getPayload());
        case 17:
            return thirdIndex(player, msg.getPayload());
        case 21:
            return fourthIndex(player, msg.getPayload());
        case 18:
            return fifthIndex(player, msg.getPayload());
        }
        return null;
    }

    /**
     * Retrieve the instance of the npc being clicked.
     */
    private Npc retrieveNpc(Player player, int index) {
        World world = player.getWorld();

        checkState(index >= 0 && index < world.getNpcs().capacity(), "index out of range");
        return world.getNpcs().get(index);
    }

    /**
     * Click the first index of an npc.
     */
    private Event firstIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteOrder.LITTLE);
        return new NpcFirstClickEvent(retrieveNpc(player, index));
    }

    /**
     * Click the second index of an npc.
     */
    private Event secondIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A);
        return new NpcSecondClickEvent(retrieveNpc(player, index));
    }

    /**
     * Click the third index of an npc.
     */
    private Event thirdIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A, ByteOrder.LITTLE);
        return new NpcThirdClickEvent(retrieveNpc(player, index));
    }

    /**
     * Click the fourth index of an npc.
     */
    private Event fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false);
        return new NpcFourthClickEvent(retrieveNpc(player, index));
    }

    /**
     * Click the fifth index of an npc.
     */
    private Event fifthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false, ByteOrder.LITTLE);
        return new NpcFifthClickEvent(retrieveNpc(player, index));
    }
}

