package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;

import java.util.Arrays;
import java.util.Optional;

import static io.luna.util.StringUtils.addArticle;

/**
 * A model describing an equipment item definition.
 *
 * @author lare96
 */
public final class EquipmentDefinition implements Definition {

    /**
     * A requirement for equipping an item.
     */
    public static final class Requirement {

        /**
         * The requirement skill name.
         */
        private final String name;

        /**
         * The requirement skill identifier.
         */
        private final int id;

        /**
         * The requirement skill level.
         */
        private final int level;

        /**
         * Creates a new {@link Requirement}.
         *
         * @param jsonReq The requirement data, in JSON.
         */
        public Requirement(JsonObject jsonReq) {
            name = jsonReq.get("name").getAsString();
            id = Skill.getId(name);
            level = jsonReq.get("level").getAsInt();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).
                    add("name", name).
                    add("id", id).
                    add("level", level).toString();
        }

        /**
         * Determines if the specified {@link Mob} meets this requirement.
         *
         * @param mob The mob to check.
         * @return {@code true} if {@code mob} meets this requirement, otherwise {@code false}.
         */
        public boolean meets(Mob mob) {
            return mob.skill(id).getLevel() >= level;
        }

        /**
         * Sends the player a message stating they don't meet this requirement.
         *
         * @param player The player to send the message to.
         */
        public void sendFailureMessage(Player player) {
            Skill skill = player.skill(id);
            String article = addArticle(skill);
            player.sendMessage("You need " + article + " level of " + level + " to equip this.");
        }

        /**
         * @return The requirement skill name.
         */
        public String getName() {
            return name;
        }

        /**
         * Retrieves and returns the skill identifier.
         *
         * @return The requirement skill identifier.
         */
        public int getId() {
            return id;
        }

        /**
         * @return The requirement skill level.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * A map of equipment definitions.
     */
    public static final DefinitionRepository<EquipmentDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The identifier.
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
     * An immutable list of equipment requirements.
     */
    private final ImmutableList<Requirement> requirements;

    /**
     * A list of equipment bonuses.
     */
    private final int[] bonuses;

    /**
     * Creates a new {@link EquipmentDefinition}.
     *
     * @param id The identifier.
     * @param index The equipment index.
     * @param twoHanded If this item is two-handed.
     * @param fullBody If this item covers the arms and torso.
     * @param fullHelmet If this item covers the head and face.
     * @param requirements A list of equipment requirements.
     * @param bonuses A list of equipment bonuses.
     */
    public EquipmentDefinition(int id, int index, boolean twoHanded, boolean fullBody, boolean fullHelmet,
                              Requirement[] requirements, int[] bonuses) {
        this.id = id;
        this.index = index;
        this.twoHanded = twoHanded;
        this.fullBody = fullBody;
        this.fullHelmet = fullHelmet;
        this.requirements = ImmutableList.copyOf(requirements);
        this.bonuses = Arrays.copyOf(bonuses, bonuses.length);
    }

    /**
     * Returns the first failed requirement found.
     *
     * @param player The player to check for.
     * @return The first requirement that was not met, or an empty optional if {@code player}
     * meets all requirements.
     */
    public Optional<Requirement> getFailedRequirement(Player player) {
        return requirements.stream().filter(requirement -> !requirement.meets(player)).findFirst();
    }

    /**
     * Determines if the player meets all equipment requirements.
     *
     * @param player The player to determine for.
     * @return {@code true} if {@code player} meets all requirements.
     */
    public boolean meetsAllRequirements(Player player) {
        return getFailedRequirement(player).isEmpty();
    }

    @Override
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
     * @return An immutable list of the requirements.
     */
    public ImmutableList<Requirement> getRequirements() {
        return requirements;
    }

    /**
     * Gets the bonus at the specified identifier.
     *
     * @param id The bonus identifier.
     * @return The bonus at the specified identifier as an {@code int}.
     */
    public int getBonus(int id) {
        return bonuses[id];
    }
}
