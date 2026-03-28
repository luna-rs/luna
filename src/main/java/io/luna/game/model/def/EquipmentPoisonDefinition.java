package io.luna.game.model.def;

/**
 * A definition that maps a piece of equipment to its poison severity.
 * <p>
 * This definition is typically used for equippable items that can inflict poison when used in combat. The {@code id}
 * identifies the equipment item, while {@code severity} represents the poison strength associated with that item.
 * <p>
 * All loaded equipment poison definitions are stored in {@link #ALL}.
 *
 * @author lare96
 */
public class EquipmentPoisonDefinition implements Definition {

    /**
     * A repository containing every loaded {@link EquipmentPoisonDefinition}.
     */
    public static final MapDefinitionRepository<EquipmentPoisonDefinition> ALL = new MapDefinitionRepository<>();

    /**
     * The item id for the poisoned equipment.
     */
    private final int id;

    /**
     * The poison severity applied by this equipment.
     */
    private final int severity;

    /**
     * Creates a new equipment poison definition.
     *
     * @param id The item id for the poisoned equipment.
     * @param severity The poison severity applied by the equipment.
     */
    public EquipmentPoisonDefinition(int id, int severity) {
        this.id = id;
        this.severity = severity;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The poison severity.
     */
    public int getSeverity() {
        return severity;
    }
}