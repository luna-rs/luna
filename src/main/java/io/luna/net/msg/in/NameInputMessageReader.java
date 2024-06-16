package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.InputInterface;
import io.luna.game.model.mob.inter.NameInputInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;
import io.luna.util.StringUtils;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a string is entered on an {@link NameInputInterface}.
 *
 * @author lare96
 */
public final class NameInputMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        String input = StringUtils.decodeFromBase37(msg.getPayload().getLong());
        AbstractInterfaceSet interfaces = player.getInterfaces();

        Optional<InputInterface> inputOptional = interfaces.getCurrentInput();
        if (inputOptional.isPresent()) {
            inputOptional.get().applyInput(player, OptionalInt.empty(), Optional.of(input));
            interfaces.resetCurrentInput();
        }
        return NullEvent.INSTANCE;
    }
}