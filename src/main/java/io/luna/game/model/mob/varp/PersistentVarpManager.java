package io.luna.game.model.mob.varp;

import io.luna.game.model.mob.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that manages persistent {@link Varp} types set by the server. This manager only accounts for some varps that
 * are persisted to the character file, like settings. For a full list of persistent varps and/or to add new ones,
 * please see {@link PersistentVarp}.
 *
 * @author lare96
 */
public final class PersistentVarpManager {

    /**
     * The player.
     */
    private final Player player;

    /**
     * The map of {@link PersistentVarp} types.
     */
    private final EnumMap<PersistentVarp, Integer> varps = new EnumMap<>(PersistentVarp.class);

    /**
     * Creates a new {@link PersistentVarpManager}.
     *
     * @param player The player.
     */
    public PersistentVarpManager(Player player) {
        this.player = player;
    }

    /**
     * Sets the value of {@code varp}.
     *
     * @param varpType The varp type.
     * @param value The value.
     */
    public void setValue(PersistentVarp varpType, int value) {
        varps.put(varpType, value);
    }

    /**
     * Retrieves the value of {@code varp}.
     *
     * @param varpType The varp type.
     * @return The stored value.
     */
    public int getValue(PersistentVarp varpType) {
        Integer value = varps.get(varpType);
        return value != null ? value : 0;
    }

    /**
     * Sends the currently stored value for all {@link PersistentVarp} types.
     */
    public void sendAllValues() {
        for (PersistentVarp persistentVarp : PersistentVarp.ALL.values()) {
            sendValue(persistentVarp);
        }
    }

    /**
     * Sends the currently stored value for {@code varpType}.
     *
     * @param varpType The varp type.
     */
    public void sendValue(PersistentVarp varpType) {
        Varp varp = new Varp(varpType.getClientId(), getValue(varpType));
        player.sendVarp(varp);
    }

    /**
     * Sets the currently stored value for {@code varpType} and sends it.
     *
     * @param varpType The varp type.
     * @param value The value.
     */
    public void setAndSendValue(PersistentVarp varpType, int value) {
        varps.put(varpType, value);
        player.sendVarp(new Varp(varpType.getClientId(), value));
    }

    /**
     * Loads persisted varp data from {@code savedVarps}.
     *
     * @param persistentVarps The map to load from.
     */
    public void fromMap(Map<String, Integer> persistentVarps) {
        if (persistentVarps != null) {
            for (var entry : persistentVarps.entrySet()) {
                PersistentVarp persistentVarp = PersistentVarp.valueOf(entry.getKey());
                varps.put(persistentVarp, entry.getValue());
            }
        }
    }

    /**
     * Converts the varp data into a map to be persisted.
     *
     * @return The map that will be saved.
     */
    public Map<String, Integer> toMap() {
        Map<String, Integer> persistentVarps = new HashMap<>();
        for (var entry : varps.entrySet()) {
            persistentVarps.put(entry.getKey().name(), entry.getValue());
        }
        return persistentVarps;
    }
}
