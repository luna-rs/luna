package io.luna.game.model.mob;

import api.bot.action.BotCombatActionHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import io.luna.Luna;
import io.luna.game.event.impl.SkillChangeEvent;
import io.luna.game.model.mob.bot.Bot;

import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Represents a single skill within a {@link SkillSet}.
 * <p>
 * A skill stores both permanent progression and temporary state:
 * <ul>
 *     <li>The immutable {@link #id} that identifies the skill.</li>
 *     <li>The current dynamic {@link #level}, which may be boosted or drained.</li>
 *     <li>The total accumulated {@link #experience}, from which the static level is derived.</li>
 * </ul>
 * Player static levels are calculated lazily from experience and cached in {@link #staticLevel}. NPC static levels
 * are assigned from their combat definitions and remain independent of temporary boosts, drains, and damage.
 *
 * @author lare96
 */
public final class Skill {

    /**
     * Ordered, immutable list of all skill names.
     * <p>
     * The index of each entry is that skill's identifier.
     */
    public static final ImmutableList<String> NAMES = ImmutableList.of(
            "Attack", "Defence", "Strength", "Hitpoints", "Ranged", "Prayer", "Magic",
            "Cooking", "Woodcutting", "Fletching", "Fishing", "Firemaking", "Crafting", "Smithing",
            "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming", "Runecrafting"
    );

    /**
     * Inclusive range of skill identifiers used in combat level calculations.
     */
    public static final Range<Integer> COMBAT_IDS = Range.closed(0, 6);

    /**
     * Immutable mapping of skill name to skill identifier.
     * <p>
     * This is derived from {@link #NAMES} during class initialization.
     */
    public static final ImmutableMap<String, Integer> NAME_TO_ID;

    /**
     * Immutable list of all skill identifiers.
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
     * Retrieves the display name for a skill identifier.
     *
     * @param id The skill identifier.
     * @return The skill name at {@code id}.
     * @throws IndexOutOfBoundsException If {@code id} is not a valid skill identifier.
     */
    public static String getName(int id) {
        return NAMES.get(id);
    }

    /**
     * Retrieves the identifier for a skill name.
     * <p>
     * Matching is exact and case-sensitive.
     *
     * @param name The skill name.
     * @return The matching identifier, or {@code -1} if no identifier exists for {@code name}.
     */
    public static int getId(String name) {
        Integer id = NAME_TO_ID.get(name);
        return id == null ? -1 : id;
    }

    /**
     * Determines if a skill identifier is considered a combat skill.
     *
     * @param id The skill identifier.
     * @return {@code true} if {@code id} is within {@link #COMBAT_IDS}.
     */
    public static boolean isCombatSkill(int id) {
        return COMBAT_IDS.contains(id);
    }

    static {
        NAME_TO_ID = IntStream.range(0, NAMES.size()).boxed()
                .collect(ImmutableMap.toImmutableMap(NAMES::get, Function.identity()));
        IDS = ImmutableList.copyOf(NAME_TO_ID.values());
    }

    /**
     * The owning skill set.
     */
    private transient final SkillSet set;

    /**
     * The skill identifier.
     */
    private transient final int id;

    /**
     * Cached static level derived from {@link #experience}.
     * <p>
     * A value of {@code -1} indicates that the cached level is invalid and must be recomputed.
     */
    private transient int staticLevel = -1;

    /**
     * The current dynamic level.
     * <p>
     * This may temporarily differ from the static level due to boosts, drains, or restoration.
     */
    private int level = 1;

    /**
     * The total accumulated experience for this skill.
     */
    private double experience;

    /**
     * Creates a new skill instance.
     * <p>
     * Hitpoints starts at level 10 with 1300 experience. All other skills start at level 1 with 0 experience.
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
     * Non-positive amounts are ignored.
     * <p>
     * For non-bot mobs, the raw amount is multiplied by the configured game experience multiplier.
     * Bots always receive experience using a multiplier of {@code 1.0}, with individual awards limited by
     * {@link BotCombatActionHandler#limitExperience(Bot, Skill, double)} to preserve their account builds.
     *
     * @param amount The raw amount of experience to add.
     */
    public void addExperience(double amount) {
        if (amount > 0) {
            double awarded = set.getMob() instanceof Bot bot ?
                    BotCombatActionHandler.limitExperience(bot, this, amount) :
                    amount * Luna.settings().game().experienceMultiplier();
            setExperience(experience + awarded);
        }
    }

    /**
     * Fires a {@link SkillChangeEvent} if this skill set is currently configured to do so.
     * <p>
     * The previous experience, static level, and dynamic level are included so listeners can compute deltas.
     *
     * @param oldExperience The previous experience value.
     * @param oldStaticLevel The previous static level.
     * @param oldLevel The previous dynamic level.
     */
    private void notifyListeners(double oldExperience, int oldStaticLevel, int oldLevel) {
        Mob mob = set.getMob();
        if (set.isFiringEvents() && mob instanceof Player) {
            Player player = (Player) mob;
            player.getPlugins().post(new SkillChangeEvent(player, oldExperience, oldStaticLevel, oldLevel, id));
        }
    }

    /**
     * @return The skill name from {@link #NAMES}.
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
     * Returns the static level derived from player experience or assigned from an NPC combat definition.
     * <p>
     * Experience-based values are computed lazily and cached until experience changes. NPC definitions may
     * explicitly assign zero, independently of the minimum experience-based player level of one.
     *
     * @return The static level.
     */
    public int getStaticLevel() {
        if (staticLevel == -1) {
            staticLevel = SkillSet.levelForExperience((int) experience);
        }
        return staticLevel;
    }

    /**
     * Sets the static level by converting the level into its corresponding experience value for players.
     * NPC base levels are stored directly, allowing combat definitions above the player level cap without
     * manufacturing experience. All NPC base skills may be zero; negative values are clamped to zero.
     *
     * @param level The new static level.
     */
    public void setStaticLevel(int level) {
        if (set.getMob() instanceof Npc) {
            staticLevel = Math.max(0, level);
            return;
        }
        setExperience(SkillSet.experienceForLevel(level));
        staticLevel = level;
    }

    /**
     * Returns the current dynamic level.
     *
     * @return The current level, including temporary boosts or drains.
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the dynamic level for this skill.
     * <p>
     * All skills may be drained to zero. Negative values are clamped to zero; the static level is unchanged so
     * restoration can recover a fully drained skill normally.
     * <p>
     * If the level does not change, no listeners are notified and no restoration is scheduled.
     * <p>
     * Temporary changes never alter the static level, including for NPCs.
     * <p>
     * Hitpoints that reach full health clear the mob's damage stack.
     *
     * @param newLevel The new dynamic level.
     */
    public void setLevel(int newLevel) {
        newLevel = Math.max(0, newLevel);

        int oldLevel = level;

        level = newLevel;
        if (oldLevel == level) {
            return;
        }
        Mob mob = set.getMob();
        if (id == HITPOINTS && level >= mob.getTotalHealth()) {
            // Back to full health, so earlier damage no longer counts toward who kills this mob.
            mob.getCombat().getDamageStack().clear();
        }
        set.restoreSkills(this);
        notifyListeners(experience, getStaticLevel(), oldLevel);
    }

    /**
     * Adjusts the current dynamic level by a signed amount.
     * <p>
     * Positive values restore a drained level up to its static level and leave existing boosts unchanged.
     * Negative values subtract only the requested amount, retaining any remaining boost above the static level.
     * All skills have a minimum dynamic level of zero.
     * <p>
     * Zero is a no-op. This method cannot create or increase a boost. Intermediate arithmetic uses a long so
     * extreme adjustments cannot wrap around and reverse the intended change.
     *
     * @param amount The signed adjustment to apply.
     */
    public void adjustLevel(int amount) {
        if (amount == 0 || (amount > 0 && level >= getStaticLevel())) {
            return;
        }
        long adjusted = (long) level + amount;
        if (amount > 0) {
            adjusted = Math.min(adjusted, getStaticLevel());
        }
        setLevel((int) Math.max(0L, adjusted));
    }

    /**
     * Adds a temporary boost to the current dynamic level, capped at the static level plus the boost amount.
     * <p>
     * Drained skills gain only the supplied amount rather than jumping directly to the boosted cap. Existing
     * higher boosts are preserved. Player boosts cannot raise a level above 255, matching the client skill field.
     * Non-positive amounts do nothing, and intermediate arithmetic uses longs to avoid overflow.
     *
     * @param amount The amount to add and the maximum permitted boost above the static level.
     * @return {@code true} if the dynamic level increased, otherwise {@code false}.
     */
    public boolean boost(int amount) {
        if (amount <= 0) {
            return false;
        }
        long boosted = Math.min((long) level + amount, (long) getStaticLevel() + amount);
        int maximum = set.getMob() instanceof Player ? 255 : Integer.MAX_VALUE;
        int newLevel = (int) Math.min(boosted, maximum);
        if (level >= newLevel) {
            return false;
        }
        setLevel(newLevel);
        return true;
    }

    /**
     * Applies a temporary drain relative to the static level.
     * <p>
     * The target level is {@code getStaticLevel() - amount}, clamped to zero. If this skill is already at or below
     * that value, nothing changes and {@code false} is returned. In particular, draining an already-zero skill
     * does not report a change. Intermediate arithmetic uses a long to avoid overflow.
     *
     * @param amount The amount to drain below the static level.
     * @return {@code true} if the drain was applied, otherwise {@code false}.
     */
    public boolean weaken(int amount) {
        long newLevel = Math.max(0L, (long) getStaticLevel() - amount);
        if (level <= newLevel) {
            return false;
        }
        setLevel((int) newLevel);
        return true;
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
     * The value is clamped into the range {@code [0, SkillSet.MAXIMUM_EXPERIENCE]}.
     * If the experience does not change, this method returns immediately.
     * <p>
     * When the value changes, the cached static level is invalidated and listeners are notified.
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

    /**
     * @return {@code true} if the dynamic level is greater than the static level.
     */
    public boolean isBoosted() {
        return level > getStaticLevel();
    }
}
