package io.luna.game.model.mobile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * A collection of {@link Skill}s for a {@link MobileEntity}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SkillSet implements Iterable<Skill> {

    /**
     * An {@link ImmutableList} containing the experience needed for each level.
     */
    public static final ImmutableList<Integer> EXPERIENCE_TABLE;

    /**
     * A {@link Range} containing all valid skill identifier values.
     */
    public static final Range<Integer> SKILL_IDENTIFIERS = Range.closedOpen(0, 21);

    /**
     * The maximum amount of experience that can be obtained in a single skill.
     */
    public static final int MAXIMUM_EXPERIENCE = 200_000_000;

    /**
     * The amount that all experience will be multiplied by.
     */
    public static final double EXPERIENCE_MULTIPLIER = 1;

    /**
     * Retrieves the experience amount for {@code level}. Runs in O(1) time.
     *
     * @param level The level to retrieve the experience for.
     * @return The experience for the level.
     */
    public static int experienceForLevel(int level) {
        checkArgument(level >= 1 && level <= 99, "level < 1 || level > 99");
        return EXPERIENCE_TABLE.get(level);
    }

    /**
     * Returns the size of all {@code SkillSet}s.
     */
    public static int size() {
        return SKILL_IDENTIFIERS.upperEndpoint();
    }

    /**
     * Retrieves the level for {@code experience}. Runs in O(n) time, but should still return blazingly fast assuming the
     * {@code experience} value is high.
     *
     * @param experience The experience to retrieve the level for.
     * @return The level for the experience.
     */
    public static int levelForExperience(int experience) {
        checkArgument(experience >= 0 && experience <= MAXIMUM_EXPERIENCE,
            "experience < 0 || experience > MAXIMUM_EXPERIENCE");

        if (experience == 0) {
            return 1;
        }

        for (int index = 99; index > 0; index--) {
            if (EXPERIENCE_TABLE.get(index) > experience) {
                continue;
            }
            return index;
        }
        throw new IllegalStateException("unable to compute level for experience amount, " + experience);
    }

    /**
     * Computes the values for the experience table.
     */
    static {
        int[] experienceTable = new int[100];
        int points = 0, output = 0;
        for (int lvl = 1; lvl <= 99; lvl++) {
            experienceTable[lvl] = output;
            points += Math.floor(lvl + 300.0 * Math.pow(2.0, lvl / 7.0));
            output = (int) Math.floor(points / 4);
        }
        EXPERIENCE_TABLE = ImmutableList.copyOf(Ints.asList(experienceTable));
    }

    /**
     * The {@link MobileEntity} instance.
     */
    private final MobileEntity mob;

    /**
     * The array of {@link Skill}s contained within this {@code SkillSet}.
     */
    private final Skill[] skills = IntStream.range(SKILL_IDENTIFIERS.lowerEndpoint(), SKILL_IDENTIFIERS.upperEndpoint())
        .mapToObj(it -> new Skill(it, this)).toArray(Skill[]::new);

    /**
     * The cached combat level of the {@code mob} in this skill set.
     */
    private int combatLevel = -1;

    /**
     * If this skill set is firing events.
     */
    private boolean firingEvents = true;

    /**
     * Creates a new {@link SkillSet}.
     *
     * @param mob The {@link MobileEntity} instance.
     */
    public SkillSet(MobileEntity mob) {
        this.mob = mob;
    }

    @Override
    public Iterator<Skill> iterator() {
        Iterator<Skill> mutableIterator = Arrays.asList(skills).iterator();
        return Iterators.unmodifiableIterator(mutableIterator);
    }

    @Override
    public Spliterator<Skill> spliterator() {
        return Spliterators
            .spliterator(skills, Spliterator.NONNULL | Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.DISTINCT);
    }

    /**
     * Resets the cached combat level.
     */
    public void resetCombatLevel() {
        combatLevel = -1;
    }

    /**
     * Retrieve the {@link Skill} instance that corresponds to {@code id}.
     *
     * @param id The identifier for the {@code Skill} that will be retrieved.
     * @return The retrieved {@code Skill}.
     */
    public Skill getSkill(int id) {
        return skills[id];
    }

    /**
     * <strong>Please note that this function does not give direct access to the backing array but instead creates a shallow
     * copy.</strong>
     *
     * @return The shallow copy of the backing skill array.
     */
    public Skill[] toArray() {
        return Arrays.copyOf(skills, skills.length);
    }

    /**
     * Sets the backing array of skills to {@code newSkills}. The backing array will not hold any references to the argued
     * array. The argued array must have a capacity equal to that of the backing array.
     */
    public void setSkills(Skill[] newSkills) {
        checkState(newSkills.length == skills.length, "incompatible skill array");

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
     * @return The {@link MobileEntity} instance.
     */
    public MobileEntity getMob() {
        return mob;
    }

    /**
     * @return The cached combat level of the {@code mob} in this skill set.
     */
    public int getCombatLevel() {
        if (combatLevel == -1) {
            int magLvl = skills[Skill.MAGIC].getStaticLevel();
            int ranLvl = skills[Skill.RANGED].getStaticLevel();
            int attLvl = skills[Skill.ATTACK].getStaticLevel();
            int strLvl = skills[Skill.STRENGTH].getStaticLevel();
            int defLvl = skills[Skill.DEFENCE].getStaticLevel();
            int hitLvl = skills[Skill.HITPOINTS].getStaticLevel();
            int prayLvl = skills[Skill.PRAYER].getStaticLevel();

            double mag = magLvl * 1.5;
            double ran = ranLvl * 1.5;
            double attstr = attLvl + strLvl;
            double combatLvl = 0;

            if (ran > attstr && ran > mag) {
                combatLvl = ((defLvl) * 0.25) + ((hitLvl) * 0.25) + ((prayLvl / 2) * 0.25) + ((ranLvl) * 0.4875);
            } else if (mag > attstr) {
                combatLvl = (((defLvl) * 0.25) + ((hitLvl) * 0.25) + ((prayLvl / 2) * 0.25) + ((magLvl) * 0.4875));
            } else {
                combatLvl = (((defLvl) * 0.25) + ((hitLvl) * 0.25) + ((prayLvl / 2) * 0.25) + ((attLvl) * 0.325) + ((strLvl) * 0.325));
            }
            combatLevel = (int) combatLvl;
        }
        return combatLevel;
    }

    /**
     * @return The {@link Stream} that will traverse over this iterable.
     */
    public Stream<Skill> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * @return {@code true} if this skill set is firing events, {@code false} otherwise.
     */
    public boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Sets if this skill set is firing events or not.
     */
    public void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }
}
