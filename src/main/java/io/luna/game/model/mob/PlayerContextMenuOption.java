package io.luna.game.model.mob;

import io.luna.net.msg.out.ContextMenuOptionMessageWriter;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;

 /**
 * A model representing a context menu option on a {@link PlayerContextMenu}. Options with
 * the name "Attack" are <strong>always</strong> pinned as long as the combat level of the Player interacting
 * is higher than the Player being interacted with.
 *
 * @author lare96 
 */
public final class PlayerContextMenuOption {

    /**
     * The "Attack" menu option. This interaction will be pinned.
     */
    public static final PlayerContextMenuOption ATTACK = new PlayerContextMenuOption(1, "Attack", true);

    /**
     * The "Challenge" menu option. This interaction will be pinned.
     */
    public static final PlayerContextMenuOption CHALLENGE = new PlayerContextMenuOption(1, "Challenge", true);

    /**
     * The "Follow" menu option.
     */
    public static final PlayerContextMenuOption FOLLOW = new PlayerContextMenuOption(3, "Follow", false);

    /**
     * The "Trade" menu option.
     */
    public static final PlayerContextMenuOption TRADE = new PlayerContextMenuOption(4, "Trade with", false);

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
     * Creates a new {@link PlayerContextMenuOption}.
     *
     * @param index The index.
     * @param name The name.
     * @param pinned If this should be the topmost menu option.
     */
    public PlayerContextMenuOption(int index, String name, boolean pinned) {
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
        if (obj instanceof PlayerContextMenuOption) {
            PlayerContextMenuOption other = (PlayerContextMenuOption) obj;
            return index == other.index &&
                    name.equals(other.name) &&
                    pinned == other.pinned;
        }
        return false;
    }

    /**
     * Converts this menu option into a {@link ContextMenuOptionMessageWriter}.
     *
     * @param remove If this menu option is being removed.
     * @return The converted game message.
     */
    ContextMenuOptionMessageWriter toMessage(boolean remove) {
        PlayerContextMenuOption interaction = remove ? new PlayerContextMenuOption(index, "null", pinned) : this;
        return new ContextMenuOptionMessageWriter(interaction);
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
     * @return If this should be the topmost menu option.
     */
    public boolean isPinned() {
        return pinned;
    }
}