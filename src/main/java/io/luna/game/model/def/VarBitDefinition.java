package io.luna.game.model.def;

/**
 * A definition describing data for var bits decoded from the cache.
 *
 * @author lare96
 */
public final class VarBitDefinition implements Definition {

    /**
     * The repository of var bit definitions.
     */
    public static final MultiMapDefinitionRepository<VarBitDefinition> ALL = new MultiMapDefinitionRepository<>();

    /**
     * The var bit id.
     */
    private final int id;

    /**
     * The parent varp id.
     */
    private final int parentVarpId;

    /**
     * The least significant bit.
     */
    private final int leastSignificantBit;

    /**
     * The most significant bit.
     */
    private final int mostSignificantBit;

    /**
     * Creates a new {@link VarBitDefinition}.
     *
     * @param id The var bit id.
     * @param parentVarpId The parent varp id.
     * @param leastSignificantBit The least significant bit.
     * @param mostSignificantBit The most significant bit.
     */
    public VarBitDefinition(int id, int parentVarpId, int leastSignificantBit, int mostSignificantBit) {
        this.id = id;
        this.parentVarpId = parentVarpId;
        this.leastSignificantBit = leastSignificantBit;
        this.mostSignificantBit = mostSignificantBit;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The parent varp id.
     */
    public int getParentVarpId() {
        return parentVarpId;
    }

    /**
     * @return The least significant bit.
     */
    public int getLeastSignificantBit() {
        return leastSignificantBit;
    }

    /**
     * @return The most significant bit.
     */
    public int getMostSignificantBit() {
        return mostSignificantBit;
    }
}
