package io.luna.game.model.mob.interact;

import io.luna.game.action.Action;
import io.luna.game.action.ActionState;
import io.luna.game.action.ActionType;
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent;
import io.luna.game.event.impl.InteractableEvent;
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent;
import io.luna.game.event.impl.PlayerClickEvent.PlayerFirstClickEvent;
import io.luna.game.event.impl.UseSpellEvent.MagicOnNpcEvent;
import io.luna.game.event.impl.UseSpellEvent.MagicOnPlayerEvent;
import io.luna.game.model.Direction;
import io.luna.game.model.Entity;
import io.luna.game.model.Position;
import io.luna.game.model.collision.CollisionManager;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An {@link Action} that advances a player toward an interaction target and executes matched interaction listeners
 * nce the target becomes reachable.
 * <p>
 * This action owns the standard interaction flow for reach-based events:
 * <ol>
 *     <li>the player begins interacting with a target {@link Entity}</li>
 *     <li>listeners are checked each cycle against their {@link InteractionPolicy}</li>
 *     <li>once one or more listeners become reachable, optional corrective movement is applied</li>
 *     <li>the player is re-focused on the target and the matched listeners are executed</li>
 * </ol>
 * <p>
 * Listeners are evaluated in their existing order. The first satisfied concrete policy becomes the authoritative
 * trigger for that reach cycle, and any other listeners matched in the same cycle are executed under that same
 * interaction result.
 *
 * @author lare96
 */
public final class InteractionAction extends Action<Player> {

    /**
     * A one-tick action that executes deferred interaction listeners after the parent interaction has been prepared.
     * <p>
     * This action is used to finalize an interaction on the next cycle, optionally preserving or clearing the
     * player's walking queue beforehand.
     */
    private final class DelayedInteractionAction extends Action<Player> {

        /**
         * The interaction listeners waiting to be executed.
         */
        private final List<InteractionActionListener> pending;

        /**
         * Indicates whether the player moved as part of reaching the interaction.
         * <p>
         * When {@code false}, the walking queue is cleared before the interaction listeners are executed.
         */
        private final boolean moved;

        /**
         * Creates a new delayed interaction action.
         *
         * @param pending The interaction listeners to execute when this action runs.
         * @param moved {@code true} if the player moved before the interaction was performed, otherwise {@code false}.
         */
        public DelayedInteractionAction(List<InteractionActionListener> pending, boolean moved) {
            super(InteractionAction.this.mob, ActionType.STRONG, true, 1);
            this.pending = pending;
            this.moved = moved;
        }

        @Override
        public boolean run() {
            if (!moved) {
                mob.getWalking().clear();
            }
            mob.interact(target);
            pending.forEach(listener -> listener.getAction().run());
            return true;
        }
    }

    /**
     * The interaction event that created this action.
     * <p>
     * This provides the interaction context and the backing target reference for this action.
     */
    private final InteractableEvent event;

    /**
     * The current entity being interacted with.
     * <p>
     * This target is used for reach checks, corrective movement, and final interaction execution.
     */
    private Entity target;

    /**
     * The listeners still waiting to be satisfied for this interaction.
     */
    private List<InteractionActionListener> listeners;

    /**
     * Creates a new {@link InteractionAction}.
     *
     * @param player The interacting player.
     * @param listeners The listeners to evaluate while resolving this interaction.
     * @param target The initial interaction target.
     * @param event The interaction event that supplies the interaction context.
     */
    public InteractionAction(Player player, List<InteractionActionListener> listeners, Entity target, InteractableEvent event) {
        super(player, ActionType.WEAK, true, 1);
        this.listeners = listeners;
        this.target = target;
        this.event = event;
    }

    @Override
    public void onSubmit() {
        // Interrupt any other weak actions so this interaction takes precedence.
        mob.getActions().getAll(ActionType.WEAK).forEach(action -> {
            if (!(action instanceof InteractionAction)) {
                action.interrupt();
            }
        });

        // For mobs, set the interaction focus immediately on them.
        if (target instanceof Mob) {
            mob.interact(target);
        }

        // Run the action once instantly.
        if (run()) {
            interrupt();
        }
    }

    /**
     * Advances this interaction attempt by one cycle.
     * <p>
     * Each remaining listener is evaluated against the current interaction state:
     * <ul>
     *     <li>{@link InteractionType#UNSPECIFIED} listeners execute immediately and are removed</li>
     *     <li>reachable listeners are queued and removed for post-reach execution</li>
     * </ul>
     * <p>
     * If one or more listeners are reached, they are handled with {@link #onReached(boolean, boolean, InteractionPolicy, List)}.
     * If nothing is reached and the player is no longer walking, the interaction fails through {@link #onStanding()}.
     *
     * @return {@code true} if this action has completed, otherwise {@code false}.
     */
    @Override
    public boolean run() {
        CollisionManager collisionManager = mob.getWorld().getCollisionManager();

        InteractionPolicy trigger = null;
        List<InteractionActionListener> pending = new ArrayList<>();
        Iterator<InteractionActionListener> it = listeners.iterator();
        while (it.hasNext()) {
            InteractionActionListener listener = it.next();
            InteractionPolicy policy = listener.getPolicy();
            if (policy.getType() == InteractionType.UNSPECIFIED) {
                // Listener has no policy, run irrespective of interaction code.
                listener.getAction().run();
                it.remove();
                continue;
            }
            if (collisionManager.reached(mob.getPosition(), target, policy)) {
                // We've reached a listener with a satisfied policy, queue it for later.
                if (trigger == null) {
                    trigger = policy;
                }
                pending.add(listener);
                it.remove();
            }
        }

        if (!pending.isEmpty()) {
            boolean instantEvent = event instanceof AttackNpcEvent || event instanceof PlayerFirstClickEvent ||
                    event instanceof MagicOnNpcEvent || event instanceof MagicOnPlayerEvent || event instanceof PickupItemEvent;
            onReached(target instanceof Mob, instantEvent, trigger, pending);
            return true;
        } else if (mob.getWalking().isEmpty() && !listeners.isEmpty()) {
            onStanding();
            return true;
        } else if (listeners.isEmpty()) {
            mob.sendMessage("No listener for "+event.getClass().getSimpleName()+"("+event.target().toString()+")");
            return true;
        }
        return false;
    }

    /**
     * Handles a successful reach cycle for this interaction.
     * <p>
     * Any required corrective movement is resolved first. Listener execution is then deferred into a new weak action
     * so that movement, if any, can complete before the final interaction runs.
     *
     * @param isMob {@code true} if the target is a {@link Mob}.
     * @param trigger The authoritative interaction policy that was satisfied first for this reach cycle.
     * @param pending The listeners to execute once the interaction is finalized.
     */
    private void onReached(boolean isMob, boolean combatEvent, InteractionPolicy trigger, List<InteractionActionListener> pending) {
        boolean moved = moveBeforeInteract(isMob, combatEvent, trigger);

        if (getState() == ActionState.INTERRUPTED) {
            // We're immobilized but need movement to interact, end the action and short-circuit.
            return;
        }

        if (combatEvent) {
            if (!moved) {
                mob.getWalking().clear();
            }
            pending.forEach(listener -> listener.getAction().run());
        } else {
            mob.getActions().submitIfAbsent(new DelayedInteractionAction(pending, moved));
        }
    }

    /**
     * Performs any final movement adjustments required before the interaction executes.
     * <p>
     * This only applies to close {@link InteractionType#SIZE} interactions with distance {@code 1} against mobs or
     * size-{@code 1} entities. In that case, diagonal adjacency may be corrected by stepping to a cardinal tile, and
     * standing on the same tile may be corrected by stepping away.
     * <p>
     * For NPC interactions, an {@link NpcFocusAction} is also submitted so the NPC keeps facing and tracking the
     * player during the interaction.
     *
     * @param isMob {@code true} if the target is a {@link Mob}.
     * @param trigger The interaction policy that triggered the reach.
     * @return {@code true} if corrective movement was started, otherwise {@code false}.
     */
    private boolean moveBeforeInteract(boolean isMob, boolean combatEvent, InteractionPolicy trigger) {
        boolean moved = false;
        if ((isMob || target.size() == 1) && trigger.getType() == InteractionType.SIZE &&
                trigger.getDistance() == 1) {
            if (mob.getCombat().isImmobilized()) {
                // We need to move in order to interact, but we're immobilized.
                interrupt();
                return false;
            }
            Position current = mob.getPosition();
            Set<Direction> directions = getClosestNESW();
            if (!directions.isEmpty() && target.isWithinDistance(mob, 1)) {
                /*
                 * We are diagonal to the target: walk one step to the closest cardinal (NESW) tile so the player
                 * is no longer standing diagonally adjacent.
                 */
                for (Direction dir : directions) {
                    if (mob.getNavigator().step(dir)) {
                        break;
                    }
                }
                moved = true;
            } else if (target.getPosition().equals(current)) {
                /*
                 * We are standing on top of the entity's tile. Move one step in a random NESW direction to get off
                 * the tile before interacting.
                 */
                mob.getNavigator().stepRandom(false);
                moved = true;
            }

            if (target instanceof Npc && !combatEvent) {
                // If target is a NPC, make them look at and track the player.
                Npc npc = (Npc) target;
                npc.submitAction(new NpcFocusAction(npc, mob));
            }
        }
        return moved;
    }

    /**
     * Handles the case where the player has stopped walking without reaching the target.
     * <p>
     * This usually indicates that no valid route exists to satisfy any remaining interaction listeners.
     */
    private void onStanding() {
        mob.sendMessage("I can't reach that!");
    }

    /**
     * Returns the cardinal directions that best approximate the direction from the player to the target.
     * <p>
     * When the player is diagonally adjacent to the target, this returns the two relevant cardinal
     * directions used for corrective stepping. Otherwise, an empty set is returned.
     *
     * @return The candidate cardinal directions for diagonal correction.
     */
    private Set<Direction> getClosestNESW() {
        Direction direction = Direction.between(mob.getPosition(), target.getPosition());
        switch (direction) {
            case SOUTH_WEST:
                return Set.of(Direction.SOUTH, Direction.WEST);
            case SOUTH_EAST:
                return Set.of(Direction.SOUTH, Direction.EAST);
            case NORTH_WEST:
                return Set.of(Direction.NORTH, Direction.WEST);
            case NORTH_EAST:
                return Set.of(Direction.NORTH, Direction.EAST);
        }
        return Set.of();
    }

    /**
     * Replaces the remaining listeners for this action.
     *
     * @param newListeners The new listener list.
     */
    public void setListeners(List<InteractionActionListener> newListeners) {
        listeners = newListeners;
    }

    /**
     * @return The backing interaction event.
     */
    public InteractableEvent getEvent() {
        return event;
    }

    /**
     * Sets the current interaction target.
     *
     * @param target The new interaction target.
     */
    public void setTarget(Entity target) {
        this.target = target;
    }

    /**
     * @return The interaction target.
     */
    public Entity getTarget() {
        return target;
    }
}