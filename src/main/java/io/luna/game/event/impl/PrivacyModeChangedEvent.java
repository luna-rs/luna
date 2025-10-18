package io.luna.game.event.impl;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.PlayerPrivacy;

/**
 * A {@link PlayerEvent} implementation sent when the player changes their privacy mode.
 *
 * @author lare96
 */
public final class PrivacyModeChangedEvent extends PlayerEvent {

    /**
     * The old privacy mode set.
     */
    private final PlayerPrivacy oldPrivacy;

    /**
     * The new privacy mode set.
     */
    private final PlayerPrivacy newPrivacy;

    /**
     * Creates a new {@link PrivacyModeChangedEvent}.
     *
     * @param player The player.
     * @param oldPrivacy The old privacy mode set.
     * @param newPrivacy The new privacy mode set.
     */
    public PrivacyModeChangedEvent(Player player, PlayerPrivacy oldPrivacy, PlayerPrivacy newPrivacy) {
        super(player);
        this.oldPrivacy = oldPrivacy;
        this.newPrivacy = newPrivacy;
    }

    /**
     * @return The old privacy mode set.
     */
    public PlayerPrivacy getOldPrivacy() {
        return oldPrivacy;
    }

    /**
     * @return The new privacy mode set.
     */
    public PlayerPrivacy getNewPrivacy() {
        return newPrivacy;
    }
}
