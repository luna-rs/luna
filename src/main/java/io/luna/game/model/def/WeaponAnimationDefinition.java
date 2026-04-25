package io.luna.game.model.def;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.luna.game.model.mob.combat.CombatStyle;
import kotlin.Pair;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Defines the combat animations and equipped model data used by one or more weapon item IDs.
 * <p>
 * Each definition can be shared by multiple item IDs when those weapons use the same attack animations, defensive animation, and
 * equipped model appearance.
 *
 * @author lare96
 */
public final class WeaponAnimationDefinition {

    /**
     * A global lookup table of weapon animation definitions, indexed by weapon item ID.
     * <p>
     * This map is replaced atomically after all definitions are parsed.
     */
    public static volatile ImmutableMap<Integer, WeaponAnimationDefinition> ALL = ImmutableMap.of();

    /**
     * Loads all parsed weapon animation definitions into the global lookup table.
     * <p>
     * Each item ID owned by a definition is mapped directly to that definition. If duplicate item IDs are encountered, the later definition in {@code parsedList} will
     * replace the earlier one.
     *
     * @param parsedList The parsed weapon animation definitions.
     */
    public static void load(List<WeaponAnimationDefinition> parsedList) {
        Map<Integer, WeaponAnimationDefinition> all = new HashMap<>();
        for (WeaponAnimationDefinition def : parsedList) {
            for (int id : def.ids) {
                all.put(id, def);
            }
        }
        ALL = ImmutableMap.copyOf(all);
    }

    /**
     * The weapon item IDs that use this animation definition.
     */
    private final ImmutableSet<Integer> ids;

    /**
     * The combat style animation mappings for this weapon.
     * <p>
     * Each entry maps a {@link CombatStyle} to a pair containing:
     * <ul>
     *     <li>The attack animation ID.</li>
     *     <li>The defensive animation ID.</li>
     * </ul>
     */
    private final EnumMap<CombatStyle, Pair<Integer, Integer>> styles;

    /**
     * The equipped model data used by weapons assigned to this definition.
     */
    private final WeaponModelDefinition model;

    /**
     * Creates a new {@link WeaponAnimationDefinition}.
     *
     * @param ids The weapon item IDs that use this definition.
     * @param styles The combat style animation mappings.
     * @param model The equipped weapon model data.
     */
    public WeaponAnimationDefinition(ImmutableSet<Integer> ids, EnumMap<CombatStyle, Pair<Integer, Integer>> styles,
                                     WeaponModelDefinition model) {
        this.ids = ids;
        this.styles = styles;
        this.model = model;
    }

    /**
     * @return The weapon item IDs.
     */
    public ImmutableSet<Integer> getIds() {
        return ids;
    }

    /**
     * @return The combat style animation mappings.
     */
    public EnumMap<CombatStyle, Pair<Integer, Integer>> getStyles() {
        return styles;
    }

    /**
     * @return The equipped weapon model data.
     */
    public WeaponModelDefinition getModel() {
        return model;
    }
}