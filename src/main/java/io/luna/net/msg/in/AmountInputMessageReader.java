package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.AmountInputInterface;
import io.luna.game.model.mob.inter.InputInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a number is entered on an
 * {@link AmountInputInterface}.
 *
 * @author lare96 
 */
public final class AmountInputMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        OptionalInt number = OptionalInt.of(msg.getPayload().getInt(false));
        AbstractInterfaceSet interfaces = player.getInterfaces();

        Optional<InputInterface> inputOptional = interfaces.getCurrentInput();
        if (inputOptional.isPresent()) {
            inputOptional.get().applyInput(player, number, Optional.empty());
            interfaces.resetCurrentInput();
        }
        return NullEvent.INSTANCE;
    }
}