package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.net.msg.out.WidgetVisibilityMessageWriter;

/**
 * A {@link DialogueInterface} implementation that opens a dialogue which displays a series of options.
 *
 * @author lare96 
 */
public class OptionDialogue extends DialogueInterface {

    /**
     * The title shown when none is set.
     */
    private static final String DEFAULT_TITLE = "Select an Option";

    /**
     * The options.
     */
    private final String[] options;

    /**
     * The title, or {@code null} for {@link #DEFAULT_TITLE}.
     */
    private String title;

    /**
     * Creates a new {@link OptionDialogue}.
     *
     * @param options The options.
     */
    public OptionDialogue(String... options) {
        super(DialogueUtils.optionDialogue(options.length));
        this.options = options;
    }

    /**
     * A function invoked when the first option is clicked.
     *
     * @param player The player.
     */
    public void first(Player player) {

    }

    /**
     * A function invoked when the second option is clicked.
     *
     * @param player The player.
     */
    public void second(Player player) {

    }

    /**
     * A function invoked when the third option is clicked.
     *
     * @param player The player.
     */
    public void third(Player player) {

    }

    /**
     * A function invoked when the fourth option is clicked.
     *
     * @param player The player.
     */
    public void fourth(Player player) {

    }

    /**
     * A function invoked when the fifth option is clicked.
     *
     * @param player The player.
     */
    public void fifth(Player player) {

    }

    /**
     * Sets the title shown above the options. A custom title is framed by the wider pair of swords, so longer text
     * fits.
     *
     * @param title The title, or {@code null} for {@link #DEFAULT_TITLE}.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public final boolean init(Player player) {
        // The client keeps the last title and swords it was sent, so a dialogue without a title resets them.
        int[] swords = DialogueUtils.optionSwordLayers(options.length);
        player.queue(new WidgetTextMessageWriter(title == null ? DEFAULT_TITLE : title, getId() + 1));
        player.queue(new WidgetVisibilityMessageWriter(swords[0], title != null));
        player.queue(new WidgetVisibilityMessageWriter(swords[1], title == null));

        int textWidgetId = getId() + 2;
        for (String line : options) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}