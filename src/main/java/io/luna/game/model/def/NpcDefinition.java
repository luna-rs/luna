package io.luna.game.model.def;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mobile.Npc;
import io.luna.util.StringUtils;
import io.luna.util.parser.impl.NpcDefinitionParser;

import java.util.Arrays;

/**
 * A cached definition that describes a specific {@link Npc}.
 *
 * @author lare96 <http://github.org/lare96>
 */
public final class NpcDefinition {

    /**
     * An {@link ImmutableList} of the cached {@code NpcDefinition}s.
     */
    public static final ImmutableList<NpcDefinition> DEFINITIONS;

    /**
     * The default {@link NpcDefinition} used when none in {@code DEFINITIONS} can be assigned to an {@code Npc}.
     */
    public static final NpcDefinition DEFAULT = new NpcDefinition(-1, null, null, -1, -1, -1, -1, -1,
        StringUtils.EMPTY_ARRAY);

    static {
        NpcDefinition[] definitions = new NpcDefinition[8152];
        Arrays.fill(definitions, DEFAULT);

        NpcDefinitionParser parser = new NpcDefinitionParser(definitions);
        parser.run();

        DEFINITIONS = ImmutableList.copyOf(definitions);
    }

    /**
     * The identification for the {@code Npc}.
     */
    private final int id;

    /**
     * The name of the {@code Npc}.
     */
    private final String name;

    /**
     * The description of the {@code Npc}.
     */
    private final String description;

    /**
     * The size of the {@code Npc}.
     */
    private final int size;

    /**
     * The walking animation for the {@code Npc}.
     */
    private final int walkAnimation;

    /**
     * The walking-back animation for the {@code Npc}.
     */
    private final int walkBackAnimation;

    /**
     * The walking-left animation for the {@code Npc}.
     */
    private final int walkLeftAnimation;

    /**
     * The walking-right for the {@code Npc}.
     */
    private final int walkRightAnimation;

    /**
     * The actions for the {@code Npc}.
     */
    private final ImmutableSet<String> actions;

    /**
     * Creates a new {@link NpcDefinition}.
     *
     * @param id The identification for the {@code Npc}.
     * @param name The name of the {@code Npc}.
     * @param description The description of the {@code Npc}.
     * @param size The size of the {@code Npc}.
     * @param walkAnimation The walking animation for the {@code Npc}.
     * @param walkBackAnimation The walking-back animation for the {@code Npc}.
     * @param walkLeftAnimation The walking-left animation for the {@code Npc}.
     * @param walkRightAnimation The walking-right animation for the {@code Npc}.
     * @param actions The actions for the {@code Npc}.
     */
    public NpcDefinition(int id, String name, String description, int size, int walkAnimation, int walkBackAnimation,
        int walkLeftAnimation, int walkRightAnimation, String[] actions) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.size = size;
        this.walkAnimation = walkAnimation;
        this.walkBackAnimation = walkBackAnimation;
        this.walkLeftAnimation = walkLeftAnimation;
        this.walkRightAnimation = walkRightAnimation;
        this.actions = ImmutableSet.copyOf(actions);
    }

    /**
     * @return {@code true} if {@code action} is contained by the backing set of actions.
     */
    public boolean hasAction(String action) {
        return actions.contains(action);
    }

    /**
     * @return The identification of the {@code Npc}.
     */
    public int getId() {
        return id;
    }

    /**
     * @return The name of the {@code Npc}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The description of the {@code Npc}.
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The size of the {@code Npc}.
     */
    public int getSize() {
        return size;
    }

    /**
     * @return The walking animation for the {@code Npc}.
     */
    public int getWalkAnimation() {
        return walkAnimation;
    }

    /**
     * @return The walking-back animation for the {@code Npc}.
     */
    public int getWalkBackAnimation() {
        return walkBackAnimation;
    }

    /**
     * @return The walking-left animation for the {@code Npc}.
     */
    public int getWalkLeftAnimation() {
        return walkLeftAnimation;
    }

    /**
     * @return The walking-right animation for the {@code Npc}.
     */
    public int getWalkRightAnimation() {
        return walkRightAnimation;
    }

    /**
     * @return The actions for the {@code Npc}.
     */
    public ImmutableSet<String> getActions() {
        return actions;
    }
}
