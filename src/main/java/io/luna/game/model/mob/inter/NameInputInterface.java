package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.NameInputMessageWriter;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link InputInterface} implementation that opens an "Enter name" interface.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class NameInputInterface extends InputInterface {

    @Override
    public final void open(Player player) {
        player.queue(new NameInputMessageWriter());
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