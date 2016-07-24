package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;

/**
 * A {@link SkillAction} implementation where items are removed from the {@link Inventory} of a {@link Player}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class DestructionSkillAction extends SkillAction {

    /**
     * Creates a new {@link DestructionSkillAction}.
     */
    public DestructionSkillAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    public final void execute() {
        Inventory inventory = mob.getInventory();
        Item[] remove = remove();

        if (inventory.containsAll(remove)) {
            inventory.removeAll(remove);
            onDestruction();
        } else {
            interrupt();
        }
    }

    /**
     * Function invoked when {@code remove()} is successfully removed from the {@link Inventory}.
     */
    protected void onDestruction() {

    }

    /**
     * Returns the items that will be removed from the {@link Inventory}.
     */
    protected abstract Item[] remove();
}