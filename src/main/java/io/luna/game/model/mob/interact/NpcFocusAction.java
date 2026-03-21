package io.luna.game.model.mob.interact;

import io.luna.game.action.Action;
import io.luna.game.action.ActionType;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

/**
 * A weak {@link Action} that keeps an {@link Npc} focused on a specific {@link Player} while both sides remain in
 * the same interaction.
 * <p>
 * On its first execution, the NPC is set to interact with the player. On later executions, this action verifies that
 * the NPC is still interacting with that player and that the player is still interacting with the NPC.
 * <p>
 * The action finishes once either side breaks the interaction relationship. If the player stops interacting with the
 * NPC first, the NPC's interaction is cleared before stopping.
 *
 * @author lare96
 */
final class NpcFocusAction extends Action<Npc> {

    /**
     * The player that this NPC should remain focused on.
     */
    private final Player player;

    /**
     * Creates a new {@link NpcFocusAction}.
     *
     * @param npc The NPC that should focus on the player.
     * @param player The player that the NPC should remain focused on.
     */
    public NpcFocusAction(Npc npc, Player player) {
        super(npc, ActionType.WEAK, true, 1);
        this.player = player;
    }

    @Override
    public boolean run() {
        if (getExecutions() == 0) {
            // Interact with player on first execution.
            mob.interact(player);
        }

        // NPC has broken focus with the player, or is in combat.
        if (mob.getCombat().inCombat() || !mob.isInteractingWith(player)) {
            return true;
        }

        // Player has broken focus with the NPC, reset interaction state.
        if (!player.isInteractingWith(mob)) {
            mob.interact(null);
            return true;
        }
        return false;
    }
}