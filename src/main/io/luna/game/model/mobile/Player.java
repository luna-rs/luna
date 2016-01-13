package io.luna.game.model.mobile;

import com.google.common.base.MoreObjects;
import io.luna.LunaContext;
import io.luna.game.model.Direction;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.update.UpdateFlagHolder.UpdateFlag;
import io.luna.net.msg.OutboundGameMessage;
import io.luna.net.msg.out.SendAssignmentMessage;
import io.luna.net.session.GameSession;
import io.luna.net.session.Session;
import io.luna.net.session.SessionState;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import plugin.LoginEvent;
import plugin.LogoutEvent;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

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
     * The {@link Set} of local {@code Player}s.
     */
    private final Set<Player> localPlayers = new LinkedHashSet<>();

    /**
     * The credentials of this {@code Player}.
     */
    private final PlayerCredentials credentials;

    /**
     * The current cached block for this update cycle.
     */
    private ByteBuf cachedBlock;

    /**
     * The authority level of this {@code Player}.
     */
    private PlayerRights rights = PlayerRights.PLAYER;

    /**
     * The {@link Session} assigned to this {@code Player}.
     */
    private GameSession session;

    /**
     * The last known region that this {@code Player} was in.
     */
    private Position lastRegion;

    /**
     * If the region has changed during this cycle.
     */
    private boolean regionChanged;

    /**
     * The walking direction of this {@code Player}.
     */
    private Direction runningDirection = Direction.NONE;

    /**
     * The {@link Chat} message to send during this update block.
     */
    private Chat chat;

    /**
     * The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    private ForceMovement forceMovement;

    /**
     * Creates a new {@link Player}.
     *
     * @param context The context to be managed in.
     */
    public Player(LunaContext context, PlayerCredentials credentials) {
        super(context);
        this.credentials = credentials;
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
            updateFlags.flag(UpdateFlag.REGION);
            updateFlags.flag(UpdateFlag.APPEARANCE);

            queue(new SendAssignmentMessage(true));

            plugins.post(new LoginEvent(), this);

            session.setState(SessionState.LOGGED_IN);
            LOGGER.info(this + " has logged in.");
        } else {
            throw new IllegalStateException("invalid session state");
        }
    }

    @Override
    public void onInactive() {
        if (session.getState() == SessionState.LOGOUT_QUEUE) {
            plugins.post(new LogoutEvent(), this);

            session.setState(SessionState.LOGGED_OUT);

            LOGGER.info(this + " has logged out.");

            PlayerSerializer serializer = new PlayerSerializer(this);
            serializer.asyncSave(service);
        } else {
            throw new IllegalStateException("use SendLogoutMessage, or Session.getChannel()#close instead");
        }
    }

    @Override
    public void resetEntity() {
        chat = null;
        regionChanged = false;
    }

    /**
     * Send {@code chat} message for this cycle.
     *
     * @param chat The {@link Chat} message to send during this update block.
     */
    public void chat(Chat chat) {
        this.chat = requireNonNull(chat);
        updateFlags.flag(UpdateFlag.CHAT);
    }

    /**
     * Send {@code forceMovement} message for this cycle.
     *
     * @param forceMovement The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    public void forceMovement(ForceMovement forceMovement) {
        this.forceMovement = requireNonNull(forceMovement);
        updateFlags.flag(UpdateFlag.FORCE_MOVEMENT);
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

    /**
     * @return The {@link Set} of local {@code Players}.
     */
    public Set<Player> getLocalPlayers() {
        return localPlayers;
    }

    /**
     * @return The current cached block for this update cycle.
     */
    public ByteBuf getCachedBlock() {
        return cachedBlock;
    }

    /**
     * Sets the value for {@link #cachedBlock}.
     */
    public void setCachedBlock(ByteBuf cachedBlock) {
        ByteBuf currentBlock = this.cachedBlock;

        if (currentBlock != null) {
            currentBlock.release();
        }
        if (cachedBlock != null) {
            cachedBlock.retain();
        }
        this.cachedBlock = cachedBlock;
    }

    /**
     * @return The last known region that this {@code Player} was in.
     */
    public Position getLastRegion() {
        return lastRegion;
    }

    /**
     * Sets the value for {@link #lastRegion}.
     */
    public void setLastRegion(Position lastRegion) {
        this.lastRegion = lastRegion;
    }

    /**
     * @return {@code true} if the region has changed during this cycle, {@code false} otherwise.
     */
    public boolean isRegionChanged() {
        return regionChanged;
    }

    /**
     * Sets the value for {@link #regionChanged}.
     */
    public void setRegionChanged(boolean regionChanged) {
        this.regionChanged = regionChanged;
    }

    /**
     * @return The walking direction of this {@code Player}.
     */
    public Direction getRunningDirection() {
        return runningDirection;
    }

    /**
     * Sets the value for {@link #runningDirection}.
     */
    public void setRunningDirection(Direction runningDirection) {
        this.runningDirection = runningDirection;
    }

    /**
     * @return The {@link Chat} message to send during this update block.
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * @return The {@link ForceMovement} that dictates where this {@code Player} will be forced to move.
     */
    public ForceMovement getForceMovement() {
        return forceMovement;
    }
}
