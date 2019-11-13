package io.luna.game.model.mob;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * A model representing the displayed interactions when right-clicking a Player, along with functions that
 * manipulate those interactions.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PlayerInteractionMenu implements Iterable<PlayerInteraction> {

    /**
     * The interactions.
     */
    private final PlayerInteraction[] interactions = new PlayerInteraction[5];

    /**
     * The player.
     */
    private final Player player;

    /**
     * Creates a new {@link PlayerInteractionMenu}.
     *
     * @param player The player.
     */
    public PlayerInteractionMenu(Player player) {
        this.player = player;
    }

    @Override
    public UnmodifiableIterator<PlayerInteraction> iterator() {
        return Iterators.forArray(interactions);
    }

    /**
     * Retrieves a {@link PlayerInteraction} within this menu with the specified {@code name}.
     *
     * @param name The name.
     * @return The interaction, wrapped in an optional.
     */
    public Optional<PlayerInteraction> forInteraction(String name) {
        return Arrays.stream(interactions)
            .filter(Objects::nonNull)
            .filter(interaction -> interaction.getName().equals(name))
            .findAny();
    }

    /**
     * Determines if {@code interaction} is contained within this menu.
     *
     * @param interaction The interaction
     * @return {@code true} if this menu contains the interaction.
     */
    public boolean contains(PlayerInteraction interaction) {
        return Objects.equals(interactions[getIndex(interaction)], interaction);
    }

    /**
     * Adds {@code interaction} to this menu.
     *
     * @param interaction The interaction.
     */
    public void show(PlayerInteraction interaction) {
        interactions[getIndex(interaction)] = interaction;
        player.queue(interaction.toMessage(false));
    }

    /**
     * Removes {@code interaction} from this menu.
     *
     * @param interaction The interaction.
     */
    public void hide(PlayerInteraction interaction) {
        interactions[getIndex(interaction)] = null;
        player.queue(interaction.toMessage(true));
    }

    /**
     * Returns the interaction index {@code - 1}.
     *
     * @param interaction The interaction.
     * @return The interaction index.
     */
    private int getIndex(PlayerInteraction interaction) {
        return interaction.getIndex() - 1;
    }
}
