package io.luna.game.model.mobile;

import static com.google.common.base.Preconditions.checkState;
import io.luna.LunaContext;
import io.luna.game.model.EntityType;
import io.luna.net.msg.OutboundGameMessage;
import io.luna.net.session.GameSession;
import io.luna.net.session.Session;
import io.luna.net.session.SessionState;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import plugin.LoginEvent;
import plugin.LogoutEvent;

import com.google.common.base.MoreObjects;

/**
 * A {@link MobileEntity} implementation that is controlled by a real person.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class Player extends MobileEntity {

    /**
     * The logger that will print important information.
     */
    private static final Logger LOGGER = LogManager.getLogger(Player.class);

    /**
     * The authority level of this {@code Player}.
     */
    private PlayerRights rights;

    /**
     * The credentials of this {@code Player}.
     */
    private PlayerCredentials credentials;

    /**
     * The {@link Session} assigned to this {@code Player}.
     */
    private GameSession session;

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
    public String toString() {
        return MoreObjects.toStringHelper(this).add("username", getUsername()).add("rights", rights).toString();
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

    @Override
    public void onActive() {
        if (session.getState() == SessionState.LOGIN_QUEUE) {
			plugins.post(new LoginEvent(this));

            LOGGER.info(this + " has logged in.");
        } else {
            throw new IllegalStateException("invalid session state");
        }
    }

    @Override
    public void onInactive() {
        if (session.getState() == SessionState.LOGOUT_QUEUE) {
			plugins.post(new LogoutEvent(this));

            PlayerSerializer serializer = new PlayerSerializer(this);
            serializer.asyncSave(service);

            session.setState(SessionState.LOGGED_OUT);

            LOGGER.info(this + " has logged out.");
        } else {
            throw new IllegalStateException("use Session#dispose, SendLogoutMessage, or World#queueLogout instead");
        }
    }

    /**
     * A shortcut function to {@link GameSession#queue(OutboundGameMessage)}.
     */
    public void queue(OutboundGameMessage msg) {
        session.queue(msg);
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

    /**
     * @return The {@link Session} assigned to this {@code Player}.
     */
    public GameSession getSession() {
        return session;
    }

    /**
     * Sets the value for {@link #session}.
     */
    public void setSession(GameSession session) {
        checkState(this.session == null, "session already set!");
        this.session = session;
    }
}
