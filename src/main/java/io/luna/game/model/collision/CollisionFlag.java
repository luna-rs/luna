package io.luna.game.model.collision;

import io.luna.game.model.EntityType;

/**
 * The collision flags stored in a {@link CollisionMatrix}, one 32-bit mask per tile.
 * <p>
 * The layout is identical to the {@code CollisionFlag} object used by rsmod's routefinder, so that its pathfinder can read
 * Luna's collision data directly. Wall flags describe which sides of a tile are walled off, {@link #LOC} marks a tile
 * occupied by a solid object, and the {@code *_PROJ_BLOCKER} flags are the equivalents that also stop projectiles.
 * </p>
 * <p>
 * A wall flag set on a tile blocks entering that tile from the flag's direction. For example, {@link #WALL_SOUTH} on a tile
 * blocks a mob walking north into it from the tile below.
 * </p>
 * <p>
 * The flag layout is derived from rsmod's routefinder, which is licensed under the ISC License, Copyright (c) 2025
 * RS Mod. See the third party notices in LICENSE.txt.
 * </p>
 *
 * @author Major
 * @author hydrozoa
 */
public final class CollisionFlag {

    /**
     * The wall north west flag.
     */
    public static final int WALL_NORTH_WEST = 0x1;

    /**
     * The wall north flag.
     */
    public static final int WALL_NORTH = 0x2;

    /**
     * The wall north east flag.
     */
    public static final int WALL_NORTH_EAST = 0x4;

    /**
     * The wall east flag.
     */
    public static final int WALL_EAST = 0x8;

    /**
     * The wall south east flag.
     */
    public static final int WALL_SOUTH_EAST = 0x10;

    /**
     * The wall south flag.
     */
    public static final int WALL_SOUTH = 0x20;

    /**
     * The wall south west flag.
     */
    public static final int WALL_SOUTH_WEST = 0x40;

    /**
     * The wall west flag.
     */
    public static final int WALL_WEST = 0x80;

    /**
     * The flag for a tile occupied by a solid object.
     */
    public static final int LOC = 0x100;

    /**
     * The wall north west flag, for walls that also block projectiles.
     */
    public static final int WALL_NORTH_WEST_PROJ_BLOCKER = 0x200;

    /**
     * The wall north flag, for walls that also block projectiles.
     */
    public static final int WALL_NORTH_PROJ_BLOCKER = 0x400;

    /**
     * The wall north east flag, for walls that also block projectiles.
     */
    public static final int WALL_NORTH_EAST_PROJ_BLOCKER = 0x800;

    /**
     * The wall east flag, for walls that also block projectiles.
     */
    public static final int WALL_EAST_PROJ_BLOCKER = 0x1000;

    /**
     * The wall south east flag, for walls that also block projectiles.
     */
    public static final int WALL_SOUTH_EAST_PROJ_BLOCKER = 0x2000;

    /**
     * The wall south flag, for walls that also block projectiles.
     */
    public static final int WALL_SOUTH_PROJ_BLOCKER = 0x4000;

    /**
     * The wall south west flag, for walls that also block projectiles.
     */
    public static final int WALL_SOUTH_WEST_PROJ_BLOCKER = 0x8000;

    /**
     * The wall west flag, for walls that also block projectiles.
     */
    public static final int WALL_WEST_PROJ_BLOCKER = 0x10000;

    /**
     * The flag for a tile occupied by a solid object that also blocks projectiles.
     */
    public static final int LOC_PROJ_BLOCKER = 0x20000;

    /**
     * The flag for a tile occupied by a solid ground decoration.
     */
    public static final int GROUND_DECOR = 0x40000;

    /**
     * The flag for a tile occupied by an NPC.
     */
    public static final int BLOCK_NPCS = 0x80000;

    /**
     * The flag for a tile occupied by a player.
     */
    public static final int BLOCK_PLAYERS = 0x100000;

    /**
     * The flag for a tile that is blocked terrain (solid ground or water).
     */
    public static final int BLOCK_WALK = 0x200000;

    /**
     * The wall north west flag, for objects that break route finding.
     */
    public static final int WALL_NORTH_WEST_ROUTE_BLOCKER = 0x400000;

    /**
     * The wall north flag, for objects that break route finding.
     */
    public static final int WALL_NORTH_ROUTE_BLOCKER = 0x800000;

    /**
     * The wall north east flag, for objects that break route finding.
     */
    public static final int WALL_NORTH_EAST_ROUTE_BLOCKER = 0x1000000;

    /**
     * The wall east flag, for objects that break route finding.
     */
    public static final int WALL_EAST_ROUTE_BLOCKER = 0x2000000;

    /**
     * The wall south east flag, for objects that break route finding.
     */
    public static final int WALL_SOUTH_EAST_ROUTE_BLOCKER = 0x4000000;

    /**
     * The wall south flag, for objects that break route finding.
     */
    public static final int WALL_SOUTH_ROUTE_BLOCKER = 0x8000000;

    /**
     * The wall south west flag, for objects that break route finding.
     */
    public static final int WALL_SOUTH_WEST_ROUTE_BLOCKER = 0x10000000;

    /**
     * The wall west flag, for objects that break route finding.
     */
    public static final int WALL_WEST_ROUTE_BLOCKER = 0x20000000;

    /**
     * The flag for a tile occupied by a solid object that breaks route finding.
     */
    public static final int LOC_ROUTE_BLOCKER = 0x40000000;

    /**
     * The flag for a tile that is covered by a roof, which is how indoor tiles are told apart from outdoor ones.
     */
    public static final int ROOF = 0x80000000;

    /**
     * The flags for a tile that can't be walked on regardless of the side it's entered from.
     */
    public static final int FLOOR_BLOCKED = BLOCK_WALK | GROUND_DECOR;

    /**
     * The flags that stop a mob from moving north into a tile.
     */
    public static final int BLOCK_NORTH = WALL_SOUTH | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving east into a tile.
     */
    public static final int BLOCK_EAST = WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving south into a tile.
     */
    public static final int BLOCK_SOUTH = WALL_NORTH | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving west into a tile.
     */
    public static final int BLOCK_WEST = WALL_EAST | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving north west into a tile.
     */
    public static final int BLOCK_NORTH_WEST = WALL_EAST | WALL_SOUTH_EAST | WALL_SOUTH | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving north east into a tile.
     */
    public static final int BLOCK_NORTH_EAST = WALL_SOUTH | WALL_SOUTH_WEST | WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving south west into a tile.
     */
    public static final int BLOCK_SOUTH_WEST = WALL_NORTH | WALL_NORTH_EAST | WALL_EAST | LOC | FLOOR_BLOCKED;

    /**
     * The flags that stop a mob from moving south east into a tile.
     */
    public static final int BLOCK_SOUTH_EAST = WALL_NORTH_WEST | WALL_NORTH | WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The flags for the middle tiles of the leading edge of a large mob that is moving west. A tile there must be open
     * on every side but the one the mob is leaving.
     */
    public static final int BLOCK_NORTH_AND_SOUTH_EAST =
            WALL_NORTH | WALL_NORTH_EAST | WALL_EAST | WALL_SOUTH_EAST | WALL_SOUTH | LOC | FLOOR_BLOCKED;

    /**
     * The flags for the middle tiles of the leading edge of a large mob that is moving east.
     */
    public static final int BLOCK_NORTH_AND_SOUTH_WEST =
            WALL_NORTH_WEST | WALL_NORTH | WALL_SOUTH | WALL_SOUTH_WEST | WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The flags for the middle tiles of the leading edge of a large mob that is moving south.
     */
    public static final int BLOCK_NORTH_EAST_AND_WEST =
            WALL_NORTH_WEST | WALL_NORTH | WALL_NORTH_EAST | WALL_EAST | WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The flags for the middle tiles of the leading edge of a large mob that is moving north.
     */
    public static final int BLOCK_SOUTH_EAST_AND_WEST =
            WALL_EAST | WALL_SOUTH_EAST | WALL_SOUTH | WALL_SOUTH_WEST | WALL_WEST | LOC | FLOOR_BLOCKED;

    /**
     * The wall flags indexed by {@link io.luna.game.model.Direction#getId()}, in the order north west, north, north
     * east, west, east, south west, south, south east. Must not be modified.
     */
    static final int[] WALLS = {
            WALL_NORTH_WEST,
            WALL_NORTH,
            WALL_NORTH_EAST,
            WALL_WEST,
            WALL_EAST,
            WALL_SOUTH_WEST,
            WALL_SOUTH,
            WALL_SOUTH_EAST
    };

    /**
     * The projectile-blocking wall flags indexed by {@link io.luna.game.model.Direction#getId()}, in the same order as
     * {@link #WALLS}. Must not be modified.
     */
    static final int[] WALL_PROJ_BLOCKERS = {
            WALL_NORTH_WEST_PROJ_BLOCKER,
            WALL_NORTH_PROJ_BLOCKER,
            WALL_NORTH_EAST_PROJ_BLOCKER,
            WALL_WEST_PROJ_BLOCKER,
            WALL_EAST_PROJ_BLOCKER,
            WALL_SOUTH_WEST_PROJ_BLOCKER,
            WALL_SOUTH_PROJ_BLOCKER,
            WALL_SOUTH_EAST_PROJ_BLOCKER
    };

    /**
     * Every flag that stops projectiles.
     */
    static final int PROJECTILE_BLOCKERS = WALL_NORTH_WEST_PROJ_BLOCKER | WALL_NORTH_PROJ_BLOCKER |
            WALL_NORTH_EAST_PROJ_BLOCKER | WALL_EAST_PROJ_BLOCKER | WALL_SOUTH_EAST_PROJ_BLOCKER |
            WALL_SOUTH_PROJ_BLOCKER | WALL_SOUTH_WEST_PROJ_BLOCKER | WALL_WEST_PROJ_BLOCKER | LOC_PROJ_BLOCKER;

    /**
     * The flags that stop a mob from entering a tile, indexed by the {@link io.luna.game.model.Direction#getId()} it
     * is moving in. Must not be modified.
     */
    private static final int[] MOB_BLOCKS = {
            BLOCK_NORTH_WEST,
            BLOCK_NORTH,
            BLOCK_NORTH_EAST,
            BLOCK_WEST,
            BLOCK_EAST,
            BLOCK_SOUTH_WEST,
            BLOCK_SOUTH,
            BLOCK_SOUTH_EAST
    };

    /**
     * The flags that stop an NPC from entering a tile, on top of {@link #MOB_BLOCKS}. NPCs are stopped by other NPCs
     * and by players, but nothing is stopped by an NPC other than another NPC.
     */
    private static final int NPC_BLOCKS = BLOCK_NPCS | BLOCK_PLAYERS;

    /**
     * The flags that stop a projectile from entering a tile, indexed like {@link #MOB_BLOCKS}. These are the walls and
     * objects of {@link #MOB_BLOCKS} moved onto their projectile-blocking counterparts, which are 9 bits higher. The
     * floor flags are left out, so projectiles fly over water. Must not be modified.
     */
    private static final int[] PROJECTILE_BLOCKS = new int[MOB_BLOCKS.length];

    static {
        int wallsAndLoc = 0x1FF;
        for (int i = 0; i < MOB_BLOCKS.length; i++) {
            PROJECTILE_BLOCKS[i] = (MOB_BLOCKS[i] & wallsAndLoc) << 9;
        }
    }

    /**
     * Returns the flags that decide whether the specified {@link EntityType} can enter a tile while moving in a
     * direction.
     * <p>
     * Projectiles are stopped by the projectile-blocking flags; everything else is stopped by the plain wall flags
     * along with objects and blocked floors. NPCs are additionally stopped by other NPCs and players.
     *
     * @param type The entity type.
     * @param directionId The {@link io.luna.game.model.Direction#getId()} of the movement.
     * @return The flags that block that movement.
     */
    static int blocks(EntityType type, int directionId) {
        switch (type) {
            case PROJECTILE:
                return PROJECTILE_BLOCKS[directionId];
            case NPC:
                return MOB_BLOCKS[directionId] | NPC_BLOCKS;
            default:
                return MOB_BLOCKS[directionId];
        }
    }

    /**
     * Returns every flag that makes a tile unusable for the specified {@link EntityType} from some direction.
     *
     * @param type The entity type.
     * @return The union of the flags that block it.
     */
    static int anyBlock(EntityType type) {
        int flags = 0;
        for (int i = 0; i < MOB_BLOCKS.length; i++) {
            flags |= blocks(type, i);
        }
        return flags;
    }

    /**
     * Not instantiable.
     */
    private CollisionFlag() {
    }
}
