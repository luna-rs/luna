package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ButtonClickEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.ConfirmTradeInterface;
import io.luna.game.model.mob.inter.OfferTradeInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import static com.google.common.base.Preconditions.checkState;
import static io.netty.util.internal.StringUtil.simpleClassName;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when a widget is clicked.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ButtonClickMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int buttonId = msg.getPayload().getShort(false);

        checkState(buttonId >= 0, "buttonId < 0");
        LOGGER.debug("[{}]: {}", simpleClassName(this), box(buttonId));

        switch (buttonId) {
            case 3420:
                player.getInterfaces().standardTo(OfferTradeInterface.class).
                        ifPresent(inter -> inter.accept(player));
                return null;
            case 3546:
                player.getInterfaces().standardTo(ConfirmTradeInterface.class).
                        ifPresent(inter -> inter.accept(player));
                return null;
        }
        return new ButtonClickEvent(player, buttonId);
    }
}
