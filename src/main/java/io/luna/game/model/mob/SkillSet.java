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
 * Holds all {@link Skill} instances for a single {@link Mob}.
 * <p>
 * Each skill is stored by its skill id and remains bound to this set for its lifetime unless the set is rebuilt
 * through {@link #set(Skill[])}. This type also provides shared utility for RuneScape experience and level
 * calculations, combat level caching, skill restoration scheduling, and skill event suppression during bulk updates.
 *
 * @author lare96
 */
public final class SkillSet implements Iterable<Skill> {

    /**
     * Cumulative experience thresholds for levels {@code 1..99}.
     * <p>
     * Index {@code 0} is unused so level values can be used directly as indices.
     */
    public static final int[] EXPERIENCE_TABLE;

    /**
     * The valid range of skill identifiers.
     * <p>
     * This is the half-open range {@code [0, 21)}, meaning valid skill ids are {@code 0..20}.
     */
    public static final Range<Integer> SKILL_IDS = Range.closedOpen(0, 21);

    /**
     * The maximum amount of experience a single skill may hold.
     */
    public static final int MAXIMUM_EXPERIENCE = 200_000_000;

    /**
     * Returns the cumulative experience required to reach {@code level}.
     *
     * @param level The level to query.
     * @return The cumulative experience required for {@code level}.
     * @throws IllegalArgumentException If {@code level} is not within {@code 1..99}.
     */
    public static int experienceForLevel(int level) {
        checkArgument(level >= 1 && level <= 99, "level < 1 || level > 99");
        return EXPERIENCE_TABLE[level];
    }

    /**
     * Returns the total number of supported skills.
     *
     * @return The number of valid skill ids.
     */
    public static int size() {
        return SKILL_IDS.upperEndpoint();
    }

    /**
     * Computes the level for a total experience amount.
     * <p>
     * The experience table is scanned from level {@code 99} downward until the highest matching threshold is found.
     *
     * @param experience The total experience to convert.
     * @return The corresponding level.
     * @throws IllegalArgumentException If {@code experience} is negative or exceeds
     *         {@link #MAXIMUM_EXPERIENCE}.
     */
    public static int levelForExperience(int experience) {
        checkArgument(experience >= 0 && experience <= MAXIMUM_EXPERIENCE,
                "experience < 0 || experience > MAXIMUM_EXPERIENCE");

        if (experience == 0) {
            return 1;
        }

        for (int index = 99; index > 0; index--) {
            if (EXPERIENCE_TABLE[index] > experience) {
                continue;
            }
            return index;
        }
        throw new IllegalStateException("unable to compute level for experience amount, " + experience);
    }

    static { /* Initialize the experience table cache. */
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
     * The backing skill array, indexed directly by skill id.
     */
    private final Skill[] skills;

    /**
     * Cached combat level for this mob.
     * <p>
     * A value of {@code -1} indicates that the cache is invalid and must be recomputed.
     */
    private int combatLevel = -1;

    /**
     * Custom skill-level display value used by special client interactions such as the Games Room.
     * <p>
     * When non-zero, players may display this value instead of combat level in certain client views.
     */
    private int skillLevel;

    /**
     * Whether skill-related events should currently be fired.
     * <p>
     * This is temporarily disabled during bulk updates such as {@link #set(Skill[])} to avoid emitting redundant
     * {@link SkillChangeEvent}s.
     */
    private boolean firingEvents = true;

    /**
     * Creates a new {@link SkillSet} for {@code mob}.
     * <p>
     * All supported skills are initialized immediately and stored at their matching skill ids.
     *
     * @param mob The mob that owns this skill set.
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
     * Invalidates the cached combat level.
     * <p>
     * The combat level will be recomputed the next time {@link #getCombatLevel()} is called.
     */
    public void resetCombatLevel() {
        combatLevel = -1;
    }

    /**
     * Returns the skill for {@code id}.
     *
     * @param id The skill id.
     * @return The skill stored at {@code id}.
     * @throws ArrayIndexOutOfBoundsException If {@code id} is not a valid skill id.
     */
    public Skill getSkill(int id) {
        return skills[id];
    }

    /**
     * Resets a single skill's dynamic level back to its static level.
     *
     * @param id The skill id to reset.
     * @return {@code true} if the dynamic level changed, otherwise {@code false}.
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
     * @return {@code true} if at least one skill changed, otherwise {@code false}.
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
     * Returns a shallow copy of the backing skill array.
     * <p>
     * The returned array is new, but the contained {@link Skill} instances are the same references held by this set.
     *
     * @return A shallow copy of the skill array.
     */
    public Skill[] toArray() {
        return Arrays.copyOf(skills, skills.length);
    }

    /**
     * Replaces this set's skill data with values copied from {@code newSkills}.
     * <p>
     * This method does not retain the incoming {@link Skill} instances. Instead, it creates new skills bound to this
     * {@link SkillSet}, then copies each source skill's experience and dynamic level into the new instance.
     * <p>
     * Skill-related events are temporarily suppressed for the duration of the bulk update.
     *
     * @param newSkills The source skill data to copy from.
     * @throws IllegalArgumentException If {@code newSkills.length} does not equal {@link #size()}.
     */
    public void set(Skill[] newSkills) {
        checkArgument(newSkills.length == skills.length, "newSkills.length [" + newSkills.length + "] must equal skills.length [" + skills.length + "].");

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
     * Returns the mob that owns this skill set.
     *
     * @return The owning mob.
     */
    public Mob getMob() {
        return mob;
    }

    /**
     * Returns this mob's combat level.
     * <p>
     * The value is computed from static skill levels and cached until invalidated through {@link #resetCombatLevel()}.
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
     * Returns the custom skill-level display value.
     *
     * @return The custom display value.
     */
    public int getSkillLevel() {
        return skillLevel;
    }

    /**
     * Sets the custom skill-level display value.
     * <p>
     * When non-zero, some clients will display this value instead of combat level in player interaction menus. This
     * only applies to {@link Player} instances. For other {@link Mob} types, the call is ignored.
     *
     * @param skillLevel The custom display value to set.
     */
    public void setSkillLevel(int skillLevel) {
        if (mob instanceof Player) {
            this.skillLevel = skillLevel;
            mob.getFlags().flag(UpdateFlag.APPEARANCE);
        }
    }

    /**
     * Ensures skill restoration is running when a skill has diverged from its static level.
     * <p>
     * If {@code trigger}'s dynamic level differs from its static level and the owning mob does not already have an
     * active {@link SkillRestorationAction}, one is submitted.
     *
     * @param trigger The skill change that may require restoration scheduling.
     */
    void restoreSkills(Skill trigger) {
        if (trigger.getLevel() != trigger.getStaticLevel() && !mob.getActions().contains(SkillRestorationAction.class)) {
            mob.submitAction(new SkillRestorationAction(mob));
        }
    }

    /**
     * @return A sequential stream of skills.
     */
    public Stream<Skill> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * @return {@code true} if skill events are enabled.
     */
    public boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Enables or disables skill-related event firing for this set.
     *
     * @param firingEvents {@code true} to enable event firing, otherwise {@code false}.
     */
    public void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }
}