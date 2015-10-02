package io.luna.game.model;

import io.luna.game.model.mobile.MobileEntityList;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;
import io.luna.game.model.region.RegionManager;
import io.luna.util.StringUtils;

import java.util.Optional;

/**
 * Manages the various types in the {@code io.luna.game.model} package and
 * subpackages.
 * 
 * @author lare96 <http://github.org/lare96>
 */
public final class World {

    /**
     * The list of {@link Player}s in the world.
     */
    private final MobileEntityList<Player> players = new MobileEntityList<>(EntityConstants.MAXIMUM_PLAYERS);

    /**
     * The list of {@link Npc}s in the world.
     */
    private final MobileEntityList<Npc> npcs = new MobileEntityList<>(EntityConstants.MAXIMUM_NPCS);

    /**
     * The {@link RegionManager} that manages region caching.
     */
    private final RegionManager regions = new RegionManager();

    /**
     * Retrieves a {@link Player} instance by its {@code username}.
     * 
     * @param username The username hash of the {@code Player}.
     * @return The {@code Player} instance wrapped in an {@link Optional}, or an
     *         empty {@code Optional} if no {@code Player} was found.
     */
    public Optional<Player> getPlayer(long username) {
        return players.findFirst(it -> it.getUsernameHash() == username);
    }

    /**
     * Retrieves a {@link Player} instance by its {@code username}.
     * 
     * @param username The username of the {@code Player}.
     * @return The {@code Player} instance wrapped in an {@link Optional}, or an
     *         empty {@code Optional} if no {@code Player} was found.
     */
    public Optional<Player> getPlayer(String username) {
        return getPlayer(StringUtils.encodeToBase37(username));
    }

    /**
     * @return The {@link RegionManager} instance.
     */
    public RegionManager getRegions() {
        return regions;
    }

    /**
     * @return The list of {@link Player}s in the world.
     */
    public MobileEntityList<Player> getPlayers() {
        return players;
    }

    /**
     * @return The list of {@link Npc}s in the world.
     */
    public MobileEntityList<Npc> getNpcs() {
        return npcs;
    }
}
