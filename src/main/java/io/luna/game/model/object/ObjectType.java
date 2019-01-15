package io.luna.game.model.object;

/**
 * An enumerated type whose elements represent different object types.
 *
 * @author lare96 <http://github.com/lare96>
 */
public enum ObjectType {
    // TODO Better naming!
    STRAIGHT_WALL(0, ObjectGroup.WALL),
    DIAGONAL_CORNER_WALL(1, ObjectGroup.WALL),
    ENTIRE_WALL(2, ObjectGroup.WALL),
    WALL_CORNER(3, ObjectGroup.WALL),
    STRAIGHT_INSIDE_WALL_DECORATION(4, ObjectGroup.WALL_DECORATION),
    STRAIGHT_OUTSIDE_WALL_DECORATION(5, ObjectGroup.WALL_DECORATION),
    DIAGONAL_OUTSIDE_WALL_DECORATION(6, ObjectGroup.WALL_DECORATION),
    DIAGONAL_INSIDE_WALL_DECORATION(7, ObjectGroup.WALL_DECORATION),
    DIAGONAL_INTERIOR_WALL_DECORATION(8, ObjectGroup.WALL_DECORATION),
    DIAGONAL_WALL(9, ObjectGroup.INTERACTABLE),
    DEFAULT(10, ObjectGroup.INTERACTABLE),
    WALKABLE_DEFAULT(11, ObjectGroup.INTERACTABLE),
    STRAIGHT_SLOPED_ROOF(12, ObjectGroup.INTERACTABLE),
    DIAGONAL_SLOPED_ROOF(13, ObjectGroup.INTERACTABLE),
    DIAGONAL_SLOPED_CONNECTING_ROOF(14, ObjectGroup.INTERACTABLE),
    STRAIGHT_SLOPED_CORNER_CONNECTING_ROOF(15, ObjectGroup.INTERACTABLE),
    STRAIGHT_SLOPED_CORNER_ROOF(16, ObjectGroup.INTERACTABLE),
    STRAIGHT_FLAT_TOP_ROOF(17, ObjectGroup.INTERACTABLE),
    STRAIGHT_BOTTOM_EDGE_ROOF(18, ObjectGroup.INTERACTABLE),
    DIAGONAL_BOTTOM_EDGE_CONNECTING_ROOF(19, ObjectGroup.INTERACTABLE),
    STRAIGHT_BOTTOM_EDGE_CONNECTING_ROOF(20, ObjectGroup.INTERACTABLE),
    STRAIGHT_BOTTOM_EDGE_CONNECTING_CORNER_ROOF(21, ObjectGroup.INTERACTABLE),
    GROUND_PROP(22, ObjectGroup.GROUND_DECORATION);

    /**
     * The type identifier.
     */
    private final int id;

    /**
     * The object group.
     */
    private final ObjectGroup group;

    /**
     * Creates a new {@link ObjectType}.
     *
     * @param id The type identifier.
     */
    ObjectType(int id, ObjectGroup group) {
        this.id = id;
        this.group = group;
    }

    /**
     * @return The type identifier.
     */
    public final int getId() {
        return id;
    }

    /**
     * @return The object group.
     */
    public ObjectGroup getGroup() {
        return group;
    }
}