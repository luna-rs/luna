package io.luna.net.msg.in;

import io.luna.game.event.impl.PlayerClickEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerFifthClickEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerFourthClickEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerSecondClickEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerThirdClickEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent on Player interaction menu clicks.
 *
 * @author lare96
 */
public final class PlayerClickMessageReader extends GameMessageReader<PlayerClickEvent> {

    @Override
    public PlayerClickEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int opcode = msg.getOpcode();
        ByteMessage payload = msg.getPayload();
        int playerIndex;
        switch (opcode) {
            case 245:
                playerIndex = payload.getShort(ByteOrder.LITTLE, ValueType.ADD);
                return new PlayerFirstClickEvent(player, world.getPlayers().get(playerIndex));
            case 233:
                playerIndex = payload.getShort(ValueType.ADD);
                return new PlayerSecondClickEvent(player, world.getPlayers().get(playerIndex));
            case 194:
                playerIndex = payload.getShort(ByteOrder.LITTLE);
                return new PlayerThirdClickEvent(player, world.getPlayers().get(playerIndex));
            case 116:
                playerIndex = payload.getShort(ByteOrder.LITTLE);
                return new PlayerFourthClickEvent(player, world.getPlayers().get(playerIndex));
            case 45:
                playerIndex = payload.getShort(ValueType.ADD);
                return new PlayerFifthClickEvent(player, world.getPlayers().get(playerIndex));
        }
        throw new IllegalStateException("invalid opcode");
    }

    @Override
    public boolean validate(Player player, PlayerClickEvent event) {
        return event.getTargetPlr() != null;
    }
}