package io.luna.net.msg.in;

import io.luna.game.event.impl.NullEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.dialogue.DialogueQueue;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

import java.util.Optional;

/**
 * A {@link GameMessageReader} that intercepts data for when "Click to continue" is clicked on a dialogue.
 *
 * @author lare96 
 */
public final class ContinueDialogueMessageReader extends GameMessageReader<NullEvent> {

    @Override
    public NullEvent decode(Player player, GameMessage msg) {
        int interfaceId = msg.getPayload().getShort(false);
        Optional<DialogueQueue> dialogues = player.getDialogues();
        if (dialogues.isPresent()) {
            dialogues.get().advance();
        } else {
            player.getInterfaces().close();
        }
        return NullEvent.INSTANCE;
    }
}