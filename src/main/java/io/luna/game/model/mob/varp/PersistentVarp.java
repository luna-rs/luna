package io.luna.game.model.mob.varp;

import com.google.common.collect.ImmutableMap;
import io.luna.game.model.mob.Player;

/**
 * An enum representing an identifier for a {@link Varp} that is persisted by this server. These varps are usually
 * related to some sort of player settings.
 *
 * @author lare96
 */
public enum PersistentVarp {
    WITHDRAW_AS_NOTE(115),
    BRIGHTNESS_LEVEL(166),
    MUSIC_VOLUME(168),
    EFFECTS_VOLUME(169),
    MOUSE_TYPE(170),
    CHAT_EFFECTS(171),
    AUTO_RETALIATE(172),
    RUNNING(173),
    SPLIT_PRIVATE_CHAT(287),
    ACCEPT_AID(427);

    /**
     * All varps within this enum mapped by client value -> PersistentVarp.
     */
    public static final ImmutableMap<Integer, PersistentVarp> ALL;

    static {
        ImmutableMap.Builder<Integer, PersistentVarp> builder = ImmutableMap.builder();
        for (PersistentVarp varp : values()) {
            builder.put(varp.clientId, varp);
        }
        ALL = builder.build();
    }

    /**
     * The client id of this server varp ({@link Varp#getId()}).
     */
    private final int clientId;

    /**
     * Creates a new {@link PersistentVarp}.
     *
     * @param clientId The client id.
     */
    PersistentVarp(int clientId) {
        this.clientId = clientId;
    }

    /**
     * @return The client id of this server varp ({@link Varp#getId()}).
     */
    public int getClientId() {
        return clientId;
    }
}
