package io.luna.game.model.region;

import io.luna.LunaConstants;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mobile.MobileEntity;
import io.luna.game.model.mobile.Npc;
import io.luna.game.model.mobile.Player;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all of the cached {@link Region}s and the {@link Entity}s contained within them.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public final class RegionManager {

    /**
     * The map of cached {@link Region}s.
     */
    private final Map<RegionCoordinates, Region> regions = new ConcurrentHashMap<>();

    /**
     * Returns a {@link Region} based on the given region {@code X} and region {@code Y} coordinates.
     *
     * @param x The region {@code X} coordinate.
     * @param y The region {@code Y} coordinate.
     * @return The region in accordance with these coordinates.
     */
    public Region getRegion(int x, int y) {
        return getRegion(new RegionCoordinates(x, y));
    }

    /**
     * Returns a {@link Region} based on the given {@code pos}.
     *
     * @param pos The position.
     * @return The region in accordance with this {@code pos}.
     */
    public Region getRegion(Position pos) {
        return getRegion(RegionCoordinates.create(pos));
    }

    /**
     * Returns a {@link Region} based on the given {@code coordinates}, creates and inserts a new {@code Region} if none
     * present.
     *
     * @param coordinates The {@link RegionCoordinates}.
     * @return The region in accordance with {@code coordinates}.
     */
    public Region getRegion(RegionCoordinates coordinates) {
        return regions.computeIfAbsent(coordinates, Region::new);
    }

    /**
     * Determines if a {@link Region} exists in accordance with {@code pos}.
     *
     * @param pos The position.
     * @return {@code true} if a {@code Region} exists, {@code false} otherwise.
     */
    public boolean exists(Position pos) {
        return regions.containsKey(RegionCoordinates.create(pos));
    }

    /**
     * Gets all of the {@link Player}s surrounding {@code player}, prioritized in an order somewhat identical to Runescape.
     * This is done so that staggered updating does not interfere negatively with gameplay.
     *
     * @param player The {@link Player}.
     * @return The local, prioritized, {@code Player}s.
     */
    public Set<Player> getSurroundingPlayers(Player player) {
        List<Region> allRegions = getSurroundingRegions(player.getPosition());
        Set<Player> localPlayers = getBackingSet(player);

        for (Region region : allRegions) {
            List<Player> regionPlayers = region.getEntities(EntityType.PLAYER);

            for (Player inRegion : regionPlayers) {
                if (inRegion.isViewable(player)) {
                    localPlayers.add(inRegion);
                }
            }
        }
        return localPlayers;
    }

    /**
     * Gets all of the {@link Npc}s surrounding {@code player}, prioritized in an order somewhat identical to Runescape. This
     * is done so that staggered updating does not interfere negatively with gameplay.
     *
     * @param player The {@link Player}.
     * @return The local, prioritized, {@code Npc}s.
     */
    public Set<Npc> getSurroundingNpcs(Player player) {
        List<Region> allRegions = getSurroundingRegions(player.getPosition());
        Set<Npc> localNpcs = getBackingSet(player);

        for (Region region : allRegions) {
            List<Npc> regionNpcs = region.getEntities(EntityType.NPC);

            for (Npc inRegion : regionNpcs) {
                if (inRegion.isViewable(player)) {
                    localNpcs.add(inRegion);
                }
            }
        }
        return localNpcs;
    }

    /**
     * Returns the backing set that will be used to get surrounding {@link Player}s or {@link Npc}s.
     */
    private <T extends MobileEntity> Set<T> getBackingSet(Player player) {
        return LunaConstants.STAGGERED_UPDATING ? new TreeSet<>(new RegionPriorityComparator(player)) : new HashSet<>();
    }

    /**
     * Gets the {@link Region}s surrounding {@code pos}.
     *
     * @param pos The {@link Position}.
     * @return The surrounding regions.
     */
    private List<Region> getSurroundingRegions(Position pos) {
        RegionCoordinates coordinates = RegionCoordinates.create(pos);

        int regionX = coordinates.getX();
        int regionY = coordinates.getY();

        List<Region> regions = new LinkedList<>();
        regions.add(getRegion(coordinates)); // Initial region.

        int x = pos.getX() % 32;
        int y = pos.getY() % 32;

        if (y == 15 || y == 16) {
            // Middle of region.

            if (x > 16) {

                // Middle-right part of region.
                regions.add(getRegion(regionX + 1, regionY));
            } else if (x < 15) {

                // Middle-left part of region.
                regions.add(getRegion(regionX - 1, regionY));
            }
        } else if (y > 16) {
            // Top part of region.

            if (x > 16) {

                // Top-right part of region.
                regions.add(getRegion(regionX, regionY + 1));
                regions.add(getRegion(regionX + 1, regionY));
                regions.add(getRegion(regionX + 1, regionY + 1));
            } else if (x < 15) {

                // Top-left part of region.
                regions.add(getRegion(regionX, regionY + 1));
                regions.add(getRegion(regionX - 1, regionY));
                regions.add(getRegion(regionX - 1, regionY + 1));
            } else if (x == 15 || x == 16) {

                // Top-middle part of region.
                regions.add(getRegion(regionX, regionY + 1));
            }
        } else if (y < 15) {
            // Bottom part of region.

            if (x > 16) {

                // Bottom-right part of region.
                regions.add(getRegion(regionX, regionY - 1));
                regions.add(getRegion(regionX + 1, regionY));
                regions.add(getRegion(regionX + 1, regionY - 1));
            } else if (x < 15) {

                // Bottom-left part of region.
                regions.add(getRegion(regionX, regionY - 1));
                regions.add(getRegion(regionX - 1, regionY));
                regions.add(getRegion(regionX - 1, regionY - 1));
            } else if (x == 15 || x == 16) {

                // Bottom-middle part of region.
                regions.add(getRegion(regionX, regionY - 1));
            }
        }
        return regions;
    }
}
