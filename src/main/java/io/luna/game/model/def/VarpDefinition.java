package io.luna.game.model.def;

/**
 * A definition describing data for a varP decoded from the cache.
 *
 * @author lare96
 */
public final class VarpDefinition implements Definition {

    /**
     * The varp type.
     */
    public enum VarpType {

        VAR_BIT(0),

        BRIGHTNESS_LEVEL(1),

        MUSIC_VOLUME(3),

        SOUND_EFFECTS_VOLUME(4),

        MOUSE_BUTTON(5),

        DISABLE_CHAT_EFFECTS(6),

        SPLIT_PRIVATE_CHAT(8),

        BANK_INSERT_MODE(9);


        private final int id;

        VarpType(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    /**
     * A repository of all varP definitions.
     */
    public static ArrayDefinitionRepository<VarpDefinition> ALL = new ArrayDefinitionRepository<>(725);

    /**
     * The id of the varP.
     */
    private final int id;

    /**
     * The varP type.
     */
    private final int type;

    /**
     * Creates a new {@link VarpDefinition}.
     *
     * @param id The id of the varP.
     * @param type The varP type.
     */
    public VarpDefinition(int id, int type) {
        this.id = id;
        this.type = type;
    }

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The varP type.
     */
    public int getType() {
        return type;
    }
}
