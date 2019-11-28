package io.luna.net.msg.in;

import io.luna.game.action.InteractionAction;
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
 * @author notjuanortiz
 * @see io.luna.game.action.ActionManager#submit
 */
public final class CastOnPlayerMessageReader extends GameMessageReader {

    @Override
    public Event read(Player caster, GameMessage msg) {
        int targetId = msg.getPayload().getShort(false, ValueType.ADD);
        int spellId = msg.getPayload().getShort(false, ByteOrder.LITTLE);

        Optional<Player> targetPlayer = findTarget(caster, targetId);
        if (targetPlayer.isPresent()) {
            Player target = targetPlayer.get();
            this.submitEventFor(target, new CastOnPlayerEvent(caster, spellId, target));
        }
        return null;
    }

    /**
     * Searches for the target player within the caster's {@link io.luna.game.model.World} instance.
     *
     * @param caster   The player casting the spell
     * @param targetId The target player id to search for.
     */
    private Optional<Player> findTarget(Player caster, int targetId) {
        MobList<Player> players = caster.getWorld().getPlayers();
        return players.retrieve(targetId);
    }

    private void submitEventFor(Player target, CastOnPlayerEvent event) {
        Player caster = event.getPlr();
        caster.submitAction(new InteractionAction(caster, target) {
            @Override
            public void execute() {
                caster.getPlugins().post(event);
            }
        });
    }
}
