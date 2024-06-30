package io.luna.game.model.mob;

import io.luna.util.StringUtils;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * An enumerated type representing all possible authority levels a {@link Player} can hold. The position of the
 * elements have a significant importance as the lower an element is, the higher authority it holds. We use {@link #ordinal()}
 * so that we can use >, <, >=, and <= operators in Kotlin to compare types.
* <p>
 * <strong>Warning: Do not rearrange elements unless you know what you're doing!</strong>
 *
 * @author lare96
 */
public enum PlayerRights {

    PLAYER(0),
    MODERATOR(1),
    ADMINISTRATOR(2),
    DEVELOPER(2);

    /**
     * The protocol value. May only be between 0-2 (player, moderator, administrator).
     */
    private final int clientValue;

    /**
     * The formatted name. Lowercase and capitalized.
     */
    private final String formattedName;

    /**
     * Creates a new {@link PlayerRights}.
     *
     * @param clientValue The protocol value.
     */
    PlayerRights(int clientValue) {
        checkArgument(clientValue >= 0 && clientValue <= 2, "clientValue must be >= 0 && <= 2");

        this.clientValue = clientValue;
        formattedName = StringUtils.capitalize(name().toLowerCase());
    }

    /**
     * @return The protocol value.
     */
    public final int getClientValue() {
        return clientValue;
    }

    /**
     * @return The formatted name. Lowercase and capitalized.
     */
    public String getFormattedName() {
        return formattedName;
    }
}
