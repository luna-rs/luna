package io.luna.game.model.mob;

/**
 * Represents skull icons rendered above the player's head.
 *
 * @author lare96
 */
public enum SkullIcon {

    /**
     * No skull icon.
     */
    NONE(-1),

    /**
     * Standard white skull.
     */
    WHITE(0),

    /**
     * Red skull (often used for more severe penalties or custom PvP modes).
     */
    RED(1);

    /**
     * The protocol identifier used when encoding this icon.
     */
    private final int id;

    /**
     * Creates a new {@link SkullIcon}.
     *
     * @param id The protocol identifier for this icon.
     */
    SkullIcon(int id) {
        this.id = id;
    }

    /**
     * Returns the numeric identifier used by the appearance block encoding.
     *
     * @return The skull icon identifier.
     */
    public int getId() {
        return id;
    }
}