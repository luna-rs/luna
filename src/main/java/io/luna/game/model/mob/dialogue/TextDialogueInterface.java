package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.DialogueUtils;

/**
 * A {@link DialogueInterface} that opens a dialogue with text and no head model.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class TextDialogueInterface extends DialogueInterface {

    /**
     * The text.
     */
    private final String[] text;

    /**
     * Creates a new {@link TextDialogueInterface}.
     *
     * @param text The text.
     */
    public TextDialogueInterface(String... text) {
        super(DialogueUtils.textDialogue(text.length));
        this.text = text;
    }

    @Override
    public boolean init(Player player) {
        int textWidgetId = unsafeGetId() + 1;
        for (String line : text) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}