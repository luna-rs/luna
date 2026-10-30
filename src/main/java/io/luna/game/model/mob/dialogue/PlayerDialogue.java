package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetMobModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;

import java.util.List;

/**
 * A {@link DialogueInterface} that opens a dialogue with text and a Player head model.
 *
 * @author lare96 
 */
public final class PlayerDialogue extends DialogueInterface {

    /**
     * The expression.
     */
    private final Expression expression;

    /**
     * The text.
     */
    private final String[] text;

    /**
     * Creates a new {@link DialogueInterface}.
     *
     * @param expression The expression.
     * @param text The text.
     */
    public PlayerDialogue(Expression expression, String... text) {
        super(DialogueUtils.playerDialogue(text.length));
        this.expression = expression;
        this.text = text;
    }

    /**
     * Returns an immutable snapshot of the displayed lines so dialogue consumers can identify a confirmation
     * without changing its text or invoking its continuation callback.
     *
     * @return The displayed dialogue lines.
     */
    public List<String> getLines() {
        return List.of(text);
    }

    @Override
    public boolean init(Player player) {
        int textWidgetId = getId() + 2;
        int modelWidgetId = textWidgetId - 1;

        player.queue(new WidgetMobModelMessageWriter(modelWidgetId)); // Display player head model.
        player.queue(expression.buildAnimationMessage(modelWidgetId)); // Display expression of head model.
        player.queue(new WidgetTextMessageWriter(player.getUsername(), textWidgetId++)); // Display player name.

        // Display the supplied text on the dialogue.
        for (String line : text) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}
