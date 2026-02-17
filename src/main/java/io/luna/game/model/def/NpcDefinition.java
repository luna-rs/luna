package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;

/**
 * A cache-backed definition describing a non-player character (NPC).
 *
 * <p>
 * NPC definitions provide the immutable metadata needed to render and interact with NPCs, including:
 * <ul>
 *     <li>identity: id, name, description</li>
 *     <li>movement/animation ids: stand, walk, turn animations</li>
 *     <li>interaction: context menu actions (e.g., "Talk-to", "Attack")</li>
 *     <li>presentation: size, minimap visibility, degrees-to-turn</li>
 *     <li>combat metadata: combat level</li>
 *     <li>transformation data: varp/varbit-driven morph children via {@link VarpChildDefinition}</li>
 * </ul>
 *
 * <p>
 * <b>Repository:</b> All NPC definitions are stored in {@link #ALL}, indexed by id. The repository size
 * ({@value #SIZE}) should match the expected NPC count for the target cache revision.
 *
 * <p>
 * <b>Actions:</b> {@link #actions} represents the NPC's context menu actions in client order. Slots may be
 * {@code null} depending on the cache dump, so callers should null-check if needed.
 *
 * <p>
 * <b>Transformations:</b> If {@link #getVarpDef()} is non-null, the NPC may transform into one of several child ids
 * depending on a varp/varbit value. This class only stores the transformation metadata; selection logic lives elsewhere.
 *
 * @author lare96
 */
public final class NpcDefinition implements Definition {

    /**
     * Total number of NPC definitions expected for this cache.
     */
    public static final int SIZE = 14974;

    /**
     * Repository of all {@link NpcDefinition}s, indexed by NPC id.
     */
    public static final DefinitionRepository<NpcDefinition> ALL = new ArrayDefinitionRepository<>(SIZE);

    /**
     * The NPC id.
     */
    private final int id;

    /**
     * The NPC display name.
     */
    private final String name;

    /**
     * The examine/description text.
     */
    private final String description;

    /**
     * The NPC size in tiles (typically 1 for most NPCs).
     */
    private final int size;

    /**
     * Animation id for the idle/stand pose.
     */
    private final int standAnimationId;

    /**
     * Animation id for walking.
     */
    private final int walkAnimationId;

    /**
     * Animation id for turning back.
     */
    private final int turnBackAnimationId;

    /**
     * Animation id for turning right.
     */
    private final int turnRightAnimationId;

    /**
     * Animation id for turning left.
     */
    private final int turnLeftAnimationId;

    /**
     * The degrees to turn, used by the client for turn smoothing/rotation behavior.
     */
    private final int degreesToTurn;

    /**
     * The context menu actions for this NPC in client order.
     *
     * <p>
     * Common examples: "Talk-to", "Attack", "Pickpocket", "Trade".
     * Some indices may be {@code null} if not populated in the cache.
     */
    private final ImmutableList<String> actions;

    /**
     * Whether this NPC is visible on the minimap.
     */
    private final boolean minimapVisible;

    /**
     * The NPC combat level (often shown in the client for attackable NPCs).
     */
    private final int combatLevel;

    /**
     * Transformation metadata describing varp/varbit-driven morphing behavior (nullable).
     */
    private final VarpChildDefinition varpDef;

    /**
     * Creates a new {@link NpcDefinition}.
     *
     * @param id The NPC id.
     * @param name The NPC display name.
     * @param description The examine/description text.
     * @param size The NPC size in tiles.
     * @param standAnimationId The stand/idle animation id.
     * @param walkAnimationId The walk animation id.
     * @param turnBackAnimationId The turn-back animation id.
     * @param turnRightAnimationId The turn-right animation id.
     * @param turnLeftAnimationId The turn-left animation id.
     * @param degreesToTurn Turn degrees metadata used for rotation behavior.
     * @param actions Context menu actions in client order.
     * @param minimapVisible Whether this NPC is shown on the minimap.
     * @param combatLevel The combat level.
     * @param varpDef Transformation metadata (nullable).
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
     * Returns {@code true} if the action at {@code index} equals {@code action}.
     *
     * <p>
     * Note: this will throw if {@code index} is out of range. Additionally, if the cache provides null action entries,
     * {@code actions.get(index)} may be null and this method will throw a {@link NullPointerException}.
     * Callers that expect nulls should guard accordingly.
     *
     * @param index The action index (client order).
     * @param action The action text to compare (case-sensitive).
     * @return {@code true} if the action matches.
     */
    public boolean hasAction(int index, String action) {
        return action.equals(actions.get(index));
    }

    /**
     * Returns the NPC id.
     *
     * @return The id.
     */
    @Override
    public int getId() {
        return id;
    }

    /**
     * Returns the NPC display name.
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
     * Returns the NPC size in tiles.
     *
     * @return The size.
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns the stand/idle animation id.
     *
     * @return The stand animation id.
     */
    public int getStandAnimationId() {
        return standAnimationId;
    }

    /**
     * Returns the walk animation id.
     *
     * @return The walk animation id.
     */
    public int getWalkAnimationId() {
        return walkAnimationId;
    }

    /**
     * Returns the turn-back animation id.
     *
     * @return The turn-back animation id.
     */
    public int getTurnBackAnimationId() {
        return turnBackAnimationId;
    }

    /**
     * Returns the turn-right animation id.
     *
     * @return The turn-right animation id.
     */
    public int getTurnRightAnimationId() {
        return turnRightAnimationId;
    }

    /**
     * Returns the turn-left animation id.
     *
     * @return The turn-left animation id.
     */
    public int getTurnLeftAnimationId() {
        return turnLeftAnimationId;
    }

    /**
     * Returns the degrees-to-turn metadata used for rotation behavior.
     *
     * @return The degrees-to-turn value.
     */
    public int getDegreesToTurn() {
        return degreesToTurn;
    }

    /**
     * Returns the NPC context menu actions in client order.
     *
     * @return The actions list.
     */
    public ImmutableList<String> getActions() {
        return actions;
    }

    /**
     * Returns whether this NPC is visible on the minimap.
     *
     * @return {@code true} if visible on the minimap.
     */
    public boolean isMinimapVisible() {
        return minimapVisible;
    }

    /**
     * Returns the NPC combat level.
     *
     * @return The combat level.
     */
    public int getCombatLevel() {
        return combatLevel;
    }

    /**
     * Returns transformation metadata describing varp/varbit-driven morphing behavior, if present.
     *
     * @return The varp child definition, or {@code null} if this NPC does not transform.
     */
    public VarpChildDefinition getVarpDef() {
        return varpDef;
    }
}
