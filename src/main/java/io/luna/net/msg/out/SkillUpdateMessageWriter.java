package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.ByteOrder;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays the skill level and experience.
 *
 * @author lare96
 */
public final class SkillUpdateMessageWriter extends GameMessageWriter {

    /**
     * The skill identifier.
     */
    private final int id;

    /**
     * Creates a new {@link SkillUpdateMessageWriter}.
     *
     * @param id The skill identifier.
     */
    public SkillUpdateMessageWriter(int id) {
        this.id = id;
    }

    @Override
    public ByteMessage write(Player player) {
        Skill skill = player.skill(id);
        ByteMessage msg = ByteMessage.message(49);
        msg.put(id, ValueType.NEGATE);
        msg.put(skill.getLevel());
        msg.putInt((int) skill.getExperience());
        return msg;
    }
}
