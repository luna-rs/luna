package io.luna.game.model.mob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.primitives.Ints;

import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a group of skills.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class SkillSet implements Iterable<Skill> {

    /**
     * An immutable list containing the experience needed for each level.
     */
    public static final List<Integer> EXPERIENCE_TABLE;

    /**
     * A range containing valid skill identifiers.
     */
    public static final Range<Integer> SKILL_IDS = Range.closedOpen(0, 21);

    /**
     * The maximum amount of attainable experience in a single skill.
     */
    public static final int MAXIMUM_EXPERIENCE = 200_000_000;

    /**
     * Retrieves the amount of experience needed to be attained for a level.
     *
     * @param level The level to determine for.
     * @return The experience needed.
     */
    public static int experienceForLevel(int level) {
        checkArgument(level >= 1 && level <= 99, "level < 1 || level > 99");
        
        return EXPERIENCE_TABLE.get(level);
    }

    /**
     * Returns the total amount of valid skills.
     */
    public static int size() {
        return SKILL_IDS.upperEndpoint();
    }

    /**
     * Retrieves a level for the attained experience. Runs in O(n) time, but should still run very fast
     * assuming the experience value is high.
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

    static { /* Initialize experience table cache. */
        int[] experienceTable = new int[100];
        int points = 0, output = 0;
        
        for (int lvl = 1; lvl <= 99; lvl++) {
            experienceTable[lvl] = output;
            points += Math.floor(lvl + 300D * Math.pow(2D, lvl / 7D));
            output = (int) Math.floor(points / 4D);
        }
        
        EXPERIENCE_TABLE = Arrays.stream(experienceTable).boxed().collect(Collectors.toUnmodifiableList());
    }

    /**
     * The mob.
     */
    private final Mob mob;

    /**
     * The array of skills.
     */
    private final Skill[] skills;

    /**
     * The cached combat level.
     */
    private int combatLevel = -1;

    /**
     * If this skill set is firing events.
     */
    private boolean firingEvents = true;

    /**
     * Creates a new {@link SkillSet}.
     *
     * @param mob The mob.
     */
    public SkillSet(Mob mob) {
        this.mob = mob;

        // Populate the skill set.
        skills = new Skill[size()];
        
        for (int index = 0; index < skills.length; index++) {
            skills[index] = new Skill(index, this);
        }
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
     * Resets the cached combat level.
     */
    public void resetCombatLevel() {
        combatLevel = -1;
    }

    /**
     * Retrieve the skill with the argued identifier.
     *
     * @param id The skill identifier.
     * @return The skill.
     */
    public Skill getSkill(int id) {
        return skills[id];
    }

    /**
     * Returns a <strong>shallow</strong> copy of the backing array.
     *
     * @return A copy of the skills.
     */
    public Skill[] toArray() {
        return Arrays.copyOf(skills, skills.length);
    }

    /**
     * Sets the backing array of skills. The backing array will not hold any references to the argued
     * array. The argued array must have a capacity equal to that of the backing array.
     */
    public void setSkills(Skill[] newSkills) {
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
     * @return The mob.
     */
    public Mob getMob() {
        return mob;
    }

    /**
     * Will either compute the combat level and cache it, or return the cached value.
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
            double prayerCalc = (prayer / 2D) * 0.25;

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
     * Constructs a stream that will traverse over this iterable.
     */
    public Stream<Skill> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * @return {@code true} if this skill set is firing events.
     */
    public boolean isFiringEvents() {
        return firingEvents;
    }

    /**
     * Sets if this skill set is firing events.
     *
     * @param firingEvents The value to set.
     */
    public void setFiringEvents(boolean firingEvents) {
        this.firingEvents = firingEvents;
    }
}
