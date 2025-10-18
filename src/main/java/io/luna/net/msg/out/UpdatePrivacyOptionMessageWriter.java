package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerPrivacy;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that updates the player's privacy options on the client.
 *
 * @author lare96
 */
public final class UpdatePrivacyOptionMessageWriter extends GameMessageWriter {

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(201);
        PlayerPrivacy privacy = player.getPrivacyOptions();
        msg.put(privacy.getPublicChat().getId());
        msg.put(privacy.getPrivateChat().getId());
        msg.put(privacy.getTrade().getId());
        return msg;
    }
}
