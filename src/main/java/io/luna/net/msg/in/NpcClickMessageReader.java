package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
import io.luna.game.event.Event;
import io.luna.game.event.impl.NpcClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFifthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcFourthClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent;
import io.luna.game.event.impl.NpcClickEvent.NpcThirdClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.function.BiFunction;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on NPC clicks.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcClickMessageReader extends GameMessageReader {

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
     *
     * @param player The player.
     * @param index The npc index.
     * @param evt The interaction event.
     */
    private void handleClick(Player player, int index, BiFunction<Player, Npc, NpcClickEvent> evt) {
        World world = player.getWorld();
        checkState(index >= 0 && index < world.getNpcs().capacity(), "index[" + index + "] out of range");

        Npc npc = world.getNpcs().get(index);
        player.submitAction(new InteractionAction(player, npc) {
            @Override
            public void execute() {
                player.getPlugins().post(evt.apply(player, npc));
            }
        });
    }

    /**
     * Click the first index of an npc.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private void firstIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ByteOrder.LITTLE);
        handleClick(player, index, NpcFirstClickEvent::new);
    }

    /**
     * Click the second index of an npc.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private void secondIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ValueType.ADD, ByteOrder.LITTLE);
        handleClick(player, index, NpcSecondClickEvent::new);
    }

    /**
     * Click the third index of an npc.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private void thirdIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(ValueType.ADD);
        handleClick(player, index, NpcThirdClickEvent::new);
    }

    /**
     * Click the fourth index of an npc.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private void fourthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false);
        handleClick(player, index, NpcFourthClickEvent::new);
    }

    /**
     * Click the fifth index of an npc.
     *
     * @param player The player.
     * @param msg The buffer to read from.
     */
    private void fifthIndex(Player player, ByteMessage msg) {
        int index = msg.getShort(false, ByteOrder.LITTLE);
        handleClick(player, index, NpcFifthClickEvent::new);
    }
}

