package io.luna.game.model.mob.persistence;

import io.luna.LunaConstants;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerRights;
import io.luna.net.codec.login.LoginResponse;

/**
 * A {@link PlayerSerializer} implementation that is passive to all persistent data. It does nothing besides set
 * the player's rights to the highest and move them to the default position.
 *
 * @author lare96 <http://github.com/lare96>
 */
public final class PassivePlayerSerializer extends PlayerSerializer {

    @Override
    public LoginResponse load(Player player, String enteredPassword) {
        player.setRights(PlayerRights.DEVELOPER);
        player.setPosition(LunaConstants.STARTING_POSITION);
        return LoginResponse.NORMAL;
    }

    @Override
    public boolean save(Player player) {
        return true;
    }
}