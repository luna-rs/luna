package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.AmountInputMessageWriter;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An {@link InputInterface} implementation that opens an "Enter amount" interface.
 *
 * @author lare96 
 */
public abstract class AmountInputInterface extends InputInterface {

    @Override
    public final void open(Player player) {
        player.queue(new AmountInputMessageWriter());
    }

    @Override
    public final void applyInput(Player player, OptionalInt amount, Optional<String> name) {
        amount.ifPresent(value -> onAmountInput(player, value));
    }

    /**
     * A function invoked when the Player has entered an amount.
     *
     * @param player The player.
     * @param value The number entered.
     */
    public abstract void onAmountInput(Player player, int value);
}