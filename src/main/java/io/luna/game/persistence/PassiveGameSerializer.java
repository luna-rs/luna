package io.luna.game.persistence;

import io.luna.game.model.World;

import java.util.Set;

/**
 * A {@link GameSerializer} implementation that is passive to all persistent data.
 *
 * @author lare96
 */
public final class PassiveGameSerializer extends GameSerializer {

    @Override
    public PlayerData loadPlayer(World world, String username) {
        return null;
    }

    @Override
    public void savePlayer(World world, String username, PlayerData data) {

    }

    @Override
    public boolean deletePlayer(World world, String username) {
        return false;
    }

    @Override
    public Set<String> loadBotUsernames(World world) {
        return Set.of();
    }
}