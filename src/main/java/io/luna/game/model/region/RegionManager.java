package io.luna.game.model.region;

import io.luna.LunaConstants;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.mob.Mob;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static io.luna.game.model.EntityConstants.VIEWING_DISTANCE;

/**
 * A model containing a collection of loaded map regions.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class RegionManager {

    /**
     * A concurrent map of loaded regions.
     */
    private final Map<RegionCoordinates, Region> regions = new ConcurrentHashMap<>();

    /**
     * Retrieves a region based on the argued region coordinates, constructing and loading a new
     * one if needed.
     *
     * @param coordinates The coordinates to construct a new region with.
     * @return The existing or newly loaded region.
     */
    public Region getRegion(RegionCoordinates coordinates) {
        return regions.computeIfAbsent(coordinates, Region::new);
    }

    /**
     * Returns a set of viewable players from the position of {@code player}, potentially ordered
     * using the region update comparator.
     *
     * @param player The player to compute viewable players for.
     * @return A set of viewable players.
     */
    public Set<Player> getViewablePlayers(Player player) {
        List<Region> viewableRegions = getViewableRegions(player.getPosition());
        Set<Player> viewablePlayers = computeSetImpl(player);

        for (Region region : viewableRegions) {
            Set<Player> regionPlayers = region.getAll(EntityType.PLAYER);
            for (Player inside : regionPlayers) {
                if (inside.isViewable(player)) {
                    viewablePlayers.add(inside);
                }
            }
        }
        return viewablePlayers;
    }

    /**
     * Returns a set of viewable npcs from the position of {@code player}, potentially ordered
     * using the region update comparator.
     *
     * @param player The player to compute viewable npcs for.
     * @return A set of viewable npcs.
     */
    public Set<Npc> getViewableNpcs(Player player) {
        List<Region> viewableRegions = getViewableRegions(player.getPosition());
        Set<Npc> viewableNpcs = computeSetImpl(player);

        for (Region region : viewableRegions) {
            Set<Npc> regionNpcs = region.getAll(EntityType.NPC);
            for (Npc inside : regionNpcs) {
                if (inside.isViewable(player)) {
                    viewableNpcs.add(inside);
                }
            }
        }
        return viewableNpcs;
    }

    /**
     * Returns the appropriate set to use when computing viewable mobs. Will either be a {@link TreeSet}
     * or {@link HashSet} depending on Luna constants settings.
     *
     * @param player The player to compute for.
     * @return The backing set that will be used.
     */
    private <T extends Mob> Set<T> computeSetImpl(Player player) {
        return LunaConstants.STAGGERED_UPDATING ?
                new TreeSet<>(new RegionUpdateComparator(player)) : new HashSet<>();
    }

    /**
     * Returns a list of viewable regions from {@code position}.
     *
     * @param position The position to get viewable regions from.
     * @return A list of viewable regions.
     */
    private List<Region> getViewableRegions(Position position) {
        RegionCoordinates coords = position.getRegionCoordinates(); // Current region's coordinates.
        int localX = coords.getLocalX(position), localY = coords.getLocalY(position); // Player's (x,y) in the region.

        boolean isEastViewable = localX + VIEWING_DISTANCE >= 63; // Is eastern region viewable?
        boolean isWestViewable = localX - VIEWING_DISTANCE <= 0; // Is western region viewable?
        boolean isNorthViewable = localY + VIEWING_DISTANCE >= 63; // Is northern region viewable?
        boolean isSouthViewable = localY - VIEWING_DISTANCE <= 0; // Is southern region viewable?

        List<Region> regions = new ArrayList<>(4); // A list to store viewable regions.
        Consumer<RegionCoordinates> addRegion = c -> regions.add(getRegion(c)); // Store a new region.

        addRegion.accept(coords); // Store current region.

        // First store regions for X-axis.
        if (isEastViewable) {
            addRegion.accept(coords.east()); // Store eastern region.
        } else if (isWestViewable) {
            addRegion.accept(coords.west()); // Store western region.
        }

        // Then, for the Y-axis.
        if (isNorthViewable) {
            addRegion.accept(coords.north()); // Store northern region.
        } else if (isSouthViewable) {
            addRegion.accept(coords.south()); // Store southern region.
        }

        // Finally, store all diagonal regions.
        if (isNorthViewable && isEastViewable) {
            addRegion.accept(coords.northEast()); // Store north-eastern region.
        } else if (isNorthViewable && isWestViewable) {
            addRegion.accept(coords.northWest()); // Store north-western region.
        } else if (isSouthViewable && isEastViewable) {
            addRegion.accept(coords.southEast()); // Store south-eastern region.
        } else if (isSouthViewable && isWestViewable) {
            addRegion.accept(coords.southWest()); // Store south-western region.
        }
        return regions;
    }
}
