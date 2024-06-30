package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.AbstractInterfaceSet;
import io.luna.game.model.mob.inter.DialogueInterface;

import java.util.Queue;
import java.util.function.Consumer;

/**
 * A model representing a queue of {@link DialogueInterface}s that will be shown in sequential order. New
 * instances of this class should be created through the {@link DialogueQueueBuilder}.
 *
 * @author lare96 
 */
public final class DialogueQueue {

    /**
     * The player.
     */
    private final Player player;

    /**
     * The queue of dialogues.
     */
    private final Queue<DialogueInterface> dialogues;

    /**
     * Creates a new {@link DialogueQueue}.
     *
     * @param player The player.
     * @param dialogues The queue of dialogues.
     */
    DialogueQueue(Player player, Queue<DialogueInterface> dialogues) {
        this.player = player;
        this.dialogues = dialogues;
    }

    /**
     * Advances this dialogue queue by {@code 1}, and displays the next dialogue.
     */
    public void advance() {
        DialogueInterface nextDialogue = dialogues.poll();
        if (nextDialogue != null) {
            player.getInterfaces().open(nextDialogue);
        } else {
            AbstractInterfaceSet interfaces = player.getInterfaces();
            if (interfaces.standardTo(DialogueInterface.class).isPresent()) {
                interfaces.close();
            }
            player.resetDialogues();
        }
    }
}