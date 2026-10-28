package io.luna.game.model.mob.dialogue;

import game.player.Messages;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.net.msg.out.WidgetItemModelMessageWriter;
import io.luna.util.StringUtils;

/**
 * Displays an item in the chatbox, optionally granting it when the dialogue is reached.
 * A successful grant belongs to this dialogue instance and is not repeated when it is reopened.
 * Display-only dialogues never require inventory space or change any item container.
 *
 * @author lare96 
 */
public final class GiveItemDialogue extends DialogueInterface {

    /**
     * The item to give.
     */
    private final Item item;

    /**
     * The text to display when giving the item.
     */
    private final String displayText;

    /**
     * Whether this dialogue owns the inventory grant, rather than displaying an existing transfer.
     */
    private final boolean addItem;

    /**
     * Records a successful grant so replaying the same dialogue cannot duplicate its reward.
     * A failed inventory insertion leaves the dialogue eligible for another attempt.
     */
    private boolean itemGiven;

    /**
     * Creates a new {@link GiveItemDialogue}.
     *
     * @param item The item to give.
     * @param displayText The text to display when giving the item.
     */
    public GiveItemDialogue(Item item, String displayText) {
        this(item, displayText, true);
    }

    /**
     * Creates a new {@link GiveItemDialogue} with the default display text.
     *
     * @param item The item to give.
     */
    public GiveItemDialogue(Item item) {
        this(item, true);
    }

    /**
     * Creates an item display with explicit reward ownership.
     *
     * @param item The item to display and optionally give.
     * @param displayText The display text, or {@code null} for the default item description.
     * @param addItem Whether to grant the item on the first successful opening.
     */
    public GiveItemDialogue(Item item, String displayText, boolean addItem) {
        super(306);
        this.item = item;
        this.displayText = displayText == null ? computeDisplayText() : displayText;
        this.addItem = addItem;
    }

    /**
     * Creates an item display using the default description and explicit reward ownership.
     *
     * @param item The item to display and optionally give.
     * @param addItem Whether to grant the item on the first successful opening.
     */
    public GiveItemDialogue(Item item, boolean addItem) {
        this(item, null, addItem);
    }

    @Override
    public boolean init(Player player) {
        if (addItem && !itemGiven) {
            if (!player.getInventory().hasSpaceFor(item) || !player.getInventory().add(item)) {
                player.sendMessage(Messages.INSTANCE.inventoryFull());
                return false;
            }
            itemGiven = true;
        }
        player.sendText(displayText, 308);
        player.queue(new WidgetItemModelMessageWriter(307, 200, item.getId()));
        return true;
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
