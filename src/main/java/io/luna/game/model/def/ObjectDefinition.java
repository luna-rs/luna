package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;

/**
 * A definition model describing in-game objects.
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 */
public final class ObjectDefinition implements Definition {

    /**
     * The definition count.
     */
    public static final int SIZE = 14974;

    /**
     * The object definitions.
     */
    public static final DefinitionRepository<ObjectDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

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
    private final String examine;

    /**
     * The length.
     */
    private final int length;

    /**
     * The width.
     */
    private final int width;

    /**
     * An immutable list of actions.
     */
    private final ImmutableList<String> actions;

    /**
     * If the object is impenetrable.
     */
    private final boolean isImpenetrable;

    /**
     * If the object is interactive.
     */
    private final boolean isInteractive;

    /**
     * If the object is obstructive.
     */
    private final boolean isObstructive;

    /**
     * If the object is solid.
     */
    private final boolean isSolid;

    /**
     * Creates a new {@link ObjectDefinition}.
     *
     * @param id The identifier.
     * @param name The name.
     * @param examine The examine text.
     * @param length The length.
     * @param width The width.
     * @param isImpenetrable If the object is impenetrable.
     * @param isInteractive If the object is interactive.
     * @param isObstructive If the object is obstructive.
     * @param isSolid If it is solid.
     * @param actions A list of actions.
     */
    public ObjectDefinition(int id, String name, String examine, int length, int width, boolean isImpenetrable,
                            boolean isInteractive, boolean isObstructive, boolean isSolid, String[] actions) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.length = length;
        this.width = width;
        this.isImpenetrable = isImpenetrable;
        this.isInteractive = isInteractive;
        this.isObstructive = isObstructive;
        this.isSolid = isSolid;
        this.actions = ImmutableList.copyOf(actions);
    }

    /**
     * Determines if the object action at {@code index} is equal to {@code action}.
     *
     * @param index The action index.
     * @param action The action to compare.
     * @return {@code true} if the actions are equal.
     */
    public boolean hasAction(int index, String action) {
        if(index < 0 || index >= actions.size()) {
            return false;
        }
        return action.equals(actions.get(index));
    }

    @Override
    public int getId() {
        return id;
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
    public String getExamine() {
        return examine;
    }

    /**
     * @return The length.
     */
    public int getLength() {
        return length;
    }

    /**
     * @return The width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * Computes the size using {@link ObjectDefinition#length} * {@link ObjectDefinition#width}.
     *
     * @return The area of this object, in tiles.
     */
    public int getSize() {
        return length * width;
    }

    /**
     * @return If the object is impenetrable.
     */
    public boolean isImpenetrable() {
        return isImpenetrable;
    }

    /**
     * @return If the object is interactive.
     */
    public boolean isInteractive() {
        return isInteractive;
    }

    /**
     * @return If the object is obstructive.
     */
    public boolean isObstructive() {
        return isObstructive;
    }

    /**
     * @return If it is solid.
     */
    public boolean isSolid() {
        return isSolid;
    }

    /**
     * @return An immutable list of actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }
}
