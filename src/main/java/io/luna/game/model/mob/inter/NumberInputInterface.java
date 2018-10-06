package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.AmountInputMessageWriter;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link InputInterface} implementation that opens an "Enter (x) amount" interface.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class NumberInputInterface extends InputInterface {

    @Override
    public final void open(Player player) {
        player.queue(new AmountInputMessageWriter());
    }

    @Override
    public final void applyInput(Player player, OptionalInt number, Optional<String> string) {
        number.ifPresent(value -> onNumberInput(player, value));
    }

    /**
     * A function invoked when the Player has entered a number.
     *
     * @param player The player.
     * @param number The number entered.
     */
    public abstract void onNumberInput(Player player, int number);
}