package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.map.builder.DynamicMapBuilder;
import io.luna.game.model.map.builder.DynamicMapChunk;
import io.luna.game.model.map.builder.DynamicMapPalette;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.controller.ControllerKey;
import io.luna.game.model.object.GameObject;
import io.luna.game.task.Task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A dynamically constructed map; also known as an instance, somewhere in the rs2 world. These can be used for things
 * like minigames, private areas, random events, and player cutscenes.
 * <p>
 * Dynamic maps can be constructed simply by using specialized functions within {@link DynamicMapBuilder}, while
 * {@link DynamicMapPalette} allows for finer control over chunk placement in the palette.
 * <p>
 * All instances are assigned empty space somewhere in the world, which is reclaimed when the instance is no longer
 * needed. This ensures that instances are always isolated and that empty space is always available.
 *
 * @author lare96
 */
public final class DynamicMap {

    // Todo test test test
    // todo need a reliable way to translate positions from real world chunks to instanced ones

    /**
     * The world instance.
     */
    private final World world;

    /**
     * The base real chunk that will be used to translate coordinates from the real map to this instanced map.
     */
    private final Chunk baseChunk;

    /**
     * The palette of this map.
     */
    private final DynamicMapPalette palette;

    /**
     * The key of the controller for this instance.
     */
    private final ControllerKey<?> controllerKey;

    /**
     * The players within this instance.
     */
    private final Set<Player> players = new LinkedHashSet<>();

    /**
     * The empty space that this instance is assigned to.
     */
    private DynamicMapSpace assignedSpace;

    /**
     * Creates a new {@link DynamicMap}.
     */
    public DynamicMap(LunaContext context, Chunk baseChunk,
                      DynamicMapPalette palette, ControllerKey<? extends DynamicMapController> controllerKey) {
        this.baseChunk = baseChunk;
        this.palette = palette;
        this.controllerKey = controllerKey;
        world = context.getWorld();
    }

    /**
     * Creates this instance by requesting empty space, and transferring all map data over to the empty space.
     */
    public void create() {
        // Request empty space from the pool.
        world.getDynamicMapSpacePool().request(this);

        // Get the base chunks from the palette.
        Set<Chunk> realChunks = new HashSet<>();
        palette.forEach((x, y, z) -> {
            DynamicMapChunk mapChunk = palette.getChunk(x, y, z);
            if (mapChunk != null) {
                realChunks.add(mapChunk.getChunk());
            }
        });

        for (Chunk chunk : realChunks) {

            // Now add the static objects in the real map to our instanced map.
            ChunkRepository repository = world.getChunks().load(chunk);
            repository.getAll(EntityType.OBJECT).stream().map(it -> (GameObject) it).
                    filter(it -> !it.isDynamic()).forEach(it -> {
                        GameObject instanceObject = GameObject.createStatic(world.getContext(), it.getId(),
                                getInstancePosition(it.getPosition()), it.getObjectType(), it.getDirection());
                        world.getObjects().register(instanceObject);
                    });

            // Add collision data as well.
            ChunkRepository instancedRepository = world.getChunks().load(getInstancePosition(chunk.getAbsPosition()));
            for(int index = 0; index < repository.getMatrices().length; index++) {
                instancedRepository.getMatrices()[index].replace(repository.getMatrices()[index]);
            }
        }
    }

    /**
     * Attempts to add {@code plr} to this instance.
     *
     * @param plr The player to add.
     */
    public boolean add(Player plr) { // todo bad name
        if (players.add(plr) && assignedSpace != null) {
            plr.lock();
            plr.setDynamicMap(this);
            plr.move(assignedSpace.getPrimary().getAbsPosition());
            plr.setLastRegion(null);
          //  plr.                queue(new DynamicMapMessageWriter(this, ));

            world.schedule(new Task(1) {
                @Override
                protected void execute() {
                    plr.unlock();
                    plr.getControllers().register(controllerKey);
                    cancel();
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Attempts to remove {@code plr} from this instance.
     *
     * @param plr The player to remove.
     */
    public boolean remove(Player plr) {
        if (players.remove(plr)) {
            Position oldPosition = plr.getPosition();
            plr.setLastRegion(null);
            plr.getControllers().unregister(controllerKey);
            plr.setDynamicMap(null);
            plr.sendRegionUpdate(oldPosition);
            return true;
        }
        return false;
    }

    /**
     * Deletes this instance by forcing all players to leave, clearing the area of entities, and releasing the occupied
     * space back to the {@link DynamicMapSpacePool}.
     */
    public void delete() {
        // Force all players to leave.
        Set<Player> removePlayers = new HashSet<>(players);
        for (Player player : removePlayers) {
            remove(player);
        }

        // Clear all entities.
        Set<Chunk> clearChunks = new HashSet<>();
        for (Region region : assignedSpace.getAllRegions()) {
            int id = region.getId();
            clearChunks.addAll(DynamicMapPalette.getAllChunksInRegion(id));
        }

        List<Runnable> removalActions = new ArrayList<>();
        for (Chunk chunk : clearChunks) {
            ChunkRepository repository = world.getChunks().load(chunk);
            for (var next : repository.getAll().entrySet()) {
                EntityType type = next.getKey();
                Set<Entity> entities = next.getValue();
                for (Entity entity : entities) {
                    switch (type) {
                        case ITEM:
                            removalActions.add(() -> world.getItems().unregister((GroundItem) entity));
                            break;
                        case NPC:
                            removalActions.add(() -> world.getNpcs().remove((Npc) entity));
                            break;
                        case OBJECT:
                            removalActions.add(() -> world.getObjects().unregister((GameObject) entity));
                            break;
                    }
                }
            }
            removalActions.add(repository::clear);
        }

        // Finalize removals.
        for (Runnable action : removalActions) {
            action.run();
        }

        // Return empty space back to pool.
        world.getDynamicMapSpacePool().release(this);
    }

    /**
     * Retrieves the {@link Position} in this instance that mirrors the actual coordinate.
     *
     * @param actualPosition The real position to get the instance position of.
     * @return The instance position.
     */
    public Position getInstancePosition(Position actualPosition) {
        // TODO find chunk of actual, link it to the dynamic map chunk in the palette. then get the base chunk of the
        // instance coordinates with the same chunk.

        // Determine the deltas between a real arbitrary base position and the base display chunk. We can use
        // these deltas later to translate from our instance position the exact same way.
        Position basePosition = baseChunk.getAbsPosition();
        System.out.println("base " + basePosition);
        System.out.println("actual " + actualPosition);
        int deltaX = actualPosition.getX() - basePosition.getX();
        int deltaY = actualPosition.getY() - basePosition.getY();
        System.out.println("dx " + deltaX);
        System.out.println("dy " + deltaY);
        System.out.println("assigned abs " + assignedSpace.getPrimary().getAbsPosition());
        System.out.println();
        // The base position in our instance will always be the same spot as base display chunk. Therefore we can do a
        // 1:1 translation using the previous deltas.
        return assignedSpace.getPrimary().getAbsPosition().translate(deltaX, deltaY);
    }

    /**
     * Forwards to {@link DynamicMap#getInstancePosition(Position)} with a {@code z} value of 0.
     *
     * @param x The real {@code x}.
     * @param y The real {@code y}.
     * @return The instance position.
     */
    public Position getInstancePosition(int x, int y) {
        return getInstancePosition(x, y, 0);
    }

    /**
     * Forwards to {@link DynamicMap#getInstancePosition(Position)}.
     *
     * @param x The real {@code x}.
     * @param y The real {@code y}.
     * @param z The real {@code z}.
     * @return The instance position.
     */
    public Position getInstancePosition(int x, int y, int z) {
        return getInstancePosition(new Position(x, y, z));
    }

    /**
     * @return The empty space that this instance is assigned to.
     */
    public DynamicMapSpace getAssignedSpace() {
        return assignedSpace;
    }

    /**
     * Sets the empty space that this instance is assigned to.
     */
    void setAssignedSpace(DynamicMapSpace assignedSpace) {
        this.assignedSpace = assignedSpace;
    }

    /**
     * @return The base real chunk that will be used to translate coordinates from the real map to this instanced map.
     */
    public Chunk getBaseChunk() {
        return baseChunk;
    }

    /**
     * @return The palette of this map.
     */
    public DynamicMapPalette getPalette() {
        return palette;
    }

    /**
     * @return The key of the controller for this instance.
     */
    public ControllerKey<?> getControllerKey() {
        return controllerKey;
    }
}
