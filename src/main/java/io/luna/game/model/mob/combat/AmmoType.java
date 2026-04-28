package io.luna.game.model.mob.combat;

import io.luna.game.model.def.AmmoDefinition;

import static java.util.Objects.requireNonNull;

/**
 * Enumerates the logical ranged ammunition categories supported by combat.
 * <p>
 * These values are used as high-level ammo group identifiers rather than as direct item ids. A single {@link AmmoType}
 * may correspond to one or more concrete ammo item variants in the cache/definitions layer (for example poisoned or
 * upgraded variants), while still sharing the same combat behavior.
 * <p>
 * This enum is useful for:
 * <ul>
 *     <li>Grouping equivalent ammo items under one combat type</li>
 *     <li>Matching ammo against compatible weapons</li>
 *     <li>Driving projectile/graphic selection</li>
 *     <li>Applying ranged strength and special-case combat rules</li>
 * </ul>
 *
 * @author lare96
 */
public enum AmmoType {

    /**
     * Bronze arrows.
     */
    BRONZE_ARROW,

    /**
     * Iron arrows.
     */
    IRON_ARROW,

    /**
     * Steel arrows.
     */
    STEEL_ARROW,

    /**
     * Mithril arrows.
     */
    MITHRIL_ARROW,

    /**
     * Broad arrows.
     */
    BROAD_ARROW,

    /**
     * Adamant arrows.
     */
    ADAMANT_ARROW,

    /**
     * Rune arrows.
     */
    RUNE_ARROW,

    /**
     * Crystal arrows.
     */
    CRYSTAL_ARROW,

    /**
     * Generic bolts.
     */
    BOLTS,

    /**
     * Barbed bolts.
     */
    BARBED_BOLTS,

    /**
     * Opal bolts.
     */
    OPAL_BOLTS,

    /**
     * Pearl bolts.
     */
    PEARL_BOLTS,

    /**
     * Bolt racks.
     */
    BOLT_RACK,

    /**
     * Bronze knives.
     */
    BRONZE_KNIFE,

    /**
     * Iron knives.
     */
    IRON_KNIFE,

    /**
     * Steel knives.
     */
    STEEL_KNIFE,

    /**
     * Black knives.
     */
    BLACK_KNIFE,

    /**
     * Mithril knives.
     */
    MITHRIL_KNIFE,

    /**
     * Adamant knives.
     */
    ADAMANT_KNIFE,

    /**
     * Rune knives.
     */
    RUNE_KNIFE,

    /**
     * Bronze darts.
     */
    BRONZE_DART,

    /**
     * Iron darts.
     */
    IRON_DART,

    /**
     * Steel darts.
     */
    STEEL_DART,

    /**
     * Black darts.
     */
    BLACK_DART,

    /**
     * Mithril darts.
     */
    MITHRIL_DART,

    /**
     * Adamant darts.
     */
    ADAMANT_DART,

    /**
     * Rune darts.
     */
    RUNE_DART,

    /**
     * Bronze javelins.
     */
    BRONZE_JAVELIN,

    /**
     * Iron javelins.
     */
    IRON_JAVELIN,

    /**
     * Steel javelins.
     */
    STEEL_JAVELIN,

    /**
     * Mithril javelins.
     */
    MITHRIL_JAVELIN,

    /**
     * Adamant javelins.
     */
    ADAMANT_JAVELIN,

    /**
     * Rune javelins.
     */
    RUNE_JAVELIN,

    /**
     * Bronze thrownaxes.
     */
    BRONZE_THROWNAXE,

    /**
     * Iron thrownaxes.
     */
    IRON_THROWNAXE,

    /**
     * Steel thrownaxes.
     */
    STEEL_THROWNAXE,

    /**
     * Mithril thrownaxes.
     */
    MITHRIL_THROWNAXE,

    /**
     * Adamant thrownaxes.
     */
    ADAMANT_THROWNAXE,

    /**
     * Rune thrownaxes.
     */
    RUNE_THROWNAXE,

    /**
     * Toktz-xil-ul obsidian rings.
     */
    TOKTZ_XIL_UL;

    /**
     * The cached definition representing this constant.
     */
    private AmmoDefinition def;

    /**
     * @return The cached definition representing this constant.
     */
    public AmmoDefinition getDef() {
        if (def == null) {
            def = requireNonNull(AmmoDefinition.ALL.get(this), this + " has no valid AmmoDefinition.");
        }
        return def;
    }
}