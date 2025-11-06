package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetTextMessageWriter;

/**
 * A {@link DialogueInterface} implementation that opens a dialogue which displays a series of options.
 *
 * @author lare96 
 */
public class OptionDialogue extends DialogueInterface {

    /**
     * The options.
     */
    private final String[] options;

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

    @Override
    public final boolean init(Player player) {
        int textWidgetId = getId() + 2;
        for (String line : options) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}