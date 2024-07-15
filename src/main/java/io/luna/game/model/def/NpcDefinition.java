package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;

/**
 * A definition model describing a non-player mob.
 *
 * @author lare96
 */
public final class NpcDefinition implements Definition {

    /**
     * The definition count.
     */
    public static final int SIZE = 14974;

    /**
     * The NPC definition repository.
     */
    public static final DefinitionRepository<NpcDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The NPC id.
     */
    private final int id;

    /**
     * The NPC name.
     */
    private final String name;

    /**
     * The NPC description.
     */
    private final String description;

    /**
     * The NPC size.
     */
    private final int size;

    /**
     * The NPC stand animation.
     */
    private final int standAnimationId;

    /**
     * The NPC walk animation.
     */
    private final int walkAnimationId;

    /**
     * The NPC turn back animation.
     */
    private final int turnBackAnimationId;

    /**
     * The NPC turn right animation.
     */
    private final int turnRightAnimationId;

    /**
     * The NPC turn left animation.
     */
    private final int turnLeftAnimationId;

    /**
     * The NPC degrees to turn.
     */
    private final int degreesToTurn;

    /**
     * The NPC context menu actions.
     */
    private final ImmutableList<String> actions;

    /**
     * If the NPC is visible on the minimap.
     */
    private final boolean minimapVisible;

    /**
     * The NPC combat level.
     */
    private final int combatLevel;

    /**
     * The NPC transformation varP definition.
     */
    private final VarpChildDefinition varpDef;

    /**
     * Creates a new {@link NpcDefinition}.
     *
     * @param id The NPC id.
     * @param name The NPC name.
     * @param description The NPC description.
     * @param size The NPC size.
     * @param standAnimationId The NPC stand animation.
     * @param walkAnimationId The NPC walk animation.
     * @param turnBackAnimationId The NPC turn back animation.
     * @param turnRightAnimationId The NPC turn right animation.
     * @param turnLeftAnimationId The NPC turn left animation.
     * @param degreesToTurn The NPC degrees to turn.
     * @param actions The NPC context menu actions.
     * @param minimapVisible If the NPC is visible on the minimap.
     * @param combatLevel The NPC combat level.
     * @param varpDef The NPC transformation varP definition.
     */
    public NpcDefinition(int id, String name, String description, int size, int standAnimationId, int walkAnimationId,
                         int turnBackAnimationId, int turnRightAnimationId, int turnLeftAnimationId, int degreesToTurn,
                         ImmutableList<String> actions, boolean minimapVisible, int combatLevel,
                         VarpChildDefinition varpDef) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.size = size;
        this.standAnimationId = standAnimationId;
        this.walkAnimationId = walkAnimationId;
        this.turnBackAnimationId = turnBackAnimationId;
        this.turnRightAnimationId = turnRightAnimationId;
        this.turnLeftAnimationId = turnLeftAnimationId;
        this.degreesToTurn = degreesToTurn;
        this.actions = actions;
        this.minimapVisible = minimapVisible;
        this.combatLevel = combatLevel;
        this.varpDef = varpDef;
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

    @Override
    public int getId() {
        return id;
    }

    /**
     * @return The NPC name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The NPC description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The NPC size.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The NPC stand animation.
     */
    public int getStandAnimationId() {
        return standAnimationId;
    }

    /**
     * @return The NPC walk animation.
     */
    public int getWalkAnimationId() {
        return walkAnimationId;
    }

    /**
     * @return The NPC turn back animation.
     */
    public int getTurnBackAnimationId() {
        return turnBackAnimationId;
    }

    /**
     * @return The NPC turn right animation.
     */
    public int getTurnRightAnimationId() {
        return turnRightAnimationId;
    }

    /**
     * @return The NPC turn left animation.
     */
    public int getTurnLeftAnimationId() {
        return turnLeftAnimationId;
    }

    /**
     * @return The NPC degrees to turn.
     */
    public int getDegreesToTurn() {
        return degreesToTurn;
    }

    /**
     * @return The NPC context menu actions.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }

    /**
     * @return If the NPC is visible on the minimap.
     */
    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    /**
     * @return The NPC combat level.
     */
    public int getCombatLevel() {
        return combatLevel;
    }

    /**
     * @return The NPC transformation varP definition.
     */
    public VarpChildDefinition getVarpDef() {
        return varpDef;
    }
}
