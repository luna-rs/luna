package io.luna.game.model.mob;

import io.luna.net.msg.out.PlayerInteractionMessageWriter;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing an interaction option on a {@link PlayerInteractionMenu}. Interactions with
 * the name "Attack" are <strong>always</strong> pinned as long as the combat level of the Player interacting
 * is higher than the Player being interacted with.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerInteraction {

    /**
     * The "Attack" interaction. This interaction will be pinned.
     */
    public static final PlayerInteraction ATTACK = new PlayerInteraction(1, "Attack", true);

    /**
     * The "Challenge" interaction. This interaction will be pinned.
     */
    public static final PlayerInteraction CHALLENGE = new PlayerInteraction(1, "Challenge", true);

    /**
     * The "Follow" interaction.
     */
    public static final PlayerInteraction FOLLOW = new PlayerInteraction(3, "Follow", false);

    /**
     * The "Trade" interaction.
     */
    public static final PlayerInteraction TRADE = new PlayerInteraction(4, "Trade with", false);

    /**
     * The index.
     */
    private final int index;

    /**
     * The name.
     */
    private final String name;

    /**
     * If this should be the topmost left-click interaction.
     */
    private final boolean pinned;

    /**
     * Creates a new {@link PlayerInteraction}.
     *
     * @param index The index.
     * @param name The name.
     * @param pinned If this should be the topmost interaction.
     */
    public PlayerInteraction(int index, String name, boolean pinned) {
        checkArgument(index >= 1 && index <= 5, "Index must be >= 1 and <= 5.");
        checkArgument(name != null, "Name cannot be null. Use \"null\" instead.");
        this.index = index;
        this.name = name;
        this.pinned = pinned;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, pinned);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PlayerInteraction) {
            PlayerInteraction other = (PlayerInteraction) obj;
            return index == other.index &&
                    name.equals(other.name) &&
                    pinned == other.pinned;
        }
        return false;
    }

    /**
     * Converts this interaction into a {@link PlayerInteractionMessageWriter}.
     *
     * @param remove If this interaction is being removed.
     * @return The converted game message.
     */
    PlayerInteractionMessageWriter toMessage(boolean remove) {
        PlayerInteraction interaction = remove ? new PlayerInteraction(index, "null", pinned) : this;
        return new PlayerInteractionMessageWriter(interaction);
    }

    /**
     * @return The index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return If this should be the topmost interaction.
     */
    public boolean isPinned() {
        return pinned;
    }
}