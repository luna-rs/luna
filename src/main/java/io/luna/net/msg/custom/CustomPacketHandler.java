package io.luna.net.msg.custom;

import io.luna.game.model.mob.Player;

import java.util.List;

/**
 * Validates and handles one stable custom packet identifier on the game thread.
 *
 * @author lare96
 */
public interface CustomPacketHandler {

    /**
     * @return The stable custom identifier, independent of native protocol opcodes.
     */
    int id();

    /**
     * Checks the argument schema without changing state or executing feature logic.
     *
     * @param payload The decoded flat values.
     * @return Whether the count, types, and ranges are accepted.
     */
    boolean validate(List<Object> payload);

    /**
     * @return Whether this explicitly passive handler can leave player automation running.
     */
    default boolean isPassive() {
        return false;
    }

    /**
     * Executes after normal reader action-lock and controller checks. Implementations must enforce any
     * feature-specific permission and safely resolve the requested game data before using it.
     *
     * @param player The requesting player.
     * @param request The schema-validated request.
     */
    void handle(Player player, CustomPacketRequest request);
}
