package io.luna.net.msg.out;

import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Skill;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.msg.OutboundGameMessage;

/**
 * An {@link OutboundGameMessage} implementation that synchronizes a {@link Skill} with the client.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SendSkillUpdateMessage extends OutboundGameMessage {

    /**
     * The identifier for the skill being updated.
     */
    private final int id;

    /**
     * Creates a new {@link SendSkillUpdateMessage}.
     *
     * @param id The identifier for the skill being updated.
     */
    public SendSkillUpdateMessage(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage writeMessage(Player player) {
        Skill skill = player.skill(id);
        ByteMessage msg = ByteMessage.message(134);
        msg.put(id);
        msg.putInt((int) skill.getExperience(), ByteOrder.MIDDLE);
        msg.put(skill.getLevel());
        return msg;
    }
}
