package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import io.luna.game.model.item.Item;
import io.luna.game.model.mobile.Skill;
import io.luna.game.model.mobile.SkillSet;

import java.util.HashMap;
import java.util.Map;

/**
 * A cached definition that describes a specific {@link Item} that can be equipped.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinition {

    /**
     * An inner-class that details the level needed in a specific skill in order to equip an item.
     */
    public static final class EquipmentRequirement {

        /**
         * The skill identifier.
         */
        private final int id;

        /**
         * The level needed.
         */
        private final int level;

        /**
         * Creates a new {@link EquipmentRequirement}.
         *
         * @param name The skill identifier.
         * @param level The level needed.
         */
        public EquipmentRequirement(String name, int level) {
            this.level = level;
            id = Skill.getId(name);
        }

        /**
         * Returns {@code true} if the argued {@link SkillSet} satisfies this requirement, {@code false} otherwise.
         */
        public boolean satisfies(SkillSet skills) {
            Skill skill = skills.getSkill(id);
            return skill.getStaticLevel() >= level;
        }

        /**
         * @return The skill identifier.
         */
        public int getId() {
            return id;
        }

        /**
         * @return The level needed.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * A {@link Map} of the cached {@code EquipmentDefinition}s.
     */
    public static final Map<Integer, EquipmentDefinition> DEFINITIONS = new HashMap<>();

    /**
     * The default {@link EquipmentDefinition} used when none in {@code DEFINITIONS} can be assigned to an {@code Item}.
     */
    public static final EquipmentDefinition DEFAULT = new EquipmentDefinition(-1, -1, false, false, false,
        new EquipmentRequirement[] {}, new int[] {});

    public static EquipmentDefinition getDefinition(int id) {
        return DEFINITIONS.getOrDefault(id, DEFAULT);
    }

    /**
     * The identifier for the item.
     */
    private final int id;

    /**
     * The index this item equips to.
     */
    private final int index;

    /**
     * If this item is a two-handed weapon.
     */
    private final boolean twoHanded;

    /**
     * If this item covers the entire torso, including arms.
     */
    private final boolean fullBody;

    /**
     * If this item covers the entire head.
     */
    private final boolean fullHelmet;

    /**
     * An {@link ImmutableList} containing all of the {@link EquipmentRequirement}s for this item.
     */
    private final ImmutableList<EquipmentRequirement> requirements;

    /**
     * An {@link ImmutableList} containing the bonuses for this item.
     */
    private final ImmutableList<Integer> bonuses;

    /**
     * Creates a new {@link EquipmentDefinition}.
     *
     * @param id The identifier for the item.
     * @param index The index this item equips to.
     * @param twoHanded If this item is a two-handed weapon.
     * @param fullBody If this item covers the entire torso, including arms.
     * @param fullHelmet If this item covers the entire head.
     * @param requirements An array containing all of the {@link EquipmentRequirement}s for this item.
     * @param bonuses An array containing the bonuses for this item.
     */
    public EquipmentDefinition(int id, int index, boolean twoHanded, boolean fullBody, boolean fullHelmet,
        EquipmentRequirement[] requirements, int[] bonuses) {
        this.id = id;
        this.index = index;
        this.twoHanded = twoHanded;
        this.fullBody = fullBody;
        this.fullHelmet = fullHelmet;
        this.requirements = ImmutableList.copyOf(requirements);
        this.bonuses = ImmutableList.copyOf(Ints.asList(bonuses));
    }

    /**
     * @return The identifier for the item.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The index this item equips to.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return {@code true} if this item is a two-handed weapon, {@code false} otherwise.
     */
    public boolean isTwoHanded() {
        return twoHanded;
    }

    /**
     * @return {@code true} if this item covers the entire torso including arms, {@code false otherwise}.
     */
    public boolean isFullBody() {
        return fullBody;
    }

    /**
     * @return {@code true} if this item covers the entire head, {@code false} otherwise.
     */
    public boolean isFullHelmet() {
        return fullHelmet;
    }

    /**
     * @return An {@link ImmutableList} containing all of the {@link EquipmentRequirement}s for this item.
     */
    public ImmutableList<EquipmentRequirement> getRequirements() {
        return requirements;
    }

    /**
     * @return An {@link ImmutableList} containing the bonuses for this item.
     */
    public ImmutableList<Integer> getBonuses() {
        return bonuses;
    }
}
