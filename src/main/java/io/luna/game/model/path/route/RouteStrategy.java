package io.luna.game.model.path.route;

import io.luna.game.model.collision.CollisionFlag;

/**
 * A rule that decides whether a mob may move onto a tile, given the flags of the tile and the flags that would block
 * the move.
 * <p>
 * This is how pathing keeps a mob in the kind of terrain it belongs to, such as keeping a shopkeeper inside their
 * shop. The rules are the same ones the real game uses.
 *
 * @author hydrozoa
 */
public enum RouteStrategy {

    /**
     * Ordinary walking. The move is allowed unless something blocks it.
     */
    NORMAL {
        @Override
        public boolean canMove(int tileFlags, int blockFlags) {
            return (tileFlags & blockFlags) == 0;
        }
    },

    /**
     * Movement over terrain that is blocked for ordinary walking, such as water. Walls and objects still stop the move.
     */
    BLOCKED {
        @Override
        public boolean canMove(int tileFlags, int blockFlags) {
            int flags = blockFlags & ~CollisionFlag.BLOCK_WALK;
            return (tileFlags & flags) == 0 && (tileFlags & CollisionFlag.BLOCK_WALK) != 0;
        }
    },

    /**
     * Movement that stays under a roof, which keeps a mob inside buildings.
     */
    INDOORS {
        @Override
        public boolean canMove(int tileFlags, int blockFlags) {
            return (tileFlags & blockFlags) == 0 && (tileFlags & CollisionFlag.ROOF) != 0;
        }
    },

    /**
     * Movement that stays out from under roofs, which keeps a mob outside of buildings.
     */
    OUTDOORS {
        @Override
        public boolean canMove(int tileFlags, int blockFlags) {
            return (tileFlags & (blockFlags | CollisionFlag.ROOF)) == 0;
        }
    };

    /**
     * Determines if a mob may move onto a tile.
     *
     * @param tileFlags The {@link CollisionFlag}s of the tile being moved onto.
     * @param blockFlags The flags that stop the move.
     * @return {@code true} if the move is allowed.
     */
    public abstract boolean canMove(int tileFlags, int blockFlags);
}
