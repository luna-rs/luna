package io.luna.game.model.mobile;

import static com.google.common.base.Preconditions.checkState;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;

import java.util.Objects;

/**
 * A {@link MobileEntity} implementation that is controlled by a real person.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Player extends MobileEntity {

    /**
     * The authority level of this {@code Player}.
     */
    private PlayerRights rights;

    /**
     * The credentials of this {@code Player}.
     */
    private PlayerCredentials credentials;

    /**
     * Creates a new {@link Player}.
     *
     * @param context The context to be managed in.
     */
    public Player(LunaContext context) {
        super(context);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public EntityType type() {
        return EntityType.PLAYER;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUsernameHash());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Player) {
            Player other = (Player) obj;
            return other.getUsernameHash() == getUsernameHash();
        }
        return false;
    }

    /**
     * @return The authority level of this {@code Player}.
     */
    public PlayerRights getRights() {
        return rights;
    }

    /**
     * Sets the value for {@link #rights}.
     */
    public void setRights(PlayerRights rights) {
        this.rights = rights;
    }

    /**
     * @return The credentials of this {@code Player}.
     */
    public PlayerCredentials getCredentials() {
        return credentials;
    }

    /**
     * @return The username of this {@code Player}.
     */
    public String getUsername() {
        return credentials.getUsername();
    }

    /**
     * @return The password of this {@code Player}.
     */
    public String getPassword() {
        return credentials.getPassword();
    }

    /**
     * @return The username hash of this {@code Player}.
     */
    public long getUsernameHash() {
        return credentials.getUsernameHash();
    }

    /**
     * Sets the value for {@link #credentials}.
     */
    public void setCredentials(PlayerCredentials credentials) {
        checkState(this.credentials == null, "credentials already set!");
        this.credentials = credentials;
    }
}
