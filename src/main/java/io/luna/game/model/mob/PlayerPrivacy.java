package io.luna.game.model.mob;

import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A model representing the privacy settings for a player.
 *
 * <p>
 * Each setting controls visibility and access for:
 * </p>
 *
 * <ul>
 *     <li><b>Public Chat</b>, whether other players public chat messages can be seen.</li>
 *     <li><b>Private Chat</b>, who can private message the player and see their online status.</li>
 *     <li><b>Trade/Requests</b>, who can send trade or other interaction requests to the player.</li>
 * </ul>
 *
 * @author lare96
 */
public final class PlayerPrivacy {

    /**
     * The logger.
     */
    private static final Logger logger = LogManager.getLogger(PlayerPrivacy.class);

    /**
     * Represents different privacy modes that can be selected by a player.
     */
    public enum PrivacyMode {

        /**
         * Visible to everyone.
         */
        ON(0),

        /**
         * Visible to friends only.
         */
        FRIENDS(1),

        /**
         * Fully hidden.
         */
        OFF(2),

        /**
         * Special hidden mode used for public chat only.
         */
        HIDE(3);

        /**
         * All privacy modes mapped to their client IDs.
         */
        private static final ImmutableMap<Integer, PrivacyMode> ALL;

        static {
            ImmutableMap.Builder<Integer, PrivacyMode> map = ImmutableMap.builder();
            for (PrivacyMode mode : values()) {
                map.put(mode.clientId, mode);
            }
            ALL = map.build();
        }

        /**
         * The client ID.
         */
        private final int clientId;

        /**
         * Creates a new {@link PrivacyMode} with the given client ID.
         *
         * @param clientId The client ID.
         */
        PrivacyMode(int clientId) {
            this.clientId = clientId;
        }

        /**
         * Converts a raw client ID into a corresponding {@link PrivacyMode}.
         *
         * <p>
         * If the ID is not valid, this method will log a warning and return {@link #ON} by default.
         * </p>
         *
         * @param id The raw client ID.
         * @return The corresponding privacy mode, or {@link #ON} if the ID is invalid.
         */
        public static PrivacyMode fromId(int id) {
            PrivacyMode mode = ALL.get(id);
            if (mode == null) {
                logger.warn("Invalid privacy mode ID [{}], defaulting to ON.", id);
                return ON;
            }
            return mode;
        }

        /**
         * @return The client ID.
         */
        public int getId() {
            return clientId;
        }
    }

    /**
     * The privacy setting for public chat.
     */
    private final PrivacyMode publicChat;

    /**
     * The privacy setting for private chat.
     */
    private final PrivacyMode privateChat;

    /**
     * The privacy setting for interactions.
     */
    private final PrivacyMode trade;

    /**
     * Creates a new {@link PlayerPrivacy} with all settings set to {@link PrivacyMode#ON}.
     */
    public PlayerPrivacy() {
        this(PrivacyMode.ON, PrivacyMode.ON, PrivacyMode.ON);
    }

    /**
     * Creates a new {@link PlayerPrivacy} with custom settings.
     *
     * @param publicChat The privacy mode for public chat.
     * @param privateChat The privacy mode for private chat.
     * @param trade The privacy mode for interactions.
     */
    public PlayerPrivacy(PrivacyMode publicChat, PrivacyMode privateChat, PrivacyMode trade) {
        this.publicChat = publicChat;
        this.privateChat = privateChat;
        this.trade = trade;
    }

    /**
     * @return The public chat privacy setting.
     */
    public PrivacyMode getPublicChat() {
        return publicChat;
    }

    /**
     * @return The private chat privacy setting.
     */
    public PrivacyMode getPrivateChat() {
        return privateChat;
    }

    /**
     * @return The trade privacy setting.
     */
    public PrivacyMode getTrade() {
        return trade;
    }
}
