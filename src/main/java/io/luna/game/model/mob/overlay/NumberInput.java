package io.luna.game.model.mob.overlay;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.NumberInputMessageWriter;

/**
 * An {@link InputOverlay} that prompts the player to enter a numeric value ("Enter amount").
 * <p>
 * This overlay is used for quantity-based input scenarios such as withdrawing items from a bank,
 * trading specific amounts, or splitting stackable items. It sends a {@link NumberInputMessageWriter}
 * to the client, which opens the standard numeric input box and awaits user entry.
 *
 * @author lare96
 */
public abstract class NumberInput extends InputOverlay<Integer> {

    @Override
    public final void open(Player player) {
        player.queue(new NumberInputMessageWriter());
    }
}
