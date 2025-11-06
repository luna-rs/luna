package io.luna.game.model.mob.dialogue;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.AbstractOverlaySet;

import java.util.Queue;

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
            player.getOverlays().open(nextDialogue);
        } else {
            AbstractOverlaySet overlays = player.getOverlays();
            player.resetDialogues();
            if (overlays.contains(DialogueInterface.class)) {
                overlays.closeWindows();
            }
        }
    }
}