package io.luna.game.model.mob.persistence;

import io.luna.game.model.World;

import java.util.Set;

/**
 * A {@link PlayerSerializer} implementation that is passive to all persistent data.
 *
 * @author lare96
 */
public final class PassivePlayerSerializer extends PlayerSerializer {

    @Override
    public PlayerData load(World world, String username) throws Exception {
        return null;
    }

    @Override
    public void save(World world, String username, PlayerData data) throws Exception {

    }

    @Override
    public Set<String> loadBotUsernames(World world) throws Exception {
        return Set.of();
    }

    @Override
    public boolean delete(World world, String username) throws Exception {
        return false;
    }
}