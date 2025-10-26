package io.luna.net.msg.in;

import io.luna.game.event.impl.NumberInputEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.NumberInputInterface;
import io.luna.game.model.mob.inter.InputInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when a number is entered on an
 * {@link NumberInputInterface}.
 *
 * @author lare96
 */
public final class NumberInputMessageReader extends GameMessageReader<NumberInputEvent> {

    @Override
    public NumberInputEvent decode(Player player, GameMessage msg) {
        int amount = msg.getPayload().getInt(false);
        return new NumberInputEvent(player, amount);
    }

    @Override
    public boolean validate(Player player, NumberInputEvent event) {
        return player.getInterfaces().getCurrentInput().map(InputInterface::getClass).
                filter(NumberInputInterface.class::isAssignableFrom).isPresent();
    }
}