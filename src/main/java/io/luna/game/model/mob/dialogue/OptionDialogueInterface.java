package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.DialogueUtils;

/**
 * A {@link DialogueInterface} implementation that opens a dialogue which displays a series of options.
 *
 * @author lare96 <http://github.com/lare96>
 */
public class OptionDialogueInterface extends DialogueInterface {

    /**
     * The options.
     */
    private final String[] options;

    /**
     * Creates a new {@link OptionDialogueInterface}.
     *
     * @param options The options.
     */
    public OptionDialogueInterface(String... options) {
        super(DialogueUtils.optionDialogue(options.length));
        this.options = options;
    }

    /**
     * A function invoked when the first option is clicked.
     *
     * @param player The player.
     */
    public void firstOption(Player player) {

    }

    /**
     * A function invoked when the second option is clicked.
     *
     * @param player The player.
     */
    public void secondOption(Player player) {

    }

    /**
     * A function invoked when the third option is clicked.
     *
     * @param player The player.
     */
    public void thirdOption(Player player) {

    }

    /**
     * A function invoked when the fourth option is clicked.
     *
     * @param player The player.
     */
    public void fourthOption(Player player) {

    }

    /**
     * A function invoked when the fifth option is clicked.
     *
     * @param player The player.
     */
    public void fifthOption(Player player) {

    }

    @Override
    public final boolean init(Player player) {
        int textWidgetId = unsafeGetId() + 2;
        for (String line : options) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}