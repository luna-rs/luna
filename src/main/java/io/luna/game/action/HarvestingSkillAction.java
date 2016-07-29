package io.luna.game.action;

import io.luna.game.model.item.Inventory;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.mobile.Skill;
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
     * The {@link Skill} being used for this harvesting Action.
     */
    protected final Skill skill;

    /**
     * The array of items currently being added.
     */
    protected Item[] currentAdd;

    /**
     * The array of items currently being removed.
     */
    protected Item[] currentRemove;

    /**
     * Creates a new {@link HarvestingSkillAction}.
     */
    public HarvestingSkillAction(Player player, int skillId) {
        super(player, false, 1);
        skill = player.skill(skillId);
    }

    @Override
    public final void execute() {
        if (!canHarvest()) {
            interrupt();
            return;
        }

        /* NOTE: Formula is a WIP and hasn't been tested much. */
        double skillFactor = skill.getLevel() * 0.0009408206197;
        double chanceFactor = harvestChance();
        double totalFactor = skillFactor + chanceFactor;
        double currentRoll = ThreadLocalRandom.current().nextDouble();

        checkState(chanceFactor >= 0.0, "harvestChance() must be > 0.0");
        checkState(chanceFactor <= 1.0, "harvestChance() must be < 1.0");

        if (totalFactor >= currentRoll) {
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
            onHarvest();
        }
    }

    /**
     * Function invoked at the beginning of every Action loop. Return {@code false} to interrupt the Action.
     */
    protected boolean canHarvest() {
        return true;
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
     * Return the chance of harvesting {@code add()}.
     */
    protected abstract double harvestChance();

    /**
     * Return the items that will be added.
     */
    protected abstract Item[] add();
}
