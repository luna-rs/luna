package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.google.gson.JsonObject;
import io.luna.game.model.def.DefinitionRepository.MapDefinitionRepository;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.Skill;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Predicates.not;
import static io.luna.util.StringUtils.addArticle;

/**
 * A model describing an equipment item definition.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinition implements Definition {

    /**
     * A requirement for equipping an item.
     */
    public static final class Requirement {

        /**
         * The requirement skill identifier.
         */
        private final int id;

        /**
         * The requirement level.
         */
        private final int level;

        /**
         * Creates a new {@link Requirement}.
         *
         * @param object The JSON object structure.
         */
        public Requirement(JsonObject object) {
            id = Skill.getId(object.get("name").getAsString());
            level = object.get("level").getAsInt();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this).
                    add("name", Skill.getName(id)).
                    add("id", id).
                    add("level", level).toString();
        }

        /**
         * Determines if {@code player} meets this requirement.
         *
         * @param player The player to check.
         * @return {@code true} if {@code player} meets this requirement.
         */
        public boolean meets(Player player) {
            return player.skill(id).getLevel() >= level;
        }

        /**
         * Sends the player a message stating they don't meet this requirement.
         *
         * @param player The player to send the message to.
         */
        public void sendFailureMessage(Player player) {
            String article = addArticle(player.skill(id));
            player.sendMessage("You need " + article + " level of " + level + " to equip this.");
        }

        /**
         * @return The requirement skill identifier.
         */
        public int getId() {
            return id;
        }

        /**
         * @return The requirement level.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * A map of equipment definitions.
     */
    public static final DefinitionRepository<EquipmentDefinition> DEFINITIONS = new MapDefinitionRepository<>();

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
     * A set of equipment requirements.
     */
    private final ImmutableSet<Requirement> requirements;

    /**
     * A list of equipment bonuses.
     */
    private final ImmutableList<Integer> bonuses;

    /**
     * Creates a new {@link EquipmentDefinition}.
     *
     * @param id The identifier.
     * @param index The equipment index.
     * @param twoHanded If this item is two-handed.
     * @param fullBody If this item covers the arms and torso.
     * @param fullHelmet If this item covers the head and face.
     * @param requirements A set of equipment requirements.
     * @param bonuses A list of equipment bonuses.
     */
    public EquipmentDefinition(int id, int index, boolean twoHanded, boolean fullBody, boolean fullHelmet,
                               Set<Requirement> requirements, int[] bonuses) {
        this.id = id;
        this.index = index;
        this.twoHanded = twoHanded;
        this.fullBody = fullBody;
        this.fullHelmet = fullHelmet;
        this.requirements = ImmutableSet.copyOf(requirements);
        this.bonuses = ImmutableList.copyOf(Ints.asList(bonuses));
    }

    /**
     * Returns the first failed requirement found.
     *
     * @param player The player to check for.
     * @return The first requirement that was not met, or an empty optional if {@code player}
     * meets all requirements.
     */
    public Optional<Requirement> getFailedRequirement(Player player) {
        return requirements.stream().filter(not(requirement -> requirement.meets(player))).findFirst();
    }

    /**
     * Determines if the player meets all equipment requirements.
     *
     * @param player The player to determine for.
     * @return {@code true} if {@code player} meets all requirements.
     */
    public boolean meetsAllRequirements(Player player) {
        return !getFailedRequirement(player).isPresent();
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
     * @return A set of the requirements.
     */
    public ImmutableSet<Requirement> getRequirements() {
        return requirements;
    }

    /**
     * @return A list of equipment bonuses.
     */
    public ImmutableList<Integer> getBonuses() {
        return bonuses;
    }
}
