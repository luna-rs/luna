package io.luna.net.msg.in;

import io.luna.game.event.impl.ContinueDialogueEvent;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data for when "Click to continue" is clicked on
 * a dialogue.
 *
 * @author lare96
 */
public final class ContinueDialogueMessageReader extends GameMessageReader<ContinueDialogueEvent> {

    @Override
    public ContinueDialogueEvent decode(Player player, GameMessage msg) {
        // Unsure what this represents, as it differs from the currently open dialogue interface.
        int widgetId = msg.getPayload().getShort(false);
        return new ContinueDialogueEvent(player, widgetId);
    }
}