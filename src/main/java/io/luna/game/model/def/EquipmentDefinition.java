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
 * A cache/data-backed definition describing how an {@link ItemDefinition} behaves when equipped.
 * <p>
 * Equipment definitions are keyed by item id and provide the metadata needed by equipment systems, including:
 * <ul>
 *     <li>the equipment slot index (helmet/weapon/chest/etc.)</li>
 *     <li>special model coverage flags (full body, full helmet)</li>
 *     <li>two-handed flag (affects weapon/shield rules)</li>
 *     <li>skill requirements to equip the item</li>
 *     <li>equipment bonuses (attack/defence/strength/prayer, etc.)</li>
 * </ul>
 *
 * @author lare96
 */
public final class EquipmentDefinition implements Definition {

    /**
     * A single skill requirement for equipping an item.
     */
    public static final class Requirement {

        /**
         * The skill name as provided by the data source (e.g., "Attack").
         */
        private final String name;

        /**
         * The internal skill id resolved from {@link #name}.
         */
        private final int id;

        /**
         * The minimum level required.
         */
        private final int level;

        /**
         * Creates a new {@link Requirement} from JSON data.
         * <p>
         * Expected JSON fields:
         * <ul>
         *     <li>{@code name}: skill name</li>
         *     <li>{@code level}: required level</li>
         * </ul>
         *
         * @param jsonReq The requirement JSON object.
         */
        public Requirement(JsonObject jsonReq) {
            name = jsonReq.get("name").getAsString();
            id = Skill.getId(name);
            level = jsonReq.get("level").getAsInt();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("name", name)
                    .add("id", id)
                    .add("level", level)
                    .toString();
        }

        /**
         * Returns {@code true} if {@code mob} meets this requirement.
         *
         * @param mob The mob to check.
         * @return {@code true} if the skill level is at least {@link #level}.
         */
        public boolean meets(Mob mob) {
            return mob.skill(id).getLevel() >= level;
        }

        /**
         * Sends the player a message explaining that they do not meet this requirement.
         *
         * @param player The player to message.
         */
        public void sendFailureMessage(Player player) {
            Skill skill = player.skill(id);
            String article = addArticle(skill);
            player.sendMessage("You need " + article + " level of " + level + " to equip this.");
        }

        /**
         * Returns the skill name.
         *
         * @return The skill name.
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the internal skill id.
         *
         * @return The skill id.
         */
        public int getId() {
            return id;
        }

        /**
         * Returns the minimum required level.
         *
         * @return The required level.
         */
        public int getLevel() {
            return level;
        }
    }

    /**
     * Repository of all {@link EquipmentDefinition}s keyed by item id.
     */
    public static final DefinitionRepository<EquipmentDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The item id this equipment definition belongs to.
     */
    private final int id;

    /**
     * The equipment slot index this item is worn in.
     * <p>
     * This index is consumed by the equipment container (e.g., {@code Equipment.HEAD}, {@code Equipment.WEAPON}, etc.).
     */
    private final int index;

    /**
     * Whether the item is two-handed (equipping it may force the shield slot to be unequipped).
     */
    private final boolean twoHanded;

    /**
     * Whether the item covers the arms/torso (affects appearance model hiding rules).
     */
    private final boolean fullBody;

    /**
     * Whether the item covers the head/face (affects appearance model hiding rules).
     */
    private final boolean fullHelmet;

    /**
     * Skill requirements for equipping this item.
     */
    private final ImmutableList<Requirement> requirements;

    /**
     * Equipment bonuses array (defensively copied).
     */
    private final int[] bonuses;

    /**
     * Creates a new {@link EquipmentDefinition}.
     *
     * @param id The item id.
     * @param index The equipment slot index.
     * @param twoHanded Whether the item is two-handed.
     * @param fullBody Whether the item covers the arms/torso.
     * @param fullHelmet Whether the item covers the head/face.
     * @param requirements Equipment requirements (copied into an immutable list).
     * @param bonuses Equipment bonuses (defensively copied).
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
     * Returns the first requirement the player does not meet.
     *
     * @param player The player to check.
     * @return The first failed requirement, or {@link Optional#empty()} if all requirements are met.
     */
    public Optional<Requirement> getFailedRequirement(Player player) {
        return requirements.stream()
                .filter(requirement -> !requirement.meets(player))
                .findFirst();
    }

    /**
     * Returns {@code true} if the player meets all requirements to equip this item.
     *
     * @param player The player to check.
     * @return {@code true} if all requirements are met.
     */
    public boolean meetsAllRequirements(Player player) {
        return getFailedRequirement(player).isEmpty();
    }

    /**
     * Returns the item id this equipment definition belongs to.
     *
     * @return The id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns the equipment slot index this item is worn in.
     *
     * @return The slot index.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Returns whether this item is two-handed.
     *
     * @return {@code true} if two-handed.
     */
    public boolean isTwoHanded() {
        return twoHanded;
    }

    /**
     * Returns whether this item covers the arms/torso.
     *
     * @return {@code true} if full body.
     */
    public boolean isFullBody() {
        return fullBody;
    }

    /**
     * Returns whether this item covers the head/face.
     *
     * @return {@code true} if full helmet.
     */
    public boolean isFullHelmet() {
        return fullHelmet;
    }

    /**
     * Returns the list of skill requirements.
     *
     * @return The requirements list (immutable).
     */
    public ImmutableList<Requirement> getRequirements() {
        return requirements;
    }

    /**
     * Returns the bonus at the given bonus index.
     *
     * <p>
     * Note: this will throw {@link ArrayIndexOutOfBoundsException} if an invalid index is supplied.
     *
     * @param id The bonus index.
     * @return The bonus value.
     */
    public int getBonus(int id) {
        return bonuses[id];
    }
}
