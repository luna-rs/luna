package io.luna.net.msg.in;

import io.luna.game.action.DistancedAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.NpcClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFifthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFourthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcThirdClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.plugin.PluginManager;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ByteTransform;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import java.util.function.Function;

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
            firstIndex(player, msg.getPayload());
            break;
        case 17:
            secondIndex(player, msg.getPayload());
            break;
        case 72:
            thirdIndex(player, msg.getPayload());
            break;
        case 21:
            fourthIndex(player, msg.getPayload());
            break;
        case 18:
            fifthIndex(player, msg.getPayload());
            break;
        }
        return null;
    }

    /**
     * Handles any npc click index.
     */
    private void handleClick(Player player, int index, Function<Npc, NpcClickEvent> evt) {
        World world = player.getWorld();
        checkState(index >= 0 && index < world.getNpcs().capacity(), "index[" + index + "] out of range");

        Npc npc = world.getNpcs().get(index);
        player.submitAction(new DistancedAction<Player>(player, npc.getPosition(), npc.size(), true) {
            @Override
            protected void execute() {
                PluginManager plugins = player.getPlugins();
                plugins.post(evt.apply(npc), player);

                player.interact(npc);
            }
        });
    }

    /**
     * Click the first index of an npc.
     */
    private void firstIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteOrder.LITTLE);
        handleClick(player, index, NpcFirstClickEvent::new);
    }

    /**
     * Click the second index of an npc.
     */
    private void secondIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A, ByteOrder.LITTLE);
        handleClick(player, index, NpcSecondClickEvent::new);
    }

    /**
     * Click the third index of an npc.
     */
    private void thirdIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteTransform.A);
        handleClick(player, index, NpcThirdClickEvent::new);
    }

    /**
     * Click the fourth index of an npc.
     */
    private void fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false);
        handleClick(player, index, NpcFourthClickEvent::new);
    }

    /**
     * Click the fifth index of an npc.
     */
    private void fifthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false, ByteOrder.LITTLE);
        handleClick(player, index, NpcFifthClickEvent::new);
    }
}

