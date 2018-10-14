package io.luna.net.msg.in;

import io.luna.game.event.Event;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

/**
 * A {@link GameMessageReader} that intercepts data for when "Click to continue" is clicked on a dialogue.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class ContinueDialogueMessageReader extends GameMessageReader {

    @Override
    public Event read(Player player, GameMessage msg) throws Exception {
        Optional<DialogueQueue> dialogues = player.getDialogues();
        if (dialogues.isPresent()) {
            dialogues.get().advance();
        } else {
            player.getInterfaces().close();
        }
        return null;
    }
}