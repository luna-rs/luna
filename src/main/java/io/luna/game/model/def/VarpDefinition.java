package io.luna.game.model.def;

/**
 * A cache-backed definition describing a client/server {@code varp} (variable parameter).
 * <p>
 * {@code varp}s are integer variables used by the client to represent and drive settings/state such as brightness,
 * volume levels, chat options, and other data. This definition exposes:
 * <ul>
 *     <li>the varp id ({@link #getId()})</li>
 *     <li>a decoded “type” field ({@link #getType()}) that categorizes the varp</li>
 * </ul>
 * <p>
 * <b>Repository:</b> All definitions are stored in {@link #ALL}, indexed by id. The repository size (725) should match
 * the expected varp count for the target cache/protocol.
 * <p>
 * <b>Type mapping:</b> {@link VarpType} contains known/commonly-used type ids observed in the cache. The {@link #type}
 * field is stored as an {@code int} because not every cache value is guaranteed to be covered by the enum.
 *
 * @author lare96
 */
public final class VarpDefinition implements Definition {

    /**
     * Known varp type ids.
     * <p>
     * These values categorize certain varps into commonly understood buckets (settings, toggles, etc.).
     * Not all varp types in a cache are necessarily represented here.
     */
    public enum VarpType {

        /**
         * A varp that participates in varbit packing/unpacking.
         */
        VAR_BIT(0),

        /**
         * Brightness setting level.
         */
        BRIGHTNESS_LEVEL(1),

        /**
         * Music volume setting level.
         */
        MUSIC_VOLUME(3),

        /**
         * Sound effects volume setting level.
         */
        SOUND_EFFECTS_VOLUME(4),

        /**
         * Mouse button mode (e.g., one-button vs two-button).
         */
        MOUSE_BUTTON(5),

        /**
         * Chat effects enabled/disabled toggle.
         */
        DISABLE_CHAT_EFFECTS(6),

        /**
         * Split private chat mode toggle.
         */
        SPLIT_PRIVATE_CHAT(8),

        /**
         * Bank rearrange mode (insert vs swap).
         */
        BANK_INSERT_MODE(9);

        /**
         * The raw cache id for this varp type.
         */
        private final int id;

        VarpType(int id) {
            this.id = id;
        }

        /**
         * Returns the raw cache id for this type.
         *
         * @return The type id.
         */
        public int getId() {
            return id;
        }
    }

    /**
     * Repository of all {@link VarpDefinition}s, indexed by varp id.
     * <p>
     * The repository capacity ({@code 725}) reflects the expected number of varps for this cache revision.
     */
    public static ArrayDefinitionRepository<VarpDefinition> ALL = new ArrayDefinitionRepository<>(725);

    /**
     * The varp id.
     */
    private final int id;

    /**
     * The raw varp type value decoded from the cache.
     * <p>
     * This is an {@code int} rather than {@link VarpType} to allow unknown/unmapped types.
     */
    private final int type;

    /**
     * Creates a new {@link VarpDefinition}.
     *
     * @param id The varp id.
     * @param type The raw varp type value decoded from the cache.
     */
    public VarpDefinition(int id, int type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Returns the varp id.
     *
     * @return The id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns the raw varp type value.
     * <p>
     * If you want to interpret this as a known {@link VarpType}, you can compare against {@link VarpType#getId()}.
     *
     * @return The raw type value.
     */
    public int getType() {
        return type;
    }
}
