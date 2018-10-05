package io.luna.game.model.mob.inter;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.AmountInputMessageWriter;
import io.luna.net.msg.out.NameInputMessageWriter;

/**
 * An {@link AbstractInterface} implementation that opens an "Enter amount" interface.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class InputInterface extends AbstractInterface {

    /**
     * An enumerated type representing different input types.
     */
    public enum InputType {
        NAME,
        AMOUNT
    }

    /**
     *  The input type.
     */
    private final InputType inputType;

    /**
     * Creates a new {@link InputInterface}.
     *
     * @param inputType The input type.
     */
    public InputInterface(InputType inputType) {
        this.inputType = inputType;
    }

    @Override
    public void open(Player player) {
        switch (inputType) {
            case NAME:
                player.queue(new NameInputMessageWriter());
                break;
            case AMOUNT:
                player.queue(new AmountInputMessageWriter());
                break;
        }
    }

    /**
     * @return The input type.
     */
    public InputType getInputType() {
        return inputType;
    }
}
