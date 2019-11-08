package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CastOnPlayerEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

public final class CastOnPlayerMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) {
        int targetPlayerId = msg.getPayload().getShort(false, ValueType.ADD, ByteOrder.LITTLE); // the id of the player
        // targeted
        int targetSpellId = msg.getPayload().getShort(false, ValueType.ADD); // the id of the spell casted

        if (!targetExistsInWorld(targetPlayerId, player.getWorld())) {
            throw new InvalidSpellTargetException(targetPlayerId);
        }
        return new CastOnPlayerEvent(player, targetSpellId, targetPlayerId);
    }

    private boolean targetExistsInWorld(int targetPlayerId, World world) {
        return world.getPlayers().stream().anyMatch(player -> player.getDatabaseId() == targetPlayerId);
    }

    private static class InvalidSpellTargetException extends RuntimeException {
        InvalidSpellTargetException(int targetPlayerId) {
            super("Target no longer exists.");
        }
    }

}
