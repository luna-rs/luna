package io.luna.game.model.mob.dialogue;

import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetMobModelMessageWriter;
import io.luna.net.msg.out.WidgetTextMessageWriter;
import io.luna.util.DialogueUtils;

/**
 * A {@link DialogueInterface} that opens a dialogue with text and an NPC head model.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class NpcDialogueInterface extends DialogueInterface {

    /**
     * The NPC identifier.
     */
    private final int npcId;

    /**
     * The expression.
     */
    private final Expression expression;

    /**
     * The text.
     */
    private final String[] text;

    /**
     * Creates a new {@link NpcDialogueInterface}.
     *
     * @param npcId The NPC identifier.
     * @param expression The expression.
     * @param text The text.
     */
    public NpcDialogueInterface(int npcId, Expression expression, String... text) {
        super(DialogueUtils.npcDialogue(text.length));
        this.npcId = npcId;
        this.expression = expression;
        this.text = text;
    }

    @Override
    public boolean init(Player player) {
        String npcName = NpcDefinition.ALL.retrieve(npcId).getName();
        int textWidgetId = unsafeGetId() + 2;
        int modelWidgetId = textWidgetId - 1;

        player.queue(new WidgetMobModelMessageWriter(modelWidgetId, npcId)); // Display NPC head model.
        player.queue(expression.buildMsgWriter(modelWidgetId)); // Display expression of head model.
        player.queue(new WidgetTextMessageWriter(npcName, textWidgetId++)); // Display NPC name.

        // Display the supplied text on the dialogue.
        for (String line : text) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}