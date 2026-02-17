package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A cache-backed definition describing an in-game world object.
 * <p>
 * Object definitions provide immutable metadata used for rendering and interaction, including:
 * <ul>
 *     <li>identity: id, name, examine text</li>
 *     <li>size: {@code sizeX} (width) and {@code sizeY} (length) in tiles</li>
 *     <li>interaction/menu actions (e.g., "Open", "Climb", "Search")</li>
 *     <li>collision/behavior flags (solid, impenetrable, interactive, obstructive)</li>
 *     <li>optional object animation id</li>
 *     <li>optional varp/varbit-driven transformation data via {@link VarpChildDefinition}</li>
 * </ul>
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 * @author lare96
 */
public final class GameObjectDefinition implements Definition {

    /**
     * Total number of object definitions expected for this cache.
     */
    public static final int SIZE = 14974;

    /**
     * Repository of all {@link GameObjectDefinition}s, indexed by object id.
     */
    public static final DefinitionRepository<GameObjectDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The object id.
     */
    private final int id;

    /**
     * The object display name.
     */
    private final String name;

    /**
     * The examine/description text.
     */
    private final String description;

    /**
     * Width in tiles.
     */
    private final int sizeX;

    /**
     * Length in tiles.
     */
    private final int sizeY;

    /**
     * Interaction direction/orientation metadata from the cache.
     */
    private final int direction;

    /**
     * Whether the object is solid (typically blocks movement).
     */
    private final boolean solid;

    /**
     * Whether the object is impenetrable (typically blocks projectiles/line-of-sight, depending on engine rules).
     */
    private final boolean impenetrable;

    /**
     * Whether the object is interactive (has interaction options).
     */
    private final boolean interactive;

    /**
     * Optional animation id for the object.
     */
    private final OptionalInt animationId;

    /**
     * Context menu actions in client order.
     */
    private final ImmutableList<String> actions;

    /**
     * Whether the object is obstructive (often used by clipping/pathfinding rules).
     */
    private final boolean obstructive;

    /**
     * Optional varp/varbit-driven transformation metadata (nullable).
     */
    private final VarpChildDefinition varpDef;

    /**
     * Creates a new {@link GameObjectDefinition}.
     *
     * @param id The object id.
     * @param name The object name.
     * @param description The examine/description text.
     * @param sizeX Width in tiles.
     * @param sizeY Length in tiles.
     * @param direction Interaction direction/orientation metadata.
     * @param solid Whether the object blocks movement.
     * @param impenetrable Whether the object blocks projectiles/LOS (depending on rules).
     * @param interactive Whether the object is interactive.
     * @param animationId Optional animation id.
     * @param actions Context menu actions in client order.
     * @param obstructive Whether the object is obstructive (clipping/pathfinding hint).
     * @param varpDef Transformation metadata (nullable).
     */
    public GameObjectDefinition(int id, String name, String description, int sizeX, int sizeY, int direction, boolean solid,
                                boolean impenetrable, boolean interactive, OptionalInt animationId, ImmutableList<String> actions,
                                boolean obstructive, VarpChildDefinition varpDef) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.direction = direction;
        this.solid = solid;
        this.impenetrable = impenetrable;
        this.interactive = interactive;
        this.animationId = animationId;
        this.actions = actions;
        this.obstructive = obstructive;
        this.varpDef = varpDef;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("name", name)
                .add("description", description)
                .add("sizeX", sizeX)
                .add("sizeY", sizeY)
                .add("solid", solid)
                .add("impenetrable", impenetrable)
                .add("interactive", interactive)
                .add("animationId", animationId)
                .add("actions", actions)
                .add("obstructive", obstructive)
                .add("varpDef", varpDef)
                .toString();
    }

    /**
     * Returns the object id.
     *
     * @return The id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns {@code true} if the action at {@code index} equals {@code action}.
     *
     * @param index The action index (client order).
     * @param action The action text to compare (case-sensitive).
     * @return {@code true} if the action matches.
     */
    public boolean hasAction(int index, String action) {
        if (index < 0 || index >= actions.size()) {
            return false;
        }
        return Objects.equals(action, actions.get(index));
    }

    /**
     * Returns the object name.
     *
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the examine/description text.
     *
     * @return The description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the width in tiles.
     *
     * @return The width.
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * Returns the length in tiles.
     *
     * @return The length.
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * Returns the interaction direction/orientation metadata.
     *
     * @return The direction value.
     */
    public int getDirection() {
        return direction;
    }

    /**
     * Returns the object's area in tiles ({@code sizeX * sizeY}).
     *
     * @return The area in tiles.
     */
    public int getSize() {
        return sizeX * sizeY;
    }

    /**
     * Returns whether this object is solid.
     *
     * @return {@code true} if solid.
     */
    public boolean isSolid() {
        return solid;
    }

    /**
     * Returns whether this object is impenetrable.
     *
     * @return {@code true} if impenetrable.
     */
    public boolean isImpenetrable() {
        return impenetrable;
    }

    /**
     * Returns whether this object is interactive.
     *
     * @return {@code true} if interactive.
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * Returns the object animation id, if present.
     *
     * @return The animation id.
     */
    public OptionalInt getAnimationId() {
        return animationId;
    }

    /**
     * Returns context menu actions in client order.
     *
     * @return The actions list.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }

    /**
     * Returns whether this object is obstructive.
     *
     * @return {@code true} if obstructive.
     */
    public boolean isObstructive() {
        return obstructive;
    }

    /**
     * Returns varp/varbit-driven transformation metadata, if present.
     *
     * @return The transformation definition, or {@code null} if this object does not transform.
     */
    public VarpChildDefinition getVarpDef() {
        return varpDef;
    }
}
