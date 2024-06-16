package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerInteraction;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.codec.ValueType;
import io.luna.net.msg.GameMessageWriter;

/**
 * A {@link GameMessageWriter} implementation that displays or hides player interactions.
 *
 * @author lare96
 */
public final class PlayerInteractionMessageWriter extends GameMessageWriter {

    /**
     * The interaction.
     */
    private final PlayerInteraction interaction;

    /**
     * Creates a new {@link PlayerInteractionMessageWriter}.
     *
     * @param interaction The interaction.
     */
    public PlayerInteractionMessageWriter(PlayerInteraction interaction) {
        this.interaction = interaction;
    }

    @Override
    public ByteMessage write(Player player) {
        ByteMessage msg = ByteMessage.message(157, MessageType.VAR);
        msg.put(interaction.getIndex(), ValueType.NEGATE);
        msg.putString(interaction.getName());
        msg.put(interaction.isPinned() ? 1 : 0, ValueType.ADD);
        return msg;
    }
}