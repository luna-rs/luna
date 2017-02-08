package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.event.impl.ButtonClickEvent;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.MessageReader;

import static com.google.common.base.Preconditions.checkState;
import static io.netty.util.internal.StringUtil.simpleClassName;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * A {@link MessageReader} implementation that intercepts data sent when a widget is clicked.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class ButtonClickMessageReader extends MessageReader {

    // TODO: Ensure that 'buttonId' is a valid widget on the interface currently open (if one is open)

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        int buttonId = msg.getPayload().getShort(false);

        checkState(buttonId >= 0, "buttonId < 0");

        LOGGER.debug("[{}]: {}", simpleClassName(this), box(buttonId));
        return new ButtonClickEvent(player, buttonId);
    }
}
