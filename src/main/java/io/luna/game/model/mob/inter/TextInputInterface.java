package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.TextInputMessageWriter;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link InputInterface} implementation that opens an "Enter name" interface.
 *
 * @author lare96 
 */
public abstract class TextInputInterface extends InputInterface {

    @Override
    public final void open(Player player) {
        player.queue(new TextInputMessageWriter());
    }

    @Override
    public final void applyInput(Player player, OptionalInt amount, Optional<String> name) {
        name.ifPresent(value -> onNameInput(player, value));
    }

    /**
     * A function invoked when the Player has entered a name.
     *
     * @param player The player.
     * @param value The string entered.
     */
    public abstract void onNameInput(Player player, String value);
}