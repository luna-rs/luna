package io.luna.game.model.def;

/**
 * A cache-backed definition describing a {@code varbit} (variable bitfield) decoded from the cache.
 * <p>
 * A {@code varbit} is a small integer value packed into a range of bits inside a parent {@code varp}. The client and
 * server use varbits to represent many compact states (toggles, progress flags, small counters) without allocating a
 * full varp for each value.
 * <p>
 * This definition specifies:
 * <ul>
 *     <li>the varbit id ({@link #getId()})</li>
 *     <li>the parent varp id that contains the packed bits ({@link #getParentVarpId()})</li>
 *     <li>the inclusive bit range within the parent varp:
 *         {@link #getLeastSignificantBit()}..{@link #getMostSignificantBit()}</li>
 * </ul>
 * <p>
 * <b>Bit range semantics:</b>
 * The least/most significant bit fields describe where the varbit lives inside the parent varp's integer value.
 *
 * @author lare96
 */
public final class VarBitDefinition implements Definition {

    /**
     * The repository of all {@link VarBitDefinition}s.
     */
    public static final MultiMapDefinitionRepository<VarBitDefinition> ALL = new MultiMapDefinitionRepository<>();

    /**
     * The varbit id.
     */
    private final int id;

    /**
     * The id of the parent varp that contains this varbit's packed bit range.
     */
    private final int parentVarpId;

    /**
     * The least significant bit (LSB) position of this varbit within the parent varp.
     */
    private final int leastSignificantBit;

    /**
     * The most significant bit (MSB) position of this varbit within the parent varp.
     *
     * <p>
     * The bit range is typically treated as inclusive.
     */
    private final int mostSignificantBit;

    /**
     * Creates a new {@link VarBitDefinition}.
     *
     * @param id The varbit id.
     * @param parentVarpId The parent varp id that contains the packed value.
     * @param leastSignificantBit The least significant bit position within the parent varp.
     * @param mostSignificantBit The most significant bit position within the parent varp (inclusive).
     */
    public VarBitDefinition(int id, int parentVarpId, int leastSignificantBit, int mostSignificantBit) {
        this.id = id;
        this.parentVarpId = parentVarpId;
        this.leastSignificantBit = leastSignificantBit;
        this.mostSignificantBit = mostSignificantBit;
    }

    /**
     * Returns the varbit id.
     *
     * @return The id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns the parent varp id that contains this varbit's packed bit range.
     *
     * @return The parent varp id.
     */
    public int getParentVarpId() {
        return parentVarpId;
    }

    /**
     * Returns the least significant bit position within the parent varp.
     *
     * @return The LSB bit index.
     */
    public int getLeastSignificantBit() {
        return leastSignificantBit;
    }

    /**
     * Returns the most significant bit position within the parent varp.
     *
     * @return The MSB bit index (inclusive).
     */
    public int getMostSignificantBit() {
        return mostSignificantBit;
    }
}
