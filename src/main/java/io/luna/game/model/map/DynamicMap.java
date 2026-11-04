package io.luna.game.model.map;

import io.luna.LunaContext;
import io.luna.game.cache.map.MapIndexTable;
import io.luna.game.cache.map.MapObject;
import io.luna.game.cache.map.MapTile;
import io.luna.game.cache.map.MapTileGrid;
import io.luna.game.model.Entity;
import io.luna.game.model.EntityType;
import io.luna.game.model.Position;
import io.luna.game.model.Region;
import io.luna.game.model.World;
import io.luna.game.model.chunk.Chunk;
import io.luna.game.model.chunk.ChunkRepository;
import io.luna.game.model.collision.CollisionFlag;
import io.luna.game.model.collision.CollisionUpdate;
import io.luna.game.model.collision.CollisionUpdateType;
import io.luna.game.model.def.GameObjectDefinition;
import io.luna.game.model.item.GroundItem;
import io.luna.game.model.map.builder.DynamicMapBuilder;
import io.luna.game.model.map.builder.DynamicMapChunk;
import io.luna.game.model.map.builder.DynamicMapPalette;
import io.luna.game.model.mob.Npc;
import io.luna.game.model.mob.Player;
import io.luna.game.model.object.GameObject;
import io.luna.game.model.object.ObjectDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Represents a dynamically instanced map (a.k.a. "private area") in the RS2 world.
 *
 * <p>This is the core class for all map-based instancing logic—used in minigames, random events, private
 * encounters, and more. Each instance is constructed from a {@link DynamicMapPalette} of chunk layouts, mapped
 * into isolated "empty" world space via {@link DynamicMapSpacePool}. Static world data such as objects and collision
 * matrices are mirrored into the instance.</p>
 *
 * <p>To construct an instance, use {@link DynamicMapBuilder}. For direct control of palette mapping, see
 * {@link DynamicMapPalette} and {@link DynamicMapChunk}.</p>
 *
 * @author lare96
 */
public final class DynamicMap {

    /**
     * The world instance.
     */
    private final World world;

    /**
     * The original builder anchor. Coordinate mapping uses the palette slots, not an offset from this chunk.
     */
    private final Chunk baseChunk;

    /**
     * The palette of this map.
     */
    private final DynamicMapPalette palette;

    /**
     * The players within this instance.
     */
    private final Set<Player> players = new LinkedHashSet<>();

    /**
     * Creates a separate controller for each entering player.
     */
    private final Function<Player, DynamicMapController> controller;

    /**
     * Controllers belonging to current members, retained until their exit callback completes.
     */
    private final Map<Player, DynamicMapController> controllers = new HashMap<>();

    /**
     * True after terrain and objects have been installed successfully.
     */
    private boolean created;

    /**
     * The empty space that this instance is assigned to.
     */
    private DynamicMapSpace assignedSpace;

    /**
     * Creates a new {@link DynamicMap}.
     */
    public DynamicMap(LunaContext context, Chunk baseChunk,
                      DynamicMapPalette palette, Supplier<DynamicMapController> controller) {
        this(context, baseChunk, palette, player -> controller.get());
    }

    /**
     * Creates an unallocated instance with a snapshot of the supplied palette.
     *
     * @param context The game context.
     * @param baseChunk The source anchor retained for builder compatibility.
     * @param palette The layout to copy.
     * @param controller The per-player controller factory.
     */
    public DynamicMap(LunaContext context, Chunk baseChunk, DynamicMapPalette palette,
                      Function<Player, DynamicMapController> controller) {
        this.baseChunk = Objects.requireNonNull(baseChunk);
        this.palette = Objects.requireNonNull(palette).copy();
        this.controller = Objects.requireNonNull(controller);
        world = context.getWorld();
    }

    /**
     * Creates this instance by requesting empty space, and transferring all map data over to the empty space.
     */
    public void create() {
        checkState(assignedSpace == null, "This map already has assigned space.");
        world.getDynamicMapSpacePool().request(this);
        try {
            MapIndexTable table = world.getContext().getCache().getMapIndexTable();
            CollisionUpdate.Builder terrain = new CollisionUpdate.Builder();
            terrain.type(CollisionUpdateType.ADDING);
            terrain.mapCoordinates();
            // Prepare destination planes before applying any walls that cross chunk boundaries.
            palette.forEach((x, y, z) -> {
                DynamicMapChunk source = palette.getChunk(x, y, z);
                if (source != null) {
                    world.getChunks().load(slotPosition(x, y, z)).prepareInstancePlane(z);
                }
            });
            palette.forEach((x, y, z) -> {
                DynamicMapChunk source = palette.getChunk(x, y, z);
                if (source == null) return;
                var index = table.getIndexTable().get(source.getChunk().getAbsPosition().getRegion());
                checkState(index != null, "Palette source has no cache map: %s", source.getChunk());
                MapTileGrid grid = table.getTileSet().getGrid(index);
                for (int dx = 0; dx < 8; dx++) {
                    for (int dy = 0; dy < 8; dy++) {
                        Position real = source.getChunk().getAbsPosition().translate(dx, dy).setZ(source.getPlane());
                        MapTile tile = grid.getTile(real);
                        Position destination = transform(x, y, z, source, real, 1, 1);
                        if (z == 1 && tile.isBridge()) world.getCollisionManager().markBridged(destination);
                        if (tile.isBlocked()) terrain.flag(destination, CollisionFlag.BLOCK_WALK);
                        if (tile.isRoof()) terrain.flag(destination, CollisionFlag.ROOF);
                    }
                }
            });
            world.getCollisionManager().apply(terrain.build(), false);

            // Use immutable cache placements, not live source objects that may have been opened, removed or replaced.
            Map<Chunk, List<int[]>> slots = new HashMap<>();
            palette.forEach((x, y, z) -> {
                DynamicMapChunk source = palette.getChunk(x, y, z);
                if (source != null) slots.computeIfAbsent(source.getChunk(), key -> new ArrayList<>()).add(new int[]{x, y, z});
            });
            for (MapObject object : table.getObjectSet()) {
                List<int[]> matches = slots.get(object.getPosition().getChunk());
                if (matches == null) continue;
                for (int[] slot : matches) {
                    DynamicMapChunk source = palette.getChunk(slot[0], slot[1], slot[2]);
                    if (source.getPlane() != object.getPosition().getZ()) continue;
                    GameObjectDefinition definition = GameObjectDefinition.ALL.retrieve(object.getObjectId());
                    boolean turned = (object.getRotation().getId() & 1) != 0;
                    int width = turned ? definition.getSizeY() : definition.getSizeX();
                    int length = turned ? definition.getSizeX() : definition.getSizeY();
                    Position destination = transform(slot[0], slot[1], slot[2], source, object.getPosition(), width, length);
                    // Rotated multi-tile anchors may extend into padding; never place entities beyond owned space.
                    if (!assignedSpace.contains(destination)) continue;
                    ObjectDirection direction = ObjectDirection.ALL.get((object.getRotation().getId() + source.getRotation()) & 3);
                    world.getObjects().register(GameObject.createStatic(world.getContext(), object.getObjectId(),
                            destination, object.getType(), direction));
                }
            }
            for (Region region : assignedSpace.getAllRegions()) {
                for (Chunk chunk : DynamicMapPalette.getAllChunksInRegion(region.getId())) {
                    world.getChunks().load(chunk).snapshotCollisionMap();
                }
            }
            created = true;
        } catch (RuntimeException failure) {
            delete();
            throw failure;
        }
    }

    /**
     * Enters at the center of destination slot (6, 6, 0). Sparse palettes must map this slot or use
     * {@link #add(Player, Position)} with an explicit mapped destination.
     *
     * @param plr The player to add.
     * @return Whether the player joined.
     */
    public boolean add(Player plr) {
        return add(plr, slotPosition(6, 6, 0).translate(4, 4));
    }

    /**
     * Enters at an explicit instance coordinate, allowing sparse palettes to choose a populated spawn.
     * The controller is constructed before membership changes and receives its enter hook after placement.
     *
     * @param plr The entering player.
     * @param destination The already-mapped destination inside this instance.
     * @return Whether the player joined.
     */
    public boolean add(Player plr, Position destination) {
        checkState(created, "Create the map before adding players.");
        checkArgument(contains(destination), "The entry tile is not mapped by this palette.");
        if (players.contains(plr)) return false;
        DynamicMapController next = Objects.requireNonNull(controller.apply(plr), "controller");
        next.attach(this, plr);
        if (plr.getDynamicMap() != null) plr.getDynamicMap().remove(plr);
        // Retire the previous activity before moving; its movement hook must not redirect the new entry.
        plr.getControllers().unregister();
        players.add(plr);
        controllers.put(plr, next);
        plr.setDynamicMap(this);
        plr.move(destination);
        plr.setLastRegion(null);
        plr.getControllers().register(next);
        return true;
    }

    /**
     * Attempts to remove {@code plr} from this instance.
     *
     * @param plr The player to remove.
     * @return Whether the player was a member and exited through their controller.
     */
    public boolean remove(Player plr) {
        if (!players.contains(plr)) return false;
        DynamicMapController current = controllers.get(plr);
        Position exit = Objects.requireNonNull(current.exit(plr), "Controller exit destination");
        checkArgument(!assignedSpace.contains(exit), "The exit destination must leave this instance.");
        players.remove(plr);
        controllers.remove(plr);
        plr.setDynamicMap(null);
        plr.setLastRegion(null);
        if (plr.getControllers().getPrimary() == current) plr.getControllers().unregister();
        plr.move(exit);
        return true;
    }

    /**
     * Obtains the controller's separate recovery destination without executing its exit hook.
     *
     * @param player The current instance member being saved.
     * @return The real-world coordinate to persist for recovery after a restart.
     */
    public Position getRecoveryPosition(Player player) {
        DynamicMapController current = controllers.get(player);
        checkState(current != null, "The player is not a member of this instance.");
        Position destination = Objects.requireNonNull(current.recovery(player), "Controller recovery destination");
        checkArgument(!assignedSpace.contains(destination), "Recovery must leave this instance's reserved space.");
        return destination;
    }

    /**
     * Deletes this instance by forcing all players to leave, clearing the area of entities, and releasing the occupied
     * space back to the {@link DynamicMapSpacePool}.
     */
    public void delete() {
        if (assignedSpace == null) return;
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
        }

        // Finalize removals.
        for (Runnable action : removalActions) {
            action.run();
        }

        // Clear only after all entity removals, since walls and footprints may span neighbouring chunks.
        for (Chunk chunk : clearChunks) {
            world.getChunks().load(chunk).clearInstance();
            world.getCollisionManager().clearInstanceChunk(chunk);
        }

        // Return empty space back to pool.
        created = false;
        world.getDynamicMapSpacePool().release(this);
    }

    /**
     * Retrieves the first instance position matching a real coordinate. Matching slots are ordered by destination
     * plane, then X slot, then Y slot. Use {@link #getInstancePositions(Position)} when copies are meaningful.
     *
     * @param actualPosition The real position to get the instance position of.
     * @return The instance position.
     */
    public Position getInstancePosition(Position actualPosition) {
        List<Position> matches = getInstancePositions(actualPosition);
        checkArgument(!matches.isEmpty(), "The source position is not present in this palette: %s", actualPosition);
        return matches.get(0);
    }

    /**
     * Maps a real coordinate to every matching palette slot, including repeated chunks and plane remapping.
     * Results are ordered by destination plane, then X slot, then Y slot. Local tile offsets rotate with the chunk.
     *
     * @param actualPosition The real world tile and source plane.
     * @return All matching instance coordinates, or an empty list when the tile is not mapped.
     * @throws IllegalStateException If the map has not been allocated.
     */
    public List<Position> getInstancePositions(Position actualPosition) {
        checkState(assignedSpace != null, "The map has no assigned space.");
        Objects.requireNonNull(actualPosition, "actualPosition");
        List<Position> matches = new ArrayList<>();
        palette.forEach((x, y, z) -> {
            DynamicMapChunk source = palette.getChunk(x, y, z);
            if (source != null && source.getPlane() == actualPosition.getZ() &&
                    source.getChunk().equals(actualPosition.getChunk())) {
                matches.add(transform(x, y, z, source, actualPosition, 1, 1));
            }
        });
        return List.copyOf(matches);
    }

    /**
     * Tests mapped destination tiles rather than the larger reserved padding area.
     *
     * @param position The instance coordinate to test.
     * @return Whether this palette supplies the destination chunk and plane.
     */
    public boolean contains(Position position) {
        if (assignedSpace == null || position.getZ() < 0 || position.getZ() > 3) return false;
        Position base = assignedSpace.abs();
        int x = position.getX() - base.getX(), y = position.getY() - base.getY();
        return x >= 0 && y >= 0 && x < 104 && y < 104 && palette.getChunk(x / 8, y / 8, position.getZ()) != null;
    }

    /**
     * Maps an instance coordinate back to the real tile it was copied from, the reverse of
     * {@link #getInstancePositions(Position)}.
     *
     * @param position The instance coordinate.
     * @return The real tile and source plane, or {@code null} when this palette doesn't map the tile.
     */
    public Position getRealPosition(Position position) {
        if (!contains(position)) return null;
        Position base = assignedSpace.abs();
        int x = position.getX() - base.getX(), y = position.getY() - base.getY();
        DynamicMapChunk source = palette.getChunk(x / 8, y / 8, position.getZ());
        int localX = x & 7, localY = y & 7;
        int realX = switch (source.getRotation()) {
            case 1 -> 7 - localY;
            case 2 -> 7 - localX;
            case 3 -> localY;
            default -> localX;
        };
        int realY = switch (source.getRotation()) {
            case 1 -> localX;
            case 2 -> 7 - localY;
            case 3 -> 7 - localX;
            default -> localY;
        };
        return source.getChunk().getAbsPosition().translate(realX, realY).setZ(source.getPlane());
    }

    /**
     * Locates the absolute origin of one destination palette slot.
     */
    private Position slotPosition(int x, int y, int z) {
        checkState(assignedSpace != null, "The map has no assigned space.");
        return assignedSpace.abs().translate(x * 8, y * 8).setZ(z);
    }

    /**
     * Maps an oriented source footprint to its rotated destination anchor.
     */
    private Position transform(int x, int y, int z, DynamicMapChunk source, Position real, int width, int length) {
        int localX = real.getX() & 7, localY = real.getY() & 7;
        return slotPosition(x, y, z).translate(source.rotateX(localX, localY, width, length),
                source.rotateY(localX, localY, width, length));
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
     * @return The original builder anchor. Coordinate mapping uses the palette slots, not an offset from this chunk.
     */
    public Chunk getBaseChunk() {
        return baseChunk;
    }

    /**
     * @return The palette of this map.
     */
    public DynamicMapPalette getPalette() {
        return palette.copy();
    }

}
