package io.luna.game.model.mob.dialogue;

import io.luna.game.model.def.NpcDefinition;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetAnimationMessageWriter;
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
     * The expression animation id.
     */
    private final int expressionAnimationId;

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
        this(npcId, expression, 0, text);
    }

    /**
     * Creates a new {@link NpcDialogueInterface}.
     *
     * @param npcId The NPC identifier.
     * @param expressionAnimationId The expression animation id
     * @param text The text.
     */
    public NpcDialogueInterface(int npcId, int expressionAnimationId, String... text) {
        this(npcId, null, expressionAnimationId, text);
    }

    /**
     * Creates a new {@link NpcDialogueInterface}.
     *
     * @param npcId The NPC identifier.
     * @param expression The expression.
     * @param expressionAnimationId The expression animation id
     * @param text The text.
     */
    private NpcDialogueInterface(int npcId, Expression expression, int expressionAnimationId, String... text) {
        super(DialogueUtils.npcDialogue(text.length));
        this.npcId = npcId;
        this.expression = expression;
        this.expressionAnimationId = expressionAnimationId;
        this.text = text;
    }

    @Override
    public boolean init(Player player) {
        String npcName = NpcDefinition.ALL.retrieve(npcId).getName();
        int textWidgetId = unsafeGetId() + 2;
        int modelWidgetId = textWidgetId - 1;

        player.queue(new WidgetMobModelMessageWriter(modelWidgetId, npcId)); // Display NPC head model.
        if (expression != null) {
            player.queue(expression.buildMsgWriter(modelWidgetId)); // Predefined expression animation identifier
        } else {
            player.queue(new WidgetAnimationMessageWriter(modelWidgetId, expressionAnimationId)); // Unique expression animation identifier
        }
        player.queue(new WidgetTextMessageWriter(npcName, textWidgetId++)); // Display NPC name.

        // Display the supplied text on the dialogue.
        for (String line : text) {
            player.queue(new WidgetTextMessageWriter(line, textWidgetId++));
        }
        return true;
    }
}