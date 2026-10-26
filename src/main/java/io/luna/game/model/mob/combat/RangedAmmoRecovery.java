package io.luna.game.model.mob.combat;

import io.luna.game.model.Position;
import io.luna.game.model.chunk.ChunkUpdatableView;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.item.Item;
import io.luna.game.model.mob.Player;
import io.luna.game.task.Task;
import io.luna.util.RandomUtils;

/**
 * Owns one consumed projectile that survived the one-in-five ammunition breakage roll.
 * Recovery is independent of hit accuracy and recipient life: a paid projectile can land after either combatant
 * dies. The item is captured before equipment changes, and one recovery can produce at most one ground item.
 * Controllers may retain surviving ammunition at launch instead, as the Duel Arena does until settlement.
 *
 * @author lare96
 */
public final class RangedAmmoRecovery {

    /**
     * The player who paid for and initially owns this projectile.
     */
    private final Player owner;

    /**
     * One fired item, including its original poison variant.
     */
    private final Item item;

    /**
     * Whether this projectile has already been assigned a landing or discarded on blocked terrain.
     */
    private boolean completed;

    /**
     * Creates recovery ownership after the projectile survives its breakage roll.
     */
    private RangedAmmoRecovery(Player owner, Item item) {
        this.owner = owner;
        this.item = item;
    }

    /**
     * Rolls once for a committed shot and gives controllers first refusal of the surviving item.
     *
     * @param owner The firing player.
     * @param item The consumed single item, or null for nonrecoverable ammunition and charge-based weapons.
     * @return Ground recovery ownership, or null when nothing should be placed on the ground.
     */
    public static RangedAmmoRecovery launch(Player owner, Item item) {
        if (item == null || RandomUtils.inclusive(4) == 0 || owner.getControllers().getPrimary().recoverAmmo(item)) {
            return null;
        }
        return new RangedAmmoRecovery(owner, item);
    }

    /**
     * Assigns the projectile a single landing tile and deadline. Ordinary shots use the target tile captured at
     * launch; rebound specials call this when their chain finishes. A blocked tile destroys the surviving item.
     * The world task retains the drop even if combat impact cleanup discards the corresponding damage.
     *
     * @param position The immutable landing tile.
     * @param delay The non-negative number of ticks before the item lands.
     */
    public void land(Position position, int delay) {
        if (delay < 0) {
            throw new IllegalArgumentException("Ammunition landing delay cannot be negative.");
        }
        if (completed) {
            return;
        }
        completed = true;
        if (owner.getWorld().getCollisionManager().isBlocked(position, false)) {
            return;
        }
        if (delay == 0) {
            drop(position);
            return;
        }
        long deadline = owner.getWorld().getCurrentTick() + delay;
        owner.getWorld().schedule(new Task(false, 1) {
            @Override
            protected void execute() {
                if (owner.getWorld().getCurrentTick() >= deadline) {
                    cancel();
                    drop(position);
                }
            }
        });
    }

    /**
     * Registers the paid projectile with ordinary private ground-item visibility and expiration.
     */
    private void drop(Position position) {
        owner.getWorld().getItems().register(new GroundItem(owner.getContext(), item, position,
                ChunkUpdatableView.localView(owner)));
    }
}
