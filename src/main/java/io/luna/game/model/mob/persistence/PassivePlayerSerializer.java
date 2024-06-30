package io.luna.game.model.mob.persistence;

import io.luna.LunaContext;

import java.util.Set;

/**
 * A {@link PlayerSerializer} implementation that is passive to all persistent data.
 *
 * @author lare96
 */
public final class PassivePlayerSerializer extends PlayerSerializer {

    /**
     * Creates a new {@link PassivePlayerSerializer}.
     *
     * @param context The context.
     */
    public PassivePlayerSerializer(LunaContext context) {
        super(context);
    }

    @Override
    public PlayerData load(String username) throws Exception {
        return null;
    }

    @Override
    public void save(String username, PlayerData data) throws Exception {

    }

    @Override
    public Set<String> loadBotUsernames() throws Exception {
        return Set.of();
    }

    @Override
    public boolean delete(String username) throws Exception {
        return false;
    }
}