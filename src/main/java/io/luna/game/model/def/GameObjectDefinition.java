package io.luna.game.model.def;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.util.OptionalInt;

/**
 * A definition model describing in-game objects.
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 * @author lare96
 */
public final class GameObjectDefinition implements Definition {

    /**
     * The definition count.
     */
    public static final int SIZE = 14974;

    /**
     * The object definitions.
     */
    public static final DefinitionRepository<GameObjectDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The identifier.
     */
    private final int id;

    /**
     * The name.
     */
    private final String name;

    /**
     * The examine text.
     */
    private final String description;

    /**
     * The width.
     */
    private final int sizeX;

    /**
     * The length.
     */
    private final int sizeY;

    /**
     * If the object is solid.
     */
    private final boolean solid;

    /**
     * If the object is impenetrable.
     */
    private final boolean impenetrable;

    /**
     * If the object is interactive.
     */
    private final boolean interactive;

    /**
     * The object animation identifier, if it has one.
     */
    private final OptionalInt animationId;

    /**
     * An immutable list of actions.
     */
    private final ImmutableList<String> actions;

    /**
     * If the object is obstructive.
     */
    private final boolean obstructive;

    /**
     * The transformation definition.
     */
    private final VarpChildDefinition varpDef;

    /**
     * Creates a new {@link GameObjectDefinition}.
     *
     * @param id The identifier.
     * @param name The name.
     * @param description The examine text.
     * @param sizeX The width.
     * @param sizeY The length.
     * @param solid If the object is solid.
     * @param impenetrable If the object is impenetrable.
     * @param interactive If the object is interactive.
     * @param animationId The object animation identifier, if it has one.
     * @param actions An immutable list of actions.
     * @param obstructive If the object is obstructive.
     * @param varpDef The transformation definition.
     */
    public GameObjectDefinition(int id, String name, String description, int sizeX, int sizeY, boolean solid,
                                boolean impenetrable, boolean interactive, OptionalInt animationId, ImmutableList<String> actions,
                                boolean obstructive, VarpChildDefinition varpDef) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
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

    @Override
    public int getId() {
        return id;
    }

    /**
     * Determines if the object action at {@code index} is equal to {@code action}.
     *
     * @param index The action index.
     * @param action The action to compare.
     * @return {@code true} if the actions are equal.
     */
    public boolean hasAction(int index, String action) {
        if (index < 0 || index >= actions.size()) {
            return false;
        }
        return action.equals(actions.get(index));
    }

    /**
     * @return The name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The examine text.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The width.
     */
    public int getSizeX() {
        return sizeX;
    }

    /**
     * @return The length.
     */
    public int getSizeY() {
        return sizeY;
    }

    /**
     * @return The total area of the object ({@link #sizeX} * {@link #sizeY}).
     */
    public int getSize() {
        return sizeX * sizeY;
    }

    /**
     * @return If the object is solid.
     */
    public boolean isSolid() {
        return solid;
    }

    /**
     * @return If the object is impenetrable.
     */
    public boolean isImpenetrable() {
        return impenetrable;
    }

    /**
     * @return If the object is interactive.
     */
    public boolean isInteractive() {
        return interactive;
    }

    /**
     * @return The object animation identifier, if it has one.
     */
    public OptionalInt getAnimationId() {
        return animationId;
    }

    /**
     * @return An immutable list of actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }

    /**
     * @return If the object is obstructive.
     */
    public boolean isObstructive() {
        return obstructive;
    }

    /**
     * @return The transformation definition.
     */
    public VarpChildDefinition getVarpDef() {
        return varpDef;
    }
}
