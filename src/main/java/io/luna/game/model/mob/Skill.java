package io.luna.game.model.mob;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import io.luna.Luna;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.model.mob.bot.Bot;
import io.luna.game.plugin.PluginManager;

import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a single skill within a {@link SkillSet}.
 * <p>
 * Each skill tracks three related values:
 * <ul>
 *     <li>Its immutable skill {@link #id}</li>
 *     <li>Its current dynamic {@link #level}, which may be temporarily boosted or drained</li>
 *     <li>Its total {@link #experience}, from which the static level is derived</li>
 * </ul>
 *
 * @author lare96
 */
public final class Skill {

    /**
     * Ordered, immutable list of all skill names. The index into this list is the skill identifier.
     */
    public static final ImmutableList<String> NAMES = ImmutableList.of(
            "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic",
            "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
            "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecrafting"
    );

    /**
     * The range of skill identifiers considered in combat level calculations.
     */
    public static final Range<Integer> COMBAT_IDS = Range.closed(0, 6);

    /**
     * Maps skill name -> skill identifier.
     * <p>
     * Built from {@link #NAMES} in the static initializer.
     */
    public static final ImmutableMap<String, Integer> NAME_TO_ID;

    /**
     * Immutable list of all skill identifiers.
     * <p>
     * This is effectively {@code 0..NAMES.size()-1} but derived from {@link #NAME_TO_ID} for convenience.
     */
    public static final ImmutableList<Integer> IDS;

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
     * Retrieves the skill name for {@code id}.
     *
     * @param id The skill identifier (0-based).
     * @return The skill name.
     * @throws IndexOutOfBoundsException if {@code id} is not a valid skill identifier.
     */
    public static String getName(int id) {
        return NAMES.get(id);
    }

    /**
     * Retrieves the skill identifier for {@code name}, or {@code -1} if none were found.
     * <p>
     * Matching is exact and case-sensitive.
     *
     * @param name The skill name (must match an entry in {@link #NAMES}).
     * @return The skill identifier, {@code -1} if none were found.
     * @throws NullPointerException if {@code name} is {@code null}.
     */
    public static int getId(String name) {
        Integer id = NAME_TO_ID.get(name);
        return id == null ? -1 : id;
    }

    /**
     * Determines whether {@code id} is a combat skill.
     *
     * @param id The skill identifier.
     * @return {@code true} if {@code id} is in {@link #COMBAT_IDS}.
     */
    public static boolean isCombatSkill(int id) {
        return COMBAT_IDS.contains(id);
    }

    static {
        // Build and set [name -> identifier] cache.
        NAME_TO_ID = IntStream.range(0, NAMES.size()).boxed()
                .collect(ImmutableMap.toImmutableMap(NAMES::get, Function.identity()));
        IDS = ImmutableList.copyOf(NAME_TO_ID.values());
    }

    /**
     * The owning skill set.
     */
    private transient final SkillSet set;

    /**
     * The skill identifier (0-based index into {@link #NAMES}).
     */
    private transient final int id;

    /**
     * Cached static (experience-based) level.
     * <p>
     * This is computed lazily from {@link #experience} and cached to avoid repeated {@link SkillSet#levelForExperience(int)}
     * calls. It is invalidated by setting {@code staticLevel = -1} whenever experience changes.
     */
    private transient int staticLevel = -1;

    /**
     * The dynamic (temporarily modified) level.
     * <p>
     * This may be higher or lower than the static level due to buffs/drains. Restoration may move this back toward
     * {@link #getStaticLevel()} over time.
     */
    private int level = 1;

    /**
     * Total accumulated experience for this skill.
     * <p>
     * Bounded to {@code [0, SkillSet.MAXIMUM_EXPERIENCE]} by {@link #setExperience(double)}.
     */
    private double experience;

    /**
     * Creates a new {@link Skill}.
     * <p>
     * Hitpoints starts at level 10 with 1300 experience (classic RuneScape default), while other skills start at
     * level 1 with 0 experience.
     *
     * @param id The skill identifier.
     * @param set The owning skill set.
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
     * Adds experience to this skill.
     * <p>
     * For non-bot mobs, experience is multiplied by the configured game experience multiplier. Bots receive experience
     * at a fixed multiplier of {@code 1.0} (no bonus).
     *
     * @param amount The raw amount of experience to add.
     * @throws IllegalArgumentException if {@code amount <= 0}.
     */
    public void addExperience(double amount) {
        checkArgument(amount > 0, "amount <= 0");
        double multiplier = set.getMob() instanceof Bot ? 1.0 : Luna.settings().game().experienceMultiplier();
        setExperience(experience + (amount * multiplier));
    }

    /**
     * Posts a {@link SkillChangeEvent} to plugins if event firing is enabled.
     * <p>
     * The event includes the previous values so listeners can compute deltas or react to level-ups.
     *
     * @param oldExperience The previous experience value.
     * @param oldStaticLevel The previous static level.
     * @param oldLevel The previous dynamic level.
     */
    private void notifyListeners(double oldExperience, int oldStaticLevel, int oldLevel) {
        if (set.isFiringEvents()) {
            Mob mob = set.getMob();
            PluginManager plugins = mob.getPlugins();
            plugins.post(new SkillChangeEvent(mob, oldExperience, oldStaticLevel, oldLevel, id));
        }
    }

    /**
     * @return The skill name (from {@link #NAMES}).
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
     * Returns the static (experience-based) skill level.
     * <p>
     * This is computed lazily and cached. The cache is invalidated whenever experience changes.
     *
     * @return The experience-based level.
     */
    public int getStaticLevel() {
        if (staticLevel == -1) {
            // The value hasn't been computed yet, do it now.
            staticLevel = SkillSet.levelForExperience((int) experience);
        }
        return staticLevel;
    }

    /**
     * Sets the static (experience-based) level by converting the desired level into experience.
     *
     * @param level The target static level.
     */
    public void setStaticLevel(int level) {
        setExperience(SkillSet.experienceForLevel(level));
        staticLevel = level;
    }

    /**
     * @return The current dynamic level.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the dynamic (temporarily modified) skill level.
     * <p>
     * The level is clamped to a minimum of {@code 0}. If the value does not change, no events are fired and restoration
     * is not scheduled.
     *
     * @param newLevel The new dynamic level.
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
        set.restoreSkills(this);
        notifyListeners(experience, getStaticLevel(), oldLevel);
    }

    /**
     * Increases the dynamic skill level by {@code amount}.
     * <p>
     * If {@code exceedStaticLevel} is {@code false}, the result is capped at {@link #getStaticLevel()}.
     * <p>
     * If {@code exceedStaticLevel} is {@code true}, the result is capped at {@code getStaticLevel() + amount}.
     *
     * @param amount The amount to increase by.
     * @param exceedStaticLevel If {@code true}, allow the boost above the static level (up to {@code static + amount});
     * otherwise cap at the static level.
     */
    public void addLevels(int amount, boolean exceedStaticLevel) {
        int bound = exceedStaticLevel ? getStaticLevel() + amount : getStaticLevel();
        int newAmount = getLevel() + amount;

        newAmount = Math.min(newAmount, bound);
        setLevel(newAmount);
    }

    /**
     * Decreases the dynamic skill level by {@code amount}.
     * <p>
     * The result is clamped to a minimum of {@code 0}.
     *
     * @param amount The amount to remove.
     */
    public void removeLevels(int amount) {
        int newAmount = getLevel() - amount;
        newAmount = Math.max(newAmount, 0);
        setLevel(newAmount);
    }

    /**
     * Returns total accumulated experience.
     *
     * @return The experience value.
     */
    public double getExperience() {
        return experience;
    }

    /**
     * Sets total accumulated experience for this skill.
     * <p>
     * The value is clamped into {@code [0, SkillSet.MAXIMUM_EXPERIENCE]}. If the value does not change, nothing happens.
     * <p>
     * If the value changes, the cached static level is invalidated and listeners may be notified.
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
