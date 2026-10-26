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
 * Schedules incoming combat events for a single recipient using its world's game tick counter.
 * <p>
 * The recipient owns pending events independently of the attacker's action queue. Stopping the attacker, clearing
 * its actions, or removing it from the world does not cancel an already committed hit. The recipient must still
 * be alive and active when delivery is attempted.
 * <p>
 * Events are ordered by deadline, then submission order. {@link #process()} snapshots due events before invoking
 * callbacks, so an event submitted by another callback waits for a later queue pass. {@link #clear()} also
 * invalidates due events already removed from the pending queue but not yet delivered.
 * <p>
 * All scheduling, delivery, and cleanup occur on the game thread. Recipient lifecycle and minigame cleanup call
 * {@link #clear()} to prevent pending work from reaching a new life or a completed match.
 *
 * @author lare96
 */
public final class CombatImpactQueue {

    /**
     * The logger used for arrival and completion callback failures.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * A scheduled incoming event and the queue generation in which it was submitted.
     *
     * @param tick The earliest world tick on which the event may be delivered.
     * @param sequence The submission order used to resolve equal deadlines.
     * @param generation The queue generation that must remain current until delivery.
     * @param arrival The callback that applies the hit or projectile event.
     * @param finished The callback notified after delivery or interruption.
     * @author lare96
     */
    private record Impact(long tick, long sequence, long generation, Runnable arrival,
                          Consumer<Boolean> finished) { }

    /**
     * The mob receiving this queue's combat events.
     */
    private final Mob recipient;

    /**
     * Pending events ordered by deadline and submission sequence, allocated on first use.
     */
    private PriorityQueue<Impact> pending;

    /**
     * The sequence assigned to the next event.
     */
    private long sequence;

    /**
     * The current queue generation, advanced whenever pending work is cleared.
     */
    private long generation;

    /**
     * Creates a new {@link CombatImpactQueue}.
     *
     * @param recipient The mob whose turn processes the incoming events.
     */
    public CombatImpactQueue(Mob recipient) {
        this.recipient = recipient;
    }

    /**
     * Schedules an incoming event relative to the current world tick.
     * <p>
     * A zero delay permits delivery in the current tick if the recipient's queue pass has not run yet. Otherwise,
     * delivery waits for a later pass. An inactive or dead recipient rejects the event immediately and receives
     * only an interrupted completion notification.
     *
     * @param delay The non-negative number of game ticks before the event is due.
     * @param arrival The callback invoked when the event is due and its recipient remains valid.
     * @param finished The completion callback; {@code true} indicates interruption or an arrival failure,
     * otherwise {@code false} indicates that the arrival callback returned normally.
     * @throws IllegalArgumentException If {@code delay} is negative.
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

    /**
     * Processes a snapshot of due events before the recipient's ordinary actions and outgoing combat.
     * <p>
     * Each event rechecks the recipient and queue generation before delivery. A lethal hit or a call to
     * {@link #clear()} therefore prevents the remaining snapshot entries from being delivered. Callback failures
     * are logged, and completion is still reported without stopping the other due events.
     */
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

    /**
     * Discards pending events and advances the queue generation.
     * <p>
     * Pending events receive interrupted completion notifications immediately. Events in a delivery snapshot are
     * invalidated by the generation change and receive their notification when that snapshot continues.
     * A callback already running is allowed to finish. Calling this method again does not notify discarded
     * events a second time.
     */
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

    /**
     * Determines whether the recipient may receive an incoming combat event.
     *
     * @return {@code true} if the recipient is alive and {@link EntityState#ACTIVE}, otherwise {@code false}.
     */
    private boolean isValid() {
        return recipient.isAlive() && recipient.getState() == EntityState.ACTIVE;
    }

    /**
     * Reports an event's completion without allowing a callback failure to stop queue processing.
     *
     * @param impact The event being completed or discarded.
     * @param interrupted Whether delivery was interrupted or failed.
     */
    private void finish(Impact impact, boolean interrupted) {
        try {
            impact.finished.accept(interrupted);
        } catch (Exception e) {
            logger.error("An error occurred while finishing a combat impact.", e);
        }
    }
}
