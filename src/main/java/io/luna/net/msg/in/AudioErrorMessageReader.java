package io.luna.net.msg.in;

import io.luna.Luna;
import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when an audio error occurs.
 *
 * @author lare96
 */
public final class AudioErrorMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        int soundId = msg.getPayload().getShort();
        if (Luna.settings().game().betaMode()) {
            player.sendMessage("[AudioErrorMessageReader] soundId: " + soundId);
        }
        return NullEvent.INSTANCE;
    }
}
