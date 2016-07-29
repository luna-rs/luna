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
     * The array of items currently being added.
     */
    protected Item[] currentAdd;

    /**
     * The array of items currently being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link ProducingSkillAction}.
     */
    public ProducingSkillAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    protected void call() {
        if (!canProduce()) {
            interrupt();
            return;
        }

        Inventory inventory = mob.getInventory();

        currentRemove = remove();
        currentAdd = add();

        if (!inventory.containsAll(currentRemove)) {
            interrupt();
            return;
        }

        int newSlots = inventory.computeIndexCount(currentAdd);
        int oldSlots = inventory.computeIndexCount(currentRemove);
        if ((newSlots - oldSlots) > inventory.computeRemainingSize()) {
            mob.queue(new GameChatboxMessageWriter("You do not have enough space in your inventory."));
            interrupt();
            return;
        }

        inventory.removeAll(currentRemove);
        inventory.addAll(currentAdd);
        onProduce();
    }

    /**
     * Function invoked at the beginning of every Action loop. Return {@code false} to interrupt the Action.
     */
    protected boolean canProduce() {
        return true;
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