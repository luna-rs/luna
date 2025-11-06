package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetTextMessageWriter;

/**
 * A {@link DialogueInterface} that opens a dialogue with text and no head model.
 *
 * @author lare96 
 */
public final class TextDialogue extends DialogueInterface {

    /**
     * The text.
     */
    private final String[] text;

    /**
     * Creates a new {@link TextDialogue}.
     *
     * @param text The text.
     */
    public TextDialogue(String... text) {
        super(DialogueUtils.textDialogue(text.length));
        this.text = text;
    }

    @Override
    public boolean init(Player player) {
        int textWidgetId = getId() + 1;
        for (String line : text) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}