package io.luna.game.model.mob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import io.luna.Luna;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.plugin.PluginManager;

import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a skill within a skill set.
 *
 * @author lare96
 */
public final class Skill {

    /**
     * An immutable list of the names of all skills.
     */
    public static final ImmutableList<String> NAMES = ImmutableList.of(
            "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic",
            "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
            "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecrafting"
    );

    /**
     * The combat skill identifiers.
     */
    public static final Range<Integer> COMBAT_IDS = Range.closed(0, 6);

    /**
     * An immutable map of names to identifiers.
     */
    public static final ImmutableMap<String, Integer> NAME_TO_ID;

    /**
     * The Attack identifier.
     */
    public static final int ATTACK = 0;

    /**
     * The Defence identifier.
     */
    public static final int DEFENCE = 1;

    /**
     * The Strength identifier.
     */
    public static final int STRENGTH = 2;

    /**
     * The Hitpoints identifier.
     */
    public static final int HITPOINTS = 3;

    /**
     * The Ranged identifier.
     */
    public static final int RANGED = 4;

    /**
     * The Prayer identifier.
     */
    public static final int PRAYER = 5;

    /**
     * The Magic identifier.
     */
    public static final int MAGIC = 6;

    /**
     * The Cooking identifier.
     */
    public static final int COOKING = 7;

    /**
     * The Woodcutting identifier.
     */
    public static final int WOODCUTTING = 8;

    /**
     * The Fletching identifier.
     */
    public static final int FLETCHING = 9;

    /**
     * The Fishing identifier.
     */
    public static final int FISHING = 10;

    /**
     * The Firemaking identifier.
     */
    public static final int FIREMAKING = 11;

    /**
     * The Crafting identifier.
     */
    public static final int CRAFTING = 12;

    /**
     * The Smithing identifier.
     */
    public static final int SMITHING = 13;

    /**
     * The Mining identifier.
     */
    public static final int MINING = 14;

    /**
     * The Herblore identifier.
     */
    public static final int HERBLORE = 15;

    /**
     * The Agility identifier.
     */
    public static final int AGILITY = 16;

    /**
     * The Thieving identifier.
     */
    public static final int THIEVING = 17;

    /**
     * The Slayer identifier.
     */
    public static final int SLAYER = 18;

    /**
     * The Farming identifier.
     */
    public static final int FARMING = 19;

    /**
     * The Runecrafting identifier.
     */
    public static final int RUNECRAFTING = 20;

    /**
     * Retrieves the name of a skill by its identifier.
     *
     * @param id The identifier.
     * @return The skill name.
     */
    public static String getName(int id) {
        return NAMES.get(id);
    }

    /**
     * Retrieves the identifier of a skill by its name.
     *
     * @param name The skill name.
     * @return The identifier.
     */
    public static int getId(String name) {
        return NAME_TO_ID.get(name);
    }

    /**
     * Determines if a skill is a factor in combat level calculations.
     *
     * @param id The skill identifier.
     * @return {@code true} if the identifier is a combat skill.
     */
    public static boolean isCombatSkill(int id) {
        return COMBAT_IDS.contains(id);
    }

    static {
        // Build and set [name -> identifier] cache.
        NAME_TO_ID = IntStream.range(0, NAMES.size()).boxed()
                .collect(ImmutableMap.toImmutableMap(NAMES::get, Function.identity()));
    }

    /**
     * The skill set.
     */
    private transient final SkillSet set;

    /**
     * The skill identifier.
     */
    private transient final int id;

    /**
     * The static (experience based) skill level. Cached to avoid potentially expensive {@link SkillSet#levelForExperience(int)} calls.
     */
    private transient int staticLevel = -1;

    /**
     * The dynamic skill level.
     */
    private int level = 1;

    /**
     * The attained experience.
     */
    private double experience;

    /**
     * Creates a new {@link Skill}.
     *
     * @param id The skill identifier.
     * @param set The skill set.
     */
    public Skill(int id, SkillSet set) {
        this.id = id;
        this.set = set;

        if (id == HITPOINTS) {
            level = 10;
            experience = 1300;
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Restores depleted or buffed skills.
     */
    private void restoreSkills() {
        if (!set.isRestoring()) {
            if (level != staticLevel) {
                var world = set.getMob().getWorld();
                world.schedule(new SkillRestorationTask(set));
            }
        }
    }

    /**
     * Adds experience to this skill.
     *
     * @param amount The amount of experience to add.
     */
    public void addExperience(double amount) {
        checkArgument(amount > 0, "amount <= 0");
        amount = amount * Luna.settings().game().experienceMultiplier();
        setExperience(experience + amount);
    }

    /**
     * Notifies plugins of any level or experience changes.
     *
     * @param oldExperience The old experience amount.
     * @param oldStaticLevel The old static level.
     * @param oldLevel The old dynamic level.
     */
    private void notifyListeners(double oldExperience, int oldStaticLevel, int oldLevel) {
        if (set.isFiringEvents()) {
            Mob mob = set.getMob();
            PluginManager plugins = mob.getPlugins();
            plugins.post(new SkillChangeEvent(mob, oldExperience, oldStaticLevel, oldLevel, id));
        }
    }

    /**
     * @return The name of this skill.
     */
    public String getName() {
        return NAMES.get(id);
    }

    /**
     * @return The skill identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The static (experience based) skill level.
     */
    public int getStaticLevel() {
        if (staticLevel == -1) {
            // The value hasn't been computed yet, do it now.
            staticLevel = SkillSet.levelForExperience((int) experience);
        }
        return staticLevel;
    }

    public void setStaticLevel(int level) {
        setExperience(SkillSet.experienceForLevel(level));

    }

    /**
     * @return The dynamic skill level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the dynamic skill level.
     *
     * @param newLevel The new level.
     */
    public void setLevel(int newLevel) {
        if (newLevel < 0) {
            newLevel = 0;
        }

        int oldLevel = level;

        level = newLevel;
        if (oldLevel == level) {
            return;
        }
        restoreSkills();
        notifyListeners(experience, getStaticLevel(), oldLevel);
    }

    /**
     * Increases the dynamic skill level by {@code amount}.
     *
     * @param amount The amount to increase by.
     * @param exceedStaticLevel If the bound should be set higher than the static level, or at the
     * static level.
     */
    public void addLevels(int amount, boolean exceedStaticLevel) {
        int bound = exceedStaticLevel ? getStaticLevel() + amount : getStaticLevel();
        int newAmount = getLevel() + amount;

        newAmount = Math.min(newAmount, bound);
        setLevel(newAmount);
    }

    /**
     * Decreases the dynamic skill level by {@code amount}.
     */
    public void removeLevels(int amount) {
        int newAmount = getLevel() - amount;
        newAmount = Math.max(newAmount, 0);
        setLevel(newAmount);
    }

    /**
     * @return The attained experience.
     */
    public double getExperience() {
        return experience;
    }

    /**
     * Sets the attained experience.
     *
     * @param newExperience The new experience value.
     */
    public void setExperience(double newExperience) {
        if (newExperience < 0) {
            newExperience = 0;
        } else if (newExperience > SkillSet.MAXIMUM_EXPERIENCE) {
            newExperience = SkillSet.MAXIMUM_EXPERIENCE;
        }
        if (experience == newExperience) {
            return;
        }
        int oldStaticLevel = getStaticLevel();
        double oldExperience = experience;
        experience = newExperience;
        staticLevel = -1;
        notifyListeners(oldExperience, oldStaticLevel, level);
    }
}
