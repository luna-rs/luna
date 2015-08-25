package io.luna.game.plugin;

import io.luna.game.model.mobile.player.Player;

/**
 * A simple plugin event that wraps the
 * {@link io.luna.game.model.mobile.player.Player} instance. Virtually all
 * plugin events will extend this class.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public class PlayerPluginEvent {

    /**
     * The player this plugin event is for.
     */
    private final Player player;

    /**
     * Creates a new {@link io.luna.game.plugin.PlayerPluginEvent}.
     *
     * @param player
     *            The player this plugin event is for.
     */
    public PlayerPluginEvent(Player player) {
        this.player = player;
    }

    /**
     * Gets the player this plugin event is for.
     * 
     * @return The player for this event.
     */
    public final Player getPlayer() {
        return player;
    }
}
