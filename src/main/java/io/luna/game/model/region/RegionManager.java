package io.luna.game.model.region;

import io.luna.LunaConstants;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A model that manages regions occupied by entities.
 *
 * @author lare96 <http://github.org/lare96>
 * @author Graham
 */
public final class RegionManager {

    /**
     * A map of regions currently or previously occupied by entities.
     */
    private final Map<RegionCoordinates, Region> regions = new ConcurrentHashMap<>();

    /**
     * Returns or constructs a region based on the argued coordinates.
     */
    public Region getRegion(int x, int y) {
        return getRegion(new RegionCoordinates(x, y));
    }

    /**
     * Returns or constructs a region based on the argued position.
     */
    public Region getRegion(Position pos) {
        return getRegion(RegionCoordinates.create(pos));
    }

    /**
     * Returns or constructs a region based on the argued region coordinates.
     */
    public Region getRegion(RegionCoordinates coordinates) {
        return regions.computeIfAbsent(coordinates, Region::new);
    }

    /**
     * Determines if a cached region exists for a position.
     */
    public boolean exists(Position pos) {
        return regions.containsKey(RegionCoordinates.create(pos));
    }

    /**
     * Computes a set of entities within a region viewable from the argued position.
     */
    @SuppressWarnings("unchecked")
    public <E extends Entity> Set<E> getViewableEntities(Position position, EntityType type) {
        List<Region> allRegions = getSurroundingRegions(position);
        Set<E> entities = new HashSet<>();

        for (Region region : allRegions) {
            List<E> regionEntities = region.getEntities(type);

            for (Entity inRegion : regionEntities) {
                if (inRegion.getPosition().isViewable(position)) {
                    entities.add((E) inRegion);
                }
            }
        }
        return entities;
    }

    /**
     * Computes a set of viewable players, potentially ordered using the region update comparator.
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
     * Computes a set of viewable NPCs, potentially ordered using the region update comparator.
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
     * Constructs and returns the backing set used to hold mobs.
     */
    private <T extends Mob> Set<T> getBackingSet(Player player) {
        return LunaConstants.STAGGERED_UPDATING ? new TreeSet<>(new RegionUpdateComparator(player)) :
            new HashSet<>();
    }

    /**
     * Computes a list of regions surrounding a position.
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
