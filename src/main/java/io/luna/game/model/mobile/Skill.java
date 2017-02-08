package io.luna.game.model.mobile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.plugin.PluginManager;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A model representing a skill within a skill set.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Skill {

    /**
     * The ordered names of all available skills (id -> name).
     */
    public static final ImmutableList<String> ID_TO_NAME = ImmutableList
        .of("Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting",
            "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility",
            "Thieving", "Slayer", "Farming", "Runecrafting");

    /**
     * The ordered names of all available skills (name -> id).
     */
    public static final ImmutableMap<String, Integer> NAME_TO_ID;

    /**
     * The attack identifier.
     */
    public static final int ATTACK = 0;

    /**
     * The defence identifier.
     */
    public static final int DEFENCE = 1;

    /**
     * The strength identifier.
     */
    public static final int STRENGTH = 2;

    /**
     * The hitpoints identifier.
     */
    public static final int HITPOINTS = 3;

    /**
     * The ranged identifier.
     */
    public static final int RANGED = 4;

    /**
     * The prayer identifier.
     */
    public static final int PRAYER = 5;

    /**
     * The magic identifier.
     */
    public static final int MAGIC = 6;

    /**
     * The cooking identifier.
     */
    public static final int COOKING = 7;

    /**
     * The woodcutting identifier.
     */
    public static final int WOODCUTTING = 8;

    /**
     * The fletching identifier.
     */
    public static final int FLETCHING = 9;

    /**
     * The fishing identifier.
     */
    public static final int FISHING = 10;

    /**
     * The firemaking identifier.
     */
    public static final int FIREMAKING = 11;

    /**
     * The crafting identifier.
     */
    public static final int CRAFTING = 12;

    /**
     * The smithing identifier.
     */
    public static final int SMITHING = 13;

    /**
     * The mining identifier.
     */
    public static final int MINING = 14;

    /**
     * The herblore identifier.
     */
    public static final int HERBLORE = 15;

    /**
     * The agility identifier.
     */
    public static final int AGILITY = 16;

    /**
     * The thieving identifier.
     */
    public static final int THIEVING = 17;

    /**
     * The slayer identifier.
     */
    public static final int SLAYER = 18;

    /**
     * The farming identifier.
     */
    public static final int FARMING = 19;

    /**
     * The runecrafting identifier.
     */
    public static final int RUNECRAFTING = 20;

    /**
     * Retrieves the name of a skill by its identifier.
     */
    public static String getName(int id) {
        return ID_TO_NAME.get(id);
    }

    /**
     * Retrieves the identifier of a skill by its name.
     */
    public static int getId(String name) {
        return NAME_TO_ID.get(name);
    }

    /**
     * Determines if a skill is a factor in combat level calculations (attack, strength, defence, hitpoints, ranged,
     * prayer, magic).
     */
    public static boolean isCombatSkill(int id) {
        return id >= ATTACK && id <= MAGIC;
    }

    static { /* Initialize name -> identifier cache. */
        Map<String, Integer> skills = new HashMap<>();

        int index = 0;
        for (String name : ID_TO_NAME) {
            skills.put(name, index++);
        }

        NAME_TO_ID = ImmutableMap.copyOf(skills);
    }

    /**
     * The skill set.
     */
    private transient final SkillSet skills;

    /**
     * The skill identifier.
     */
    private transient final int id;

    /**
     * The static (experience based) skill level. Cached to avoid potentially expensive {@code
     * levelForExperience(int)} calls.
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
     * @param skills The skill set.
     */
    public Skill(int id, SkillSet skills) {
        this.id = id;
        this.skills = skills;

        if (id == HITPOINTS) {
            level = 10;
            experience = 1300;
        }
    }

    /**
     * Adds experience to this skill.
     */
    public void addExperience(double amount) {
        checkArgument(amount > 0, "amount <= 0");

        amount = amount * SkillSet.EXPERIENCE_MULTIPLIER;
        setExperience(experience + amount);
    }

    /**
     * Notifies plugins of any level or experience changes.
     */
    private void notifyListeners(double oldExperience, int oldStaticLevel, int oldLevel) {
        if (!skills.isFiringEvents()) {
            return;
        }

        Mob mob = skills.getMob();
        PluginManager plugins = mob.getPlugins();

        SkillChangeEvent evt = new SkillChangeEvent(mob, oldExperience, oldStaticLevel, oldLevel, id);
        plugins.post(evt);
    }

    /**
     * Retrieves the name of this skill.
     */
    public String name() {
        return getName(id);
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
        if (staticLevel == -1) { /* Compute and cache the value if needed. */
            staticLevel = SkillSet.levelForExperience((int) experience);
        }
        return staticLevel;
    }

    /**
     * @return The dynamic skill level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the dynamic skill level.
     */
    public void setLevel(int newLevel) {
        if (newLevel < 0) {
            newLevel = 0;
        }

        int oldStaticLevel = getStaticLevel();
        int oldLevel = level;

        level = newLevel;
        if (oldLevel == level) {
            return;
        }

        notifyListeners(experience, oldStaticLevel, oldLevel);
    }

    /**
     * Increases the dynamic skill level.
     */
    public void increaseLevel(int amount, int bound) {
        int newAmount = getLevel() + amount;
        if (newAmount > bound) {
            newAmount = bound;
        }
        setLevel(newAmount);
    }

    /**
     * Increases the dynamic skill level to a maximum of the static level.
     */
    public void increaseLevel(int amount) {
        increaseLevel(amount, getStaticLevel() + amount);
    }

    /**
     * Decreases the dynamic skill level.
     */
    public void decreaseLevel(int amount, int bound) {
        int newAmount = getLevel() - amount;
        if (newAmount < bound) {
            newAmount = bound;
        }
        setLevel(newAmount);
    }

    /**
     * Increases the dynamic skill level to a minimum of 0.
     */
    public void decreaseLevel(int amount) {
        decreaseLevel(amount, 0);
    }

    /**
     * @return The attained experience.
     */
    public double getExperience() {
        return experience;
    }

    /**
     * Sets the attained experience.
     */
    public void setExperience(double newExperience) {
        if (newExperience < 0) {
            newExperience = 0;
        } else if (newExperience > SkillSet.MAXIMUM_EXPERIENCE) {
            newExperience = SkillSet.MAXIMUM_EXPERIENCE;
        }
        int oldStaticLevel = getStaticLevel();
        double oldExperience = experience;
        experience = newExperience;
        staticLevel = -1;

        if (oldExperience == newExperience) {
            return;
        }
        notifyListeners(oldExperience, oldStaticLevel, level);
    }
}
