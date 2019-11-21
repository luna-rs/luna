package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.CastOnPlayerEvent;
import io.luna.game.model.mob.MobList;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

/**
 * A {@link GameMessageReader} that generates and submits a {@link CastOnPlayerEvent} when a player attempts to cast
 * any spell onto
 * another player.
 *
 * @see io.luna.game.action.ActionManager#submit
 */
public final class CastOnPlayerMessageReader extends GameMessageReader {

    @Override
    public Event read(Player caster, GameMessage msg) {
        int targetPlayerId = msg.getPayload().getShort(false, ValueType.ADD); // the id of the player
        // targeted
        int targetSpellId = msg.getPayload().getShort(false, ByteOrder.LITTLE); // the id of the spell casted

        MobList<Player> players = caster.getWorld().getPlayers();
        Optional<Player> targetPlayer = players.retrieve(targetPlayerId);

        if (targetPlayer.isEmpty()) {
            throw new InvalidSpellTargetException(targetPlayerId);
        }

        return new CastOnPlayerEvent(caster, targetSpellId, targetPlayerId);
    }

    private static class InvalidSpellTargetException extends RuntimeException {
        InvalidSpellTargetException(int targetPlayerId) {
            super("Player target with id:" + targetPlayerId + " no longer exists.");
        }
    }
}