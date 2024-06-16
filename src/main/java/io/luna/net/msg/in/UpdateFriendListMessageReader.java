package io.luna.net.msg.in;

import io.luna.game.event.impl.PrivacyListChangeEvent;
import io.luna.game.event.impl.PrivacyListChangeEvent.AddFriendEvent;
import io.luna.game.event.impl.PrivacyListChangeEvent.AddIgnoreEvent;
import io.luna.game.event.impl.PrivacyListChangeEvent.RemoveFriendEvent;
import io.luna.game.event.impl.PrivacyListChangeEvent.RemoveIgnoreEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the friends/ignores list is updated.
 *
 * @author lare96
 */
public final class UpdateFriendListMessageReader extends GameMessageReader<PrivacyListChangeEvent> {

    @Override
    public PrivacyListChangeEvent decode(Player player, GameMessage msg) {
        long name = msg.getPayload().getLong();
        switch (opcode) {
            case 120:
                return new AddFriendEvent(player, name);
            case 141:
                return new RemoveFriendEvent(player, name);
            case 217:
                return new AddIgnoreEvent(player, name);
            case 160:
                return new RemoveIgnoreEvent(player, name);
        }
        return null;
    }

    @Override
    public boolean validate(Player player, PrivacyListChangeEvent event) {
        return event.getName() > 0;
    }
}
