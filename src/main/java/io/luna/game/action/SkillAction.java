package io.luna.game.action;

import io.luna.game.model.mobile.Player;

/**
 * An {@link Action} implementation that details Actions related to skills. Not meant to be inherited from directly, one of
 * the following subclasses should be used instead <ul> <li> DestructionSkillAction</li>
 * <p>
 * For skills that simply remove a set of items from the inventory at a fixed interval (for example: prayer).
 * <li>HarvestingSkillAction</li>
 * <p>
 * For skills that require a harvesting formula (for example: woodcutting, fishing, mining). <li>ProducingSkillAction</li>
 * <p>
 * For skills that remove and add a set of items from and to the inventory at a fixed interval (for example: herblore,
 * fletching, crafting). </ul>
 *
 * @author lare96 <http://github.org/lare96>
 */
public abstract class SkillAction extends Action<Player> {

    /**
     * Creates a new {@link SkillAction}.
     */
    public SkillAction(Player player, boolean instant, int delay) {
        super(player, instant, delay);
    }

    @Override
    protected final void onInit() {
        if (canInit()) {
            mob.getWalkingQueue().clear();
        } else {
            interrupt();
        }
    }

    /**
     * Returns whether or not the Action can be initialized.
     */
    protected boolean canInit() {
        return true;
    }
}
