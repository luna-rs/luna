package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import io.luna.util.StringUtils;
import io.luna.util.parser.impl.ObjectDefinitionParser;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 *
 * @author Trevor Flynn {@literal <trevorflynn@liquidcrystalstudios.com>}
 */
public class ObjectDefinition {

    /**
     * A list of definitions.
     */
    public static final ImmutableList<ObjectDefinition> DEFINITIONS;

    /**
     * A default definition. Used as a substitute for {@code null}.
     */
    private static final ObjectDefinition DEFAULT = new ObjectDefinition(-1, null, null, false, false, false, false, -1, -1,
            StringUtils.EMPTY_ARRAY);

    /**
     * Retrieves the definition for {@code id}.
     */
    public static ObjectDefinition get(int id) {
        ObjectDefinition def = DEFINITIONS.get(id);
        if (def == DEFAULT) {
            throw new NoSuchElementException("No definition for id " + id);
        }
        return def;
    }

    /**
     * Returns an iterable containing all definitions.
     */
    public static Iterable<ObjectDefinition> all() {
        return DEFINITIONS;
    }

    /**
     * Returns the non-player name of {@code id}.
     */
    public static String computeNameForId(int id) {
        return get(id).getName();
    }

    static {
        /* Populate the immutable list with definitions. */
        ObjectDefinition[] definitions = new ObjectDefinition[14974];
        Arrays.fill(definitions, DEFAULT);

        ObjectDefinitionParser parser = new ObjectDefinitionParser(definitions);
        parser.run();

        DEFINITIONS = ImmutableList.copyOf(definitions);
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
     * The width.
     */
    private final int width;

    /**
     * The length.
     */
    private final int length;

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
     * If the object is obstructive
     */
    private final boolean isObstructive;

    /**
     * If the object is solid
     */
    private final boolean isSolid;

    /**
     * Creates a new {@link ObjectDefinition}.
     *
     * @param id The identifier.
     * @param name The name.
     * @param examine The examine text.
     * @param isSolid If it is solid
     * @param isImpenetrable
     * @param isObstructive
     * @param isInteractive
     * @param width
     * @param length
     * @param actions A list of actions.
     */
    public ObjectDefinition(int id, String name, String examine, boolean isSolid, boolean isImpenetrable, boolean isObstructive, boolean isInteractive, int width, int length, String[] actions) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.isSolid = isSolid;
        this.isImpenetrable = isImpenetrable;
        this.isInteractive = isInteractive;
        this.isObstructive = isObstructive;
        this.width = width;
        this.length = length;
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
     * @return A list of actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }

    /**
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return the length
     */
    public int getLength() {
        return length;
    }

    /**
     * @return the isImpenetrable
     */
    public boolean isIsImpenetrable() {
        return isImpenetrable;
    }

    /**
     * @return the isInteractive
     */
    public boolean isIsInteractive() {
        return isInteractive;
    }

    /**
     * @return the isObstructive
     */
    public boolean isIsObstructive() {
        return isObstructive;
    }

    /**
     * @return the isSolid
     */
    public boolean isIsSolid() {
        return isSolid;
    }
}
