package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link AbstractInterface} implementation that opens an input interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class InputInterface extends AbstractInterface {

    /**
     * Creates a new {@link InputInterface}.
     */
    InputInterface() {
        super(InterfaceType.INPUT);
    }

    @Override
    public final boolean isAutoClose(Player player) {
        return true;
    }

    /**
     * A function invoked when the Player inputs a value on the interface.
     *
     * @param player The player.
     * @param number The number entered.
     * @param string The string entered.
     */
    public abstract void applyInput(Player player, OptionalInt number, Optional<String> string);
}
