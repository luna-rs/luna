package io.luna.game.model.mob.inter;

import io.luna.game.action.Action;
import io.luna.game.model.mob.Player;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * An abstraction model representing an interface that can be opened and closed on a Player's screen.
 *
 * @author lare96 <http://github.com/lare96>
 */
public abstract class AbstractInterface {

    /**
     * The interface identifier.
     */
    final OptionalInt id;

    /**
     * Creates a new {@link AbstractInterface}.
     *
     * @param id The interface identifier.
     */
    private AbstractInterface(OptionalInt id) {
        this.id = id;
    }

    /**
     * Creates a new {@link AbstractInterface} with {@code id} wrapped in an optional.
     *
     * @param id The interface identifier.
     */
    AbstractInterface(int id) {
        this(OptionalInt.of(id));
    }

    /**
     * Creates a new {@link AbstractInterface} with no interface identifier.
     */
    AbstractInterface() {
        this(OptionalInt.empty());
    }

    /**
     * Opens this interface.
     *
     * @param player The player to open for.
     */
    public abstract void open(Player player);

    /**
     * A function called when this interface is closed.
     *
     * @param player The player to apply to listener for.
     */
    public void onClose(Player player) {
    }

    /**
     * Determines if this interface should be closed on movement or action initialization.
     *
     * @param player The player to determine for.
     * @return {@code true} if starting an {@link Action} or movement closes this interface.
     */
    public boolean isCloseOnAction(Player player) {
        return true;
    }

    /**
     * Determines if this interface is walkable.
     *
     * @return {@code true} if this interface is walkable.
     */
    public final boolean isWalkable() {
        return this instanceof WalkableInterface;
    }

    /**
     * Determines if this interface is input.
     *
     * @return {@code true} if this interface is input.
     */
    public final boolean isInput() {
        return this instanceof InputInterface;
    }

    /**
     * Determines if this interface is standard.
     *
     * @return {@code true} if this interface is standard.
     */
    public final boolean isStandard() {
        return this instanceof StandardInterface;
    }

    /**
     * Retrieves this interface as walkable.
     *
     * @return This interface, as walkable.
     */
    public final Optional<WalkableInterface> getAsWalkable() {
        if (isWalkable()) {
            return Optional.of((WalkableInterface) this);
        }
        return Optional.empty();
    }

    /**
     * Retrieves this interface as input.
     *
     * @return This interface, as input.
     */
    public final Optional<InputInterface> getAsInput() {
        if (isInput()) {
            return Optional.of((InputInterface) this);
        }
        return Optional.empty();
    }

    /**
     * Retrieves this interface as standard.
     *
     * @return This interface, as standard.
     */
    public final Optional<StandardInterface> getAsStandard() {
        if (isStandard()) {
            return Optional.of((StandardInterface) this);
        }
        return Optional.empty();
    }

    /**
     * Retrieves the identifier within {@link #id}. If it doesn't exist, returns {@code -1}.
     *
     * @return The identifier, or {@code -1} if none exist.
     */
    public final int unsafeGetId() {
        return id.orElse(-1);
    }

    /**
     * @return The interface identifier.
     */
    public final OptionalInt getId() {
        return id;
    }
}
