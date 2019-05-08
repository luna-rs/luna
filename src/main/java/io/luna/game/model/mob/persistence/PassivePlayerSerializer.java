package io.luna.game.model.mob.persistence;

/**
 * A {@link PlayerSerializer} implementation that is passive to all persistent data.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PassivePlayerSerializer extends PlayerSerializer {

    @Override
    public PlayerData load(String username) throws Exception {

        return null;
    }

    @Override
    public void save(String username, PlayerData data) throws Exception {

    }
}