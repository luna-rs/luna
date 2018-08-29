package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import io.luna.util.IterableArray;
import io.luna.util.ThreadUtils;

import java.util.NoSuchElementException;

/**
 * A definition model describing in-game objects.
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 */
public final class ObjectDefinition {

    /**
     * The definition count.
     */
    public static final int SIZE = 14974;

    /**
     * A list of definitions.
     */
    public static final IterableArray<ObjectDefinition> DEFINITIONS = new IterableArray<>(SIZE);

    /**
     * Sets the backing definitions.
     */
    public static void set(ObjectDefinition[] definitions) {
        ThreadUtils.ensureInitThread();

        System.arraycopy(definitions, 0, DEFINITIONS.getArray(), 0, SIZE);
    }

    /**
     * Retrieves a definition.
     */
    public static ObjectDefinition get(int id) {
        ObjectDefinition def = DEFINITIONS.get(id);
        if (def == null) {
            throw new NoSuchElementException("No definition for id " + id);
        }
        return def;
    }

    /**
     * Returns all definitions.
     */
    public static Iterable<ObjectDefinition> all() {
        return DEFINITIONS;
    }

    /**
     * Returns the object name of {@code id}.
     */
    public static String computeNameForId(int id) {
        return get(id).getName();
    }


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
     * A list of actions.
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
    public ObjectDefinition(int id, String name, String examine,  int length,int width, boolean isImpenetrable,
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
     * Determines if {@code action} is an action.
     */
    public boolean hasAction(String action) {
        return actions.contains(action);
    }

    /**
     * @return The identifier.
     */
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
     * @return A list of actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }
}
