package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.NameInputMessageWriter;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link InputInterface} implementation that opens an "Enter (x) name" interface.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class StringInputInterface extends InputInterface {

    @Override
    public final void open(Player player) {
        player.queue(new NameInputMessageWriter());
    }

    @Override
    public final void applyInput(Player player, OptionalInt number, Optional<String> string) {
        string.ifPresent(value -> onStringInput(player, value));
    }

    /**
     * A function invoked when the Player has entered a string.
     *
     * @param player The player.
     * @param string The string entered.
     */
    public abstract void onStringInput(Player player, String string);
}