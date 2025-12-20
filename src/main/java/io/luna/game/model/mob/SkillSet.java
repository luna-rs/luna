package io.luna.game.model.mob;

import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.collect.UnmodifiableIterator;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag;

import java.util.Arrays;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Container for all {@link Skill} instances owned by a {@link Mob}.
 *
 * @author lare96
 */
public final class SkillSet implements Iterable<Skill> {

    /**
     * Cumulative experience thresholds for levels 1..99.
     * <p>
     * Index 0 is unused (left as 0) so that the level can be used directly as an index.
     * </p>
     */
    public static final int[] EXPERIENCE_TABLE;

    /**
     * Range of valid skill identifiers.
     * <p>
     * This is a closed range: {@code [0, 20)} which corresponds to ids 0..20 inclusive.
     * </p>
     */
    public static final Range<Integer> SKILL_IDS = Range.closed(0, 20);

    /**
     * The maximum attainable experience in a single skill.
     */
    public static final int MAXIMUM_EXPERIENCE = 200_000_000;

    /**
     * Returns the cumulative experience required to reach {@code level}.
     *
     * @param level The level to query (1..99).
     * @return The experience threshold for that level.
     * @throws IllegalArgumentException if {@code level < 1 || level > 99}.
     */
    public static int experienceForLevel(int level) {
        checkArgument(level >= 1 && level <= 99, "level < 1 || level > 99");
        return EXPERIENCE_TABLE[level];
    }

    /**
     * Returns the total number of valid skills (currently {@code 21}).
     */
    public static int size() {
        return SKILL_IDS.upperEndpoint();
    }

    /**
     * Computes the level for a given total experience amount.
     * <p>
     * This walks the experience table from 99 down to 1 and returns the first level whose threshold is
     * {@code <= experience}. Runtime is O(99) worst-case and effectively constant.
     * </p>
     *
     * @param experience Total experience (0..{@link #MAXIMUM_EXPERIENCE}).
     * @return The computed level (1..99).
     * @throws IllegalArgumentException if {@code experience < 0 || experience > MAXIMUM_EXPERIENCE}.
     */
    public static int levelForExperience(int experience) {
        checkArgument(experience >= 0 && experience <= MAXIMUM_EXPERIENCE,
                "experience < 0 || experience > MAXIMUM_EXPERIENCE");

        if (experience == 0) {
            return 1;
        }

        for (int index = 99; index > 0; index--) {
            if (EXPERIENCE_TABLE [index] > experience) {
                continue;
            }
            return index;
        }
        throw new IllegalStateException("unable to compute level for experience amount, " + experience);
    }

    static { /* Initialize experience table cache. */
        int[] experienceTable = new int[100];
        int points = 0, output = 0;
        for (int lvl = 1; lvl <= 99; lvl++) {
            experienceTable[lvl] = output;
            points += (int) Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
            output = (int) Math.floor(points / 4.0);
        }
        EXPERIENCE_TABLE = experienceTable;
    }

    /**
     * The mob that owns this skill set.
     */
    private final Mob mob;

    /**
     * Backing array of skills indexed by skill id.
     */
    private final Skill[] skills;

    /**
     * Cached combat level computed from static levels.
     * <p>
     * Reset to {@code -1} when invalidated so it can be recomputed lazily.
     * </p>
     */
    private int combatLevel = -1;

    /**
     * Custom “skill level” display value (used by the Games Room in RuneScape).
     * <p>
     * If non-zero, some clients show this value instead of combat level on right-click/player examine.
     * </p>
     */
    private int skillLevel;

    /**
     * Whether this skill set should post skill-related events (see {@link SkillChangeEvent}).
     * <p>
     * Temporarily disabled during bulk operations like {@link #set(Skill[])} to avoid spamming events.
     * </p>
     */
    private boolean firingEvents = true;

    /**
     * Whether a {@link SkillRestorationTask} is currently active for this skill set.
     * <p>
     * This acts as a guard flag to prevent scheduling multiple restoration tasks concurrently.
     * </p>
     */
    private boolean restoring;

    /**
     * Creates a new {@link SkillSet} for {@code mob}, initializing all skills.
     *
     * @param mob The owning mob.
     */
    public SkillSet(Mob mob) {
        this.mob = mob;
        this.skills = IntStream.range(0, size()).mapToObj(i -> new Skill(i, this)).toArray(Skill[]::new);
    }

    @Override
    public UnmodifiableIterator<Skill> iterator() {
        return Iterators.forArray(skills);
    }

    @Override
    public Spliterator<Skill> spliterator() {
        return Spliterators.spliterator(skills, Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.ORDERED);
    }

    /**
     * Invalidates the cached combat level so it will be recomputed on next {@link #getCombatLevel()} call.
     */
    public void resetCombatLevel() {
        combatLevel = -1;
    }

    /**
     * Retrieves the {@link Skill} for the given skill id.
     *
     * @param id The skill identifier.
     * @return The skill instance.
     * @throws ArrayIndexOutOfBoundsException if {@code id} is not a valid skill id.
     */
    public Skill getSkill(int id) {
        return skills[id];
    }

    /**
     * Resets the dynamic level of a single skill back to its static level.
     *
     * @param id The skill id to reset.
     * @return {@code true} if the skill changed, otherwise {@code false}.
     */
    public boolean reset(int id) {
        var resetSkill = skills[id];
        int realLevel = resetSkill.getStaticLevel();
        if (resetSkill.getLevel() != realLevel) {
            resetSkill.setLevel(realLevel);
            return true;
        }
        return false;
    }

    /**
     * Resets all skills' dynamic levels back to their static levels.
     *
     * @return {@code true} if at least one skill changed.
     */
    public boolean resetAll() {
        var changed = false;
        for (int index = 0; index < skills.length; index++) {
            if (reset(index)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Returns a <b>shallow</b> copy of the backing skill array.
     */
    public Skill[] toArray() {
        return Arrays.copyOf(skills, skills.length);
    }

    /**
     * Replaces the skill data in this set with data from {@code newSkills}.
     * <p>
     * This performs a value-copy:
     * </p>
     * <ul>
     *     <li>Creates new {@link Skill} instances bound to this {@link SkillSet}.</li>
     *     <li>Copies experience and dynamic level from the corresponding element in {@code newSkills}.</li>
     *     <li>Temporarily disables {@link #isFiringEvents()} to avoid emitting events during the bulk update.</li>
     * </ul>
     * <p>
     * The incoming array must have the same length as this set ({@link #size()}).
     * </p>
     *
     * @param newSkills The source skill data.
     * @throws IllegalArgumentException if {@code newSkills.length != skills.length}.
     */
    public void set(Skill[] newSkills) {
        checkArgument(newSkills.length == skills.length, "newSkills.length must equal skills.length");

        firingEvents = false;
        try {
            int index = 0;
            for (Skill newSkill : newSkills) {
                Skill skill = new Skill(index, this);
                skill.setExperience(newSkill.getExperience());
                skill.setLevel(newSkill.getLevel());
                skills[index++] = skill;
            }
        } finally {
            firingEvents = true;
        }
    }

    /**
     * Returns the owning mob.
     */
    public Mob getMob() {
        return mob;
    }

    /**
     * Returns the combat level for this mob, computing and caching it if needed.
     * <p>
     * Uses static levels only (experience-based levels). The result is cached until invalidated with {@link #resetCombatLevel()}.
     * </p>
     *
     * @return The combat level.
     */
    public int getCombatLevel() {
        if (combatLevel == -1) {
            int magic = skills[Skill.MAGIC].getStaticLevel();
            int ranged = skills[Skill.RANGED].getStaticLevel();
            int attack = skills[Skill.ATTACK].getStaticLevel();
            int strength = skills[Skill.STRENGTH].getStaticLevel();
            int defence = skills[Skill.DEFENCE].getStaticLevel();
            int hitpoints = skills[Skill.HITPOINTS].getStaticLevel();
            int prayer = skills[Skill.PRAYER].getStaticLevel();

            double defenceCalc = defence * 0.25;
            double hitpointsCalc = hitpoints * 0.25;
            double prayerCalc = (prayer / 2.0) * 0.25;

            double mag = magic * 1.5;
            double ran = ranged * 1.5;
            double attstr = attack + strength;

            double combatLvl;
            if (ran > attstr && ran > mag) {
                combatLvl = defenceCalc + hitpointsCalc + prayerCalc + (ranged * 0.4875);
            } else if (mag > attstr) {
                combatLvl = defenceCalc + hitpointsCalc + prayerCalc + (magic * 0.4875);
            } else {
                combatLvl = defenceCalc + hitpointsCalc + prayerCalc + (attack * 0.325) + (strength * 0.325);
            }
            combatLevel = (int) combatLvl;
        }
        return combatLevel;
    }

    /**
     * Returns the custom “skill level” display value (Games Room style).
     *
     * @return The skill level display value.
     */
    public int getSkillLevel() {
        return skillLevel;
    }

    /**
     * Sets a custom “skill level” display value (Games Room style).
     * <p>
     * If {@code skillLevel != 0},  clients will display it instead of combat level in  the right-click player menu.
     * This only applies to {@link Player}s; for other {@link Mob} types, the call is ignored.
     * </p>
     *
     * @param skillLevel The custom skill level value.
     */
    public void setSkillLevel(int skillLevel) {
        if (mob instanceof Player) {
            this.skillLevel = skillLevel;
            mob.getFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Returns a sequential stream over all skills in this set.
     */
    public Stream<Skill> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Returns whether this skill set should fire skill-related events.
     *
     * @return {@code true} if events are enabled.
     */
    public boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Enables or disables skill-related event firing for this set.
     *
     * @param firingEvents {@code true} to enable event firing, {@code false} to disable it.
     */
    public void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }

    /**
     * Returns whether a restoration task is currently active for this skill set.
     *
     * @return {@code true} if currently restoring, otherwise {@code false}.
     */
    public boolean isRestoring() {
        return restoring;
    }

    /**
     * Sets whether a restoration task is currently active for this skill set.
     *
     * @param restoring The new restoring state.
     */
    public void setRestoring(boolean restoring) {
        this.restoring = restoring;
    }
}
