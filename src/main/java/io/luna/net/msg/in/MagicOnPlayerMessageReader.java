package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.UseSpellEvent.MagicOnPlayerEvent;
import io.luna.game.model.World;
import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} that generates and submits a {@link MagicOnPlayerEvent} when a player attempts to cast
 * any spell onto another player.
 *
 * @author notjuanortiz
 * @see io.luna.game.action.ActionManager#submit
 */
public final class MagicOnPlayerMessageReader extends GameMessageReader<MagicOnPlayerEvent> {

    @Override
    public MagicOnPlayerEvent decode(Player player, GameMessage msg) {
        World world = player.getWorld();
        int playerIndex = msg.getPayload().getShort(false);
        int spellId = msg.getPayload().getShort(false, ByteOrder.LITTLE);
        return new MagicOnPlayerEvent(player, spellId, world.getPlayers().get(playerIndex));
    }

    @Override
    public boolean validate(Player player, MagicOnPlayerEvent event) {
        return event.getTargetPlr() != null;
    }

    @Override
    public void handle(Player player, MagicOnPlayerEvent event) {
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[MagicOnPlayerMessageReader]: spellId: " + event.getSpellId() +
                    ", player: " + event.getTargetPlr().getUsername());
        }
    }
}
