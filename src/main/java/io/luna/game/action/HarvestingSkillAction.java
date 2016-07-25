package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.net.msg.out.GameChatboxMessageWriter;
import io.netty.util.internal.ThreadLocalRandom;

import static com.google.common.base.Preconditions.checkState;

/**
 * A {@link SkillAction} implementation that uses an algorithm to determine if items should be removed from and added to the
 * inventory every tick.
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class HarvestingSkillAction extends SkillAction {

    /**
     * Creates a new {@link HarvestingSkillAction}.
     */
    public HarvestingSkillAction(Player player) {
        super(player, false, 1);
    }

    @Override
    public final void execute() {

        /* NOTE: Formula is a WIP and hasn't been tested much. */
        double skillFactor = skillLevel() * 0.002;
        double chanceFactor = harvestChance();
        double totalFactor = skillFactor + chanceFactor;
        double currentRoll = ThreadLocalRandom.current().nextDouble();

        checkState(chanceFactor <= 0.0, "harvestChance() must be > 0.0");
        checkState(chanceFactor >= 1.0, "harvestChance() must be < 1.0");

        if (totalFactor >= currentRoll) {
            Inventory inventory = mob.getInventory();

            Item[] remove = remove();
            Item[] add = add();

            if (!inventory.containsAll(remove)) {
                interrupt();
                return;
            }

            int newSlots = inventory.computeIndexCount(add);
            int oldSlots = inventory.computeIndexCount(remove);
            if ((newSlots - oldSlots) > inventory.computeRemainingSize()) {
                mob.queue(new GameChatboxMessageWriter("You do not have enough space in your inventory."));
                interrupt();
                return;
            }

            inventory.removeAll(remove);
            inventory.addAll(add);
            onHarvest();
        }
    }

    /**
     * Function invoked when items are harvested.
     */
    protected void onHarvest() {

    }

    /**
     * Return the items that will be removed.
     */
    protected Item[] remove() {
        return Item.EMPTY_ARRAY;
    }

    /**
     * Return the level of skill this action is being done for.
     */
    protected abstract int skillLevel();

    /**
     * Return the chance of harvesting {@code add()}.
     */
    protected abstract double harvestChance();

    /**
     * Return the items that will be added.
     */
    protected abstract Item[] add();
}
