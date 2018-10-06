package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.InputInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a number is entered on an
 * input interface.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class AmountInputMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        OptionalInt amountEntered = OptionalInt.of(msg.getPayload().getInt());
        AbstractInterfaceSet interfaces = player.getInterfaces();

        Optional<InputInterface> inputOptional = interfaces.getCurrentInput();
        if (inputOptional.isPresent()) {
            inputOptional.get().applyInput(player, amountEntered, Optional.empty());
            interfaces.resetCurrentInput();
        }
        return null;
    }
}