package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;

/**
 * A definition model describing a non-player mob.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinition implements Definition {

    /**
     * The definition count.
     */
    public static final int SIZE = 8152;

    /**
     * The NPC definition repository.
     */
    public static final DefinitionRepository<NpcDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

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
     * The size.
     */
    private final int size;

    /**
     * The walking animation.
     */
    private final int walkAnimation;

    /**
     * The walking-back animation.
     */
    private final int walkBackAnimation;

    /**
     * The walking-left animation.
     */
    private final int walkLeftAnimation;

    /**
     * The walking-right animation.
     */
    private final int walkRightAnimation;

    /**
     * An immutable list of actions.
     */
    private final ImmutableList<String> actions;

    /**
     * Creates a new {@link NpcDefinition}.
     *
     * @param id The identifier.
     * @param name The name.
     * @param examine The examine text.
     * @param size The size.
     * @param walkAnimation The walking animation.
     * @param walkBackAnimation The walking-back animation.
     * @param walkLeftAnimation The walking-left animation.
     * @param walkRightAnimation The walking-right animation.
     * @param actions A list of actions.
     */
    public NpcDefinition(int id, String name, String examine, int size, int walkAnimation, int walkBackAnimation,
                         int walkLeftAnimation, int walkRightAnimation, String[] actions) {
        this.id = id;
        this.name = name;
        this.examine = examine;
        this.size = size;
        this.walkAnimation = walkAnimation;
        this.walkBackAnimation = walkBackAnimation;
        this.walkLeftAnimation = walkLeftAnimation;
        this.walkRightAnimation = walkRightAnimation;
        this.actions = ImmutableList.copyOf(actions);
    }


    /**
     * Determines if the NPC action at {@code index} is equal to {@code action}.
     *
     * @param index The action index.
     * @param action The action to compare.
     * @return {@code true} if the actions are equal.
     */
    public boolean hasAction(int index, String action) {
        return action.equals(actions.get(index));
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
     * @return The size.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The walking animation.
     */
    public int getWalkAnimation() {
        return walkAnimation;
    }

    /**
     * @return The walking-back animation.
     */
    public int getWalkBackAnimation() {
        return walkBackAnimation;
    }

    /**
     * @return The walking-left animation.
     */
    public int getWalkLeftAnimation() {
        return walkLeftAnimation;
    }

    /**
     * @return The walking-right animation.
     */
    public int getWalkRightAnimation() {
        return walkRightAnimation;
    }

    /**
     * @return An immutable list of actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }
}
