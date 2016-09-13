package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import io.luna.game.model.mobile.Skill;
import io.luna.util.parser.impl.EquipmentDefinitionParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * A definition model describing an item that can be equipped.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinition {

    /**
     * A model detailing a skill equipment requirement.
     */
    public static final class EquipmentRequirement {

        /**
         * The skill.
         */
        private final int id;

        /**
         * The level.
         */
        private final int level;

        /**
         * Creates a new {@link EquipmentRequirement}.
         *
         * @param name The skill.
         * @param level The level.
         */
        public EquipmentRequirement(String name, int level) {
            this.level = level;
            id = Skill.getId(name);
        }

        /**
         * @return The skill.
         */
        public int getId() {
            return id;
        }

        /**
         * @return The level.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * A map of equipment definitions.
     */
    public static final ImmutableMap<Integer, EquipmentDefinition> DEFINITIONS;

    /**
     * Retrieves the definition for {@code id}.
     */
    public static EquipmentDefinition get(int id) {
        EquipmentDefinition def = DEFINITIONS.get(id);
        if (def == null) {
            throw new NoSuchElementException("No definition for " + id);
        }
        return def;
    }

    /**
     * Returns an iterable containing all definitions.
     */
    public static Iterable<EquipmentDefinition> all() {
        return DEFINITIONS.values();
    }

    static { /* Populate the immutable map with definitions. */
        Map<Integer, EquipmentDefinition> definitions = new LinkedHashMap<>();

        EquipmentDefinitionParser parser = new EquipmentDefinitionParser(definitions);
        parser.run();

        DEFINITIONS = ImmutableMap.copyOf(definitions);
    }

    /**
     * The item identifier.
     */
    private final int id;

    /**
     * The equipment index.
     */
    private final int index;

    /**
     * If this item is two-handed.
     */
    private final boolean twoHanded;

    /**
     * If this item covers the arms and torso.
     */
    private final boolean fullBody;

    /**
     * If this item covers the head and face.
     */
    private final boolean fullHelmet;

    /**
     * A list of equipment requirements.
     */
    private final ImmutableList<EquipmentRequirement> requirements;

    /**
     * A list of equipment bonuses.
     */
    private final ImmutableList<Integer> bonuses;

    /**
     * Creates a new {@link EquipmentDefinition}.
     *
     * @param id The item identifier.
     * @param index The equipment index.
     * @param twoHanded If this item is two-handed.
     * @param fullBody If this item covers the arms and torso.
     * @param fullHelmet If this item covers the head and face.
     * @param requirements A list of equipment requirements.
     * @param bonuses A list of equipment bonuses.
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
     * @return The item identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The equipment index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return If this item is two-handed.
     */
    public boolean isTwoHanded() {
        return twoHanded;
    }

    /**
     * @return If this item covers the arms and torso.
     */
    public boolean isFullBody() {
        return fullBody;
    }

    /**
     * @return If this item covers the head and face.
     */
    public boolean isFullHelmet() {
        return fullHelmet;
    }

    /**
     * @return A list of equipment requirements.
     */
    public ImmutableList<EquipmentRequirement> getRequirements() {
        return requirements;
    }

    /**
     * @return A list of equipment bonuses.
     */
    public ImmutableList<Integer> getBonuses() {
        return bonuses;
    }
}
