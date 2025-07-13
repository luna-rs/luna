package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * The {@link DynamicMapSpacePool} assigns empty space to dynamic maps so that they can be isolated from the main game
 * world and other map instances. It also reclaims empty space when an instance is no longer using it. This rotating
 * pool of instances ensures that
 * <li>All instances are tracked</li>
 * <li>Instances are never visible to one another</li>
 * <li>Map space for instances is always available</li>
 */
public class DynamicMapSpacePool {

    /**
     * The logger instance.
     */
    private static final Logger logger = LogManager.getLogger();

    /**
     * The context.
     */
    private final LunaContext context;

    /**
     * The rotating pool of empty map space.
     */
    private final Set<DynamicMapSpace> emptySpacePool = new LinkedHashSet<>();

    /**
     * A set of active instances with assigned space.
     */
    private final Set<DynamicMap> instances = new LinkedHashSet<>();

    /**
     * Creates a new {@link DynamicMapSpacePool}.
     *
     * @param context The context.
     */
    public DynamicMapSpacePool(LunaContext context) {
        this.context = context;
    }

    /**
     * Builds the rotating pool of {@link DynamicMapSpace} types. A huge portion of empty space is analyzed and
     * formatted into 128x128 areas in preparation to be assigned to a {@link DynamicMap}.
     * <p>
     * We do this on startup so we can request map space for instances instantly while the server is running.
     */
    public void buildEmptySpacePool() {
        if (emptySpacePool.isEmpty()) {

            // Loop through empty space.
            int lowerRegionBound = 25_000;
            int upperRegionBound = 42_800; // Increase this = Increase generated map spaces.
            int potentialSize = upperRegionBound - lowerRegionBound;
            Set<Region> emptyRegions = new LinkedHashSet<>(potentialSize);
            for (int regionId = lowerRegionBound; regionId < upperRegionBound; regionId++) {
                Region region = new Region(regionId);
                Position base = region.getAbsPosition();
                if (base.getX() >= 6400 && base.getY() >= 100 && base.getY() <= 5248) { // Must be within valid instance coordinates.
                    emptyRegions.add(region);
                }
            }

            // Build dynamic map spaces from empty regions.
            Set<Region> usedRegions = new HashSet<>();
            for (Region primary : emptyRegions) {
                DynamicMapSpace emptySpace = new DynamicMapSpace(primary);

                // Are all generated padding regions on empty space?
                if (!emptyRegions.containsAll(emptySpace.getAllPaddingRegions()) ||
                        // Have any regions in the space already been used?
                        usedRegions.contains(primary) ||
                        usedRegions.contains(emptySpace.getPaddingTop()) ||
                        usedRegions.contains(emptySpace.getPaddingRight()) ||
                        usedRegions.contains(emptySpace.getPaddingTopRight())) {
                    continue;
                }
                emptySpacePool.add(emptySpace); // Add the empty space.
                usedRegions.addAll(emptySpace.getAllRegions()); // Record that we've used these regions.
            }
            logger.debug("Created {} pooled dynamic map spaces.", box(emptySpacePool.size()));
        }
    }

    /**
     * Requests a {@link DynamicMapSpace} from the backing pool to be assigned to {@code map}.
     *
     * @param map The map tp request space for.
     */
    public void request(DynamicMap map) {
        if (map.getAssignedSpace() == null) {
            DynamicMapSpace foundSpace = null;
            Iterator<DynamicMapSpace> it = emptySpacePool.iterator();
            while (it.hasNext()) {
                DynamicMapSpace nextSpace = it.next();
                boolean visible = false;
                for (DynamicMap usedMap : instances) {
                    if (usedMap.getAssignedSpace().isNear(nextSpace)) {
                        // An instance in use is visible to the proposed empty space. Skip it.
                        visible = true;
                        break;
                    }
                }
                if (visible) {
                    // Try again with another piece of empty space.
                    continue;
                }
                foundSpace = nextSpace;
                it.remove();
                break;
            }
            checkState(foundSpace != null,
                    "No empty space left in the pool! Ensure all instances are deleted when no longer in use.");
            if (instances.add(map)) {
                map.setAssignedSpace(foundSpace);
            }
        }
    }

    /**
     * Releases the {@link DynamicMapSpace} held by {@code map} and returns it to the pool.
     *
     * @param map The map to release space for.
     */
    void release(DynamicMap map) {
        if (map.getAssignedSpace() != null && instances.remove(map)) {
            // Return the empty space back to the pool.
            DynamicMapSpace assignedSpace = map.getAssignedSpace();
            emptySpacePool.add(assignedSpace);
            map.setAssignedSpace(null);
        }
    }

    /**
     * @return An immutable set of all active instances.
     */
    public Set<DynamicMap> getInstances() {
        return Collections.unmodifiableSet(instances);
    }
}
