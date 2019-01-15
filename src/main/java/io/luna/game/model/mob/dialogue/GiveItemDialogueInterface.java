package io.luna.game.model.mob.dialogue;

import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.inter.DialogueInterface;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.util.StringUtils;

/**
 * A {@link DialogueInterface} implementation that opens a dialogue which grants the Player an item.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class GiveItemDialogueInterface extends DialogueInterface {

    /**
     * The item to give.
     */
    private final Item item;

    /**
     * The text to display when giving the item.
     */
    private final String displayText;

    /**
     * Creates a new {@link GiveItemDialogueInterface}.
     *
     * @param item The item to give.
     * @param displayText The text to display when giving the item.
     */
    public GiveItemDialogueInterface(Item item, String displayText) {
        super(306);
        this.item = item;
        this.displayText = displayText;
    }

    /**
     * Creates a new {@link GiveItemDialogueInterface} with the default display text.
     *
     * @param item The item to give.
     */
    public GiveItemDialogueInterface(Item item) {
        super(306);
        this.item = item;
        displayText = computeDisplayText();
    }

    @Override
    public boolean init(Player player) {
        if (player.getInventory().add(item)) {
            player.sendText(displayText, 308);
            player.queue(new WidgetItemModelMessageWriter(307, 200, item.getId()));
            return true;
        }
        player.sendMessage("You do not have enough space in your inventory.");
        return false;
    }

    /**
     * Computes the default display text. It varies depending on the item quantity and name.
     *
     * @return The default display text.
     */
    private String computeDisplayText() {
        String name = item.getItemDef().getName();

        StringBuilder sb = new StringBuilder("You have received");
        sb.append(' ');
        if (item.getAmount() > 1) {
            sb.append("some");
        } else {
            sb.append(StringUtils.computeArticle(name));
        }
        sb.append(' ').append(name);
        return sb.toString();
    }
}