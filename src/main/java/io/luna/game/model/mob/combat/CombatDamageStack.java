package io.luna.game.model.mob.combat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multiset.Entry;
import io.luna.game.model.mob.Mob;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tracks combat damage dealt to a single {@link Mob}, grouped by damage source.
 * <p>
 * This stack buffers {@link CombatDamage} entries per attacker and can be queried for per-source totals, individual
 * hit lists, or the source that has dealt the highest total amount of damage.
 *
 * @author lare96
 */
public final class CombatDamageStack {

    /**
     * Buffered damage entries, grouped by attacking {@link Mob}.
     */
    private final ListMultimap<Mob, CombatDamage> buffer = ArrayListMultimap.create();

    /**
     * The mob this damage stack belongs to.
     */
    private final Mob mob;

    /**
     * Creates a new {@link CombatDamageStack}.
     *
     * @param mob The mob this damage stack belongs to.
     */
    public CombatDamageStack(Mob mob) {
        this.mob = mob;
    }

    /**
     * Pushes a new {@link CombatDamage} entry from {@code source} into this stack.
     *
     * @param source The mob that caused the damage.
     * @param damage The damage entry to buffer.
     */
    public void push(Mob source, CombatDamage damage) {
        if (!mob.equals(source)) {
            buffer.put(source, damage);
        }
    }

    /**
     * Returns the total buffered damage dealt by {@code mob}.
     *
     * @param mob The source mob to total damage for.
     * @return The total buffered damage.
     */
    public int getTotal(Mob mob) {
        int total = 0;
        for (CombatDamage damage : buffer.get(mob)) {
            total += damage.getAmount();
        }
        return total;
    }

    /**
     * Returns an immutable view of all buffered hits dealt by {@code mob}.
     *
     * @param mob The source mob to get hits for.
     * @return The buffered hits for that mob.
     */
    public List<CombatDamage> getHits(Mob mob) {
        return Collections.unmodifiableList(buffer.get(mob));
    }

    /**
     * Clears all buffered damage entries for {@code mob}.
     *
     * @param mob The source mob whose hits will be cleared.
     */
    public void clear(Mob mob) {
        buffer.removeAll(mob);
    }

    /**
     * Clears all buffered damage entries from this stack.
     */
    public void clear() {
        buffer.clear();
    }

    /**
     * Returns the source mob that has dealt the highest total buffered damage.
     *
     * @return The highest-damaging source mob, or {@code null} if this stack is empty.
     */
    public Mob getHighestDamage() {
        if (!buffer.isEmpty()) {
            Multiset<Mob> totals = HashMultiset.create();
            for (Map.Entry<Mob, Collection<CombatDamage>> entry : buffer.asMap().entrySet()) {
                for (CombatDamage damage : entry.getValue()) {
                    totals.add(entry.getKey(), damage.getAmount());
                }
            }
            Mob lastMob = null;
            int lastTotalDamage = -1;
            for (Entry<Mob> mobEntry : totals.entrySet()) {
                int totalDamage = mobEntry.getCount();
                if (totalDamage > lastTotalDamage) {
                    lastMob = mobEntry.getElement();
                    lastTotalDamage = totalDamage;
                }
            }
            return lastMob;
        }
        return null;
    }
}