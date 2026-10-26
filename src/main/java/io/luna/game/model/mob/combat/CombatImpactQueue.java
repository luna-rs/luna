package io.luna.game.model.mob.combat;

import io.luna.game.model.EntityState;
import io.luna.game.model.mob.Mob;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Incoming combat events owned by their recipient, timed against the world's tick clock. Committed hits survive
 * interruption, death, or removal of the attacker. Clearing the recipient's life discards its remaining events.
 * Events with the same deadline retain submission order; callbacks cannot recursively execute another event
 * during the same queue pass.
 */
public final class CombatImpactQueue {

    private static final Logger logger = LogManager.getLogger();

    private record Impact(long tick, long sequence, long generation, Runnable arrival,
                          Consumer<Boolean> finished) { }

    private final Mob recipient;
    private PriorityQueue<Impact> pending;
    private long sequence;
    private long generation;

    public CombatImpactQueue(Mob recipient) {
        this.recipient = recipient;
    }

    /**
     * Schedules an incoming event. Zero means the next recipient queue pass in the current tick, if it has not
     * happened yet. The finish callback receives true when the recipient is no longer valid or delivery throws.
     */
    public void schedule(int delay, Runnable arrival, Consumer<Boolean> finished) {
        checkArgument(delay >= 0, "Impact delay cannot be negative.");
        Impact impact = new Impact(recipient.getWorld().getCurrentTick() + delay, sequence++, generation,
                arrival, finished);
        if (!isValid()) {
            finish(impact, true);
            return;
        }
        if (pending == null) {
            pending = new PriorityQueue<>(Comparator.comparingLong(Impact::tick).thenComparingLong(Impact::sequence));
        }
        pending.add(impact);
    }

    /** Applies due events before this recipient's ordinary actions and combat interaction. */
    public void process() {
        if (pending == null) {
            return;
        }
        if (!isValid()) {
            clear();
            return;
        }
        long tick = recipient.getWorld().getCurrentTick();
        List<Impact> due = new ArrayList<>();
        while (!pending.isEmpty() && pending.peek().tick <= tick) {
            due.add(pending.remove());
        }
        for (Impact impact : due) {
            boolean interrupted = true;
            try {
                if (generation == impact.generation && isValid()) {
                    impact.arrival.run();
                    interrupted = false;
                }
            } catch (Exception e) {
                logger.error("An error occurred while delivering a combat impact.", e);
            } finally {
                finish(impact, interrupted);
            }
        }
    }

    /** Discards incoming work when its recipient dies, despawns, logs out, or otherwise leaves this life. */
    public void clear() {
        generation++;
        PriorityQueue<Impact> discarded = pending;
        pending = null;
        if (discarded != null) {
            while (!discarded.isEmpty()) {
                finish(discarded.remove(), true);
            }
        }
    }

    private boolean isValid() {
        return recipient.isAlive() && recipient.getState() == EntityState.ACTIVE;
    }

    private void finish(Impact impact, boolean interrupted) {
        try {
            impact.finished.accept(interrupted);
        } catch (Exception e) {
            logger.error("An error occurred while finishing a combat impact.", e);
        }
    }
}
