package io.luna.net.msg.in;

import io.luna.game.event.impl.PrivacyModeChangedEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerPrivacy;
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the player changes their privacy options.
 *
 * @author lare96
 */
public final class PrivacyOptionMessageReader extends GameMessageReader<PrivacyModeChangedEvent> {
// todo engine plugin

    @Override
    public PrivacyModeChangedEvent decode(Player player, GameMessage msg) {
        PrivacyMode publicChatMode = PrivacyMode.fromId(msg.getPayload().get());
        PrivacyMode privateChatMode = PrivacyMode.fromId(msg.getPayload().get());
        PrivacyMode tradeMode = PrivacyMode.fromId(msg.getPayload().get());
        PlayerPrivacy newPrivacy = new PlayerPrivacy(publicChatMode, privateChatMode, tradeMode);
        return new PrivacyModeChangedEvent(player, player.getPrivacyOptions(), newPrivacy);
    }

    @Override
    public void handle(Player player, PrivacyModeChangedEvent event) {
        player.setPrivacyOptions(event.getNewPrivacy());
    }
}
