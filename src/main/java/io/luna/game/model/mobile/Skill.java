package io.luna.game.model.mobile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.model.EntityType;
import io.luna.game.plugin.PluginManager;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A representation of a single skill within a {@link SkillSet}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class Skill {

    /**
     * The ordered names of all available skills (id -> name).
     */
    public static final ImmutableList<String> ID_TO_NAME = ImmutableList
        .of("Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting", "Fletching",
            "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer",
            "Farming", "Runecrafting");

    /**
     * The ordered names of all available skills (name -> id).
     */
    public static final ImmutableMap<String, Integer> NAME_TO_ID;

    /**
     * The attack skill identifier in the skill set.
     */
    public static final int ATTACK = 0;

    /**
     * The defence skill identifier in the skill set.
     */
    public static final int DEFENCE = 1;

    /**
     * The strength skill identifier in the skill set.
     */
    public static final int STRENGTH = 2;

    /**
     * The hitpoints skill identifier in the skill set.
     */
    public static final int HITPOINTS = 3;

    /**
     * The ranged skill identifier in the skill set.
     */
    public static final int RANGED = 4;

    /**
     * The prayer skill identifier in the skill set.
     */
    public static final int PRAYER = 5;

    /**
     * The magic skill identifier in the skill set.
     */
    public static final int MAGIC = 6;

    /**
     * The cooking skill identifier in the skill set.
     */
    public static final int COOKING = 7;

    /**
     * The woodcutting skill identifier in the skill set.
     */
    public static final int WOODCUTTING = 8;

    /**
     * The fletching skill identifier in the skill set.
     */
    public static final int FLETCHING = 9;

    /**
     * The fishing skill identifier in the skill set.
     */
    public static final int FISHING = 10;

    /**
     * The firemaking skill identifier in the skill set.
     */
    public static final int FIREMAKING = 11;

    /**
     * The crafting skill identifier in the skill set.
     */
    public static final int CRAFTING = 12;

    /**
     * The smithing skill identifier in the skill set.
     */
    public static final int SMITHING = 13;

    /**
     * The mining skill identifier in the skill set.
     */
    public static final int MINING = 14;

    /**
     * The herblore skill identifier in the skill set.
     */
    public static final int HERBLORE = 15;

    /**
     * The agility skill identifier in the skill set.
     */
    public static final int AGILITY = 16;

    /**
     * The thieving skill identifier in the skill set.
     */
    public static final int THIEVING = 17;

    /**
     * The slayer skill identifier in the skill set.
     */
    public static final int SLAYER = 18;

    /**
     * The farming skill identifier in the skill set.
     */
    public static final int FARMING = 19;

    /**
     * The runecrafting skill identifier in the skill set.
     */
    public static final int RUNECRAFTING = 20;

    /**
     * Retrieve the name of a skill by {@code id}.
     *
     * @param id The id to retrieve the name of.
     * @return The name of the skill.
     */
    public static String getName(int id) {
        return ID_TO_NAME.get(id);
    }

    /**
     * Retrieve the id of a skill by {@code name}.
     *
     * @param name The name to retrieve the id of.
     * @return The id of the skill.
     */
    public static int getId(String name) {
        return NAME_TO_ID.get(name);
    }

    /**
     * Determines if the skill specified by {@code id} is a combat skill.
     *
     * @param id The id to determine a combat skill.
     * @return {@code true} if the skill is a combat skill, {@code false} otherwise.
     */
    public static boolean isCombatSkill(int id) {
        return id >= ATTACK && id <= MAGIC;
    }

    static {
        Map<String, Integer> skills = new HashMap<>();

        int index = 0;
        for (String name : ID_TO_NAME) {
            skills.put(name, index++);
        }

        NAME_TO_ID = ImmutableMap.copyOf(skills);
    }

    /**
     * The {@link SkillSet} that this skill is a part of.
     */
    private transient final SkillSet skills;

    /**
     * The identifier that determines which skill this instance represents.
     */
    private transient final int id;

    /**
     * The cached static level of this skill. Used to avoid repeated calculations of the experience-based level.
     */
    private transient int staticLevel = -1;

    /**
     * The skill level. It can be increased or decreased with various consumables, spells, and special attacks from players
     * and monsters.
     */
    private int level = 1;

    /**
     * The experience attained for this skill. Can be used to determine the static skill level.
     */
    private double experience;

    /**
     * Creates a new {@link Skill}.
     *
     * @param id The identifier that determines which skill this instance represents.
     * @param skills The {@link SkillSet} that this skill is a part of.
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
     * Adds {@code amount} experience to this skill.
     *
     * @param amount The amount of experience to add.
     */
    public void addExperience(double amount) {
        checkArgument(amount > 0, "amount <= 0");

        amount = amount * SkillSet.EXPERIENCE_MULTIPLIER;
        setExperience(experience + amount);
    }

    /**
     * Notifies plugins of any level or experience changes.
     *
     * @param oldExperience The old experience value.
     * @param oldStaticLevel The old static level value.
     * @param oldLevel The old dynamic level value.
     */
    private void notifyListeners(double oldExperience, int oldStaticLevel, int oldLevel) {
        if (!skills.isFiringEvents()) {
            return;
        }

        MobileEntity mob = skills.getMob();
        PluginManager plugins = mob.getPlugins();

        SkillChangeEvent evt = new SkillChangeEvent(mob, oldExperience, oldStaticLevel, oldLevel, id);
        if (mob.type() == EntityType.PLAYER) {
            plugins.post(evt, (Player) mob);
        } else {
            plugins.post(evt);
        }
    }

    /**
     * @return The identifier that determines which skill this instance represents.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The static or, "experience-based" skill level.
     */
    public int getStaticLevel() {
        if (staticLevel == -1) {
            staticLevel = SkillSet.levelForExperience((int) experience);
        }
        return staticLevel;
    }

    /**
     * @return The skill level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the skill level.
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
     * @return The experience attained for this skill.
     */
    public double getExperience() {
        return experience;
    }

    /**
     * Sets the experience attained for this skill.
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
