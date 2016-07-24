package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.GameChatboxMessageWriter;

/**
 * A {@link SkillAction} implementation that will remove items from and add items to the inventory.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class ProducingSkillAction extends SkillAction {

    /**
     * Creates a new {@link ProducingSkillAction}.
     */
    public ProducingSkillAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    protected void execute() {
        Inventory inventory = mob.getInventory();

        Item[] remove = remove();
        Item[] add = add();

        if (!inventory.containsAll(remove)) {
            interrupt();
            return;
        }

        int newSlots = inventory.computeSlotsNeeded(add);
        int oldSlots = inventory.computeSlotsNeeded(remove);
        if ((newSlots - oldSlots) > inventory.computeRemainingSize()) {
            mob.queue(new GameChatboxMessageWriter("You do not have enough space in your inventory."));
            interrupt();
            return;
        }

        inventory.removeAll(remove);
        inventory.addAll(add);
        onProduce();
    }

    /**
     * Function executed when items are removed from and added to the inventory.
     */
    protected void onProduce() {

    }

    /**
     * Return the items that will be removed.
     */
    protected abstract Item[] remove();

    /**
     * Return the items that will be added.
     */
    protected abstract Item[] add();
}