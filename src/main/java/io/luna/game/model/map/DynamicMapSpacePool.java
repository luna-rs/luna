package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.logging.log4j.util.Unbox.box;

/**
 * The {@link DynamicMapSpacePool} assigns empty space to dynamic maps so that they can be isolated from the main game
 * world and other map instances. It also reclaims empty space when an instance is no longer using it. This rotating pool of instances ensures
 * that
 * - every instance and all available space is tracked
 * - an instance is never visible to another
 * - an instance is always available when needed
 * - empty space is reclaimed when an instance is done
 */
public class DynamicMapSpacePool {
    // TODO finish documentation, more testing
    private static final Logger logger = LogManager.getLogger();
    private final Set<DynamicMapSpace> emptySpacePool = new HashSet<>();
    private final List<DynamicMap> instances = new ArrayList<>();

    private final LunaContext context;

    public DynamicMapSpacePool(LunaContext context) {
        this.context = context;
    }

    public void buildEmptySpacePool() {
        if (emptySpacePool.isEmpty()) {
            // Loop through all empty space.
            Set<Integer> emptyRegionIds = new LinkedHashSet<>(10_000);
            for (int regionId = 25_000; regionId < 34_097; regionId++) {
                Region region = new Region(regionId);
                Position base = region.getAbsPosition();
                if (base.getX() >= 6400 && base.getY() <= 5248) {
                    emptyRegionIds.add(regionId);
                }
            }

            // Build dynamic map spaces from empty regions.
            Set<Integer> usedRegionIds = new HashSet<>();
            for (int regionId : emptyRegionIds) {
                Region main = new Region(regionId);
                DynamicMapSpace emptySpace = new DynamicMapSpace(main);
                int paddingRegionId = emptySpace.getPadding().getId();
                if (!emptyRegionIds.contains(paddingRegionId) ||
                        usedRegionIds.contains(main.getId()) ||
                        usedRegionIds.contains(paddingRegionId)) {
                    continue;
                }
                emptySpacePool.add(emptySpace);
                usedRegionIds.add(main.getId());
                usedRegionIds.add(paddingRegionId);
            }
            logger.info("Created {} pooled dynamic map spaces.", box(emptySpacePool.size()));
        }
    }

   DynamicMapSpace request() {
        DynamicMapSpace foundSpace = null;
        Iterator<DynamicMapSpace> it = emptySpacePool.iterator();
        while (it.hasNext()) {
            DynamicMapSpace nextSpace = it.next();
            boolean visible = false;
            for (DynamicMap usedMap : instances) {
                if (usedMap.getAssignedSpace().isVisibleTo(nextSpace)) {
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
        return foundSpace;
    }

    void release(DynamicMap map) {
        if (instances.remove(map)) {
            // Return the empty space back to the pool.
            DynamicMapSpace assignedSpace = map.getAssignedSpace();
            emptySpacePool.add(assignedSpace);
        }
    }

    public List<DynamicMap> getInstances() {
        return Collections.unmodifiableList(instances);
    }
}
