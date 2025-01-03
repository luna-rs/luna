package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
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
import io.luna.net.msg.out.DynamicMapMessageWriter;

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
        assignedSpace = world.getDynamicMapSpacePool().request();

        // Get the base chunks from the palette.
        Set<Chunk> realChunks = new HashSet<>();
        palette.forEach((x, y, z) -> {
            DynamicMapChunk mapChunk = palette.getChunk(x, y, z);
            if (mapChunk != null) {
                realChunks.add(mapChunk.getChunk());
            }
        });

        // Now add the static objects in the real map to our instanced map.
        for (Chunk chunk : realChunks) {
            ChunkRepository repository = world.getChunks().load(chunk);
            repository.getAll(EntityType.OBJECT).stream().map(it -> (GameObject) it).
                    filter(it -> !it.isDynamic()).forEach(it -> {
                        GameObject instanceObject = GameObject.createStatic(world.getContext(), it.getId(),
                                getInstancePosition(it.getPosition()), it.getObjectType(), it.getDirection());
                        world.getObjects().register(instanceObject);
                    });
        }
    }

    /**
     * Attempts to add {@code plr} to this instance.
     *
     * @param plr The player to add.
     */
    public boolean join(Player plr) {
        if (players.add(plr)) {
            plr.setDynamicMap(this);
            plr.getControllers().register(controllerKey);
            sendUpdate(plr);
            return true;
        }
        return false;
    }

    /**
     * Attempts to remove {@code plr} from this instance.
     *
     * @param plr The player to remove.
     */
    public boolean leave(Player plr) {
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
     * Deletes this instance by forcing all players to leave, and clearing the area of entities.
     */
    public void delete() {
        // Force all players to leave.
        Set<Player> removePlayers = new HashSet<>(players);
        for (Player player : removePlayers) {
            leave(player);
        }

        // Clear all entities.
        int mainRegionId = assignedSpace.getMain().getId();
        int paddingRegionId = assignedSpace.getPadding().getId();

        Set<Chunk> clearChunks = new HashSet<>();
        clearChunks.addAll(DynamicMapPalette.getAllChunksInRegion(mainRegionId));
        clearChunks.addAll(DynamicMapPalette.getAllChunksInRegion(paddingRegionId));

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
     * Sends the {@link DynamicMapMessageWriter} that will display this instance for {@code player}.
     *
     * @param player The player.
     */
    public void sendUpdate(Player player) {
        player.queue(new DynamicMapMessageWriter(palette));
    }

    /**
     * Retrieves the {@link Position} in this instance that mirrors the actual coordinate.
     *
     * @param actualPosition The real position to get the instance position of.
     * @return The instance position.
     */
    public Position getInstancePosition(Position actualPosition) {
        // Determine the deltas between a real arbitrary base position and the base display chunk. We can use
        // these deltas later to translate from our instance position the exact same way.
        Position basePosition = baseChunk.getAbsPosition();
        int deltaX = actualPosition.getX() - basePosition.getX();
        int deltaY = actualPosition.getY() - basePosition.getY();

        // The base position in our instance will always be the same spot as base display chunk. Therefore we can do a
        // 1:1 translation using the previous deltas.
        return assignedSpace.getMain().getAbsPosition().translate(deltaX, deltaY);
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
