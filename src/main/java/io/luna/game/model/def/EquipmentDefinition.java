package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;
import io.luna.game.model.def.DefinitionRepository.MapDefinitionRepository;

import java.util.Map;

/**
 * A model describing an equipment item definition.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class EquipmentDefinition implements Definition {

    /**
     * The equipment definition repository.
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
     * A list of equipment requirements.
     */
    private final ImmutableMap<Integer, Integer> requirements;

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
     * @param requirements A list of equipment requirements.
     * @param bonuses A list of equipment bonuses.
     */
    public EquipmentDefinition(int id, int index, boolean twoHanded, boolean fullBody, boolean fullHelmet,
                               Map<Integer, Integer> requirements, int[] bonuses) {
        this.id = id;
        this.index = index;
        this.twoHanded = twoHanded;
        this.fullBody = fullBody;
        this.fullHelmet = fullHelmet;
        this.requirements = ImmutableMap.copyOf(requirements);
        this.bonuses = ImmutableList.copyOf(Ints.asList(bonuses));
    }

    /**
     * @return The identifier.
     */
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
     * @return A list of equipment requirements.
     */
    public ImmutableMap<Integer, Integer> getRequirements() {
        return requirements;
    }

    /**
     * @return A list of equipment bonuses.
     */
    public ImmutableList<Integer> getBonuses() {
        return bonuses;
    }
}
