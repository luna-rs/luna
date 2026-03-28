package io.luna.game.model.mob.combat;

import io.luna.game.model.def.CombatSpellDefinition;
import io.luna.util.StringUtils;

import static java.util.Objects.requireNonNull;

/**
 * Enumerates all combat spells currently recognized by the combat system.
 * <p>
 * This includes offensive spells from the standard spellbook and Ancient Magicks.
 *
 * @author lare96
 */
public enum CombatSpell {

    /**
     * Standard spellbook Wind Strike.
     */
    WIND_STRIKE,

    /**
     * Standard spellbook Confuse.
     */
    CONFUSE,

    /**
     * Standard spellbook Water Strike.
     */
    WATER_STRIKE,

    /**
     * Standard spellbook Earth Strike.
     */
    EARTH_STRIKE,

    /**
     * Standard spellbook Weaken.
     */
    WEAKEN,

    /**
     * Standard spellbook Fire Strike.
     */
    FIRE_STRIKE,

    /**
     * Standard spellbook Wind Bolt.
     */
    WIND_BOLT,

    /**
     * Standard spellbook Curse.
     */
    CURSE,

    /**
     * Standard spellbook Bind.
     */
    BIND,

    /**
     * Standard spellbook Water Bolt.
     */
    WATER_BOLT,

    /**
     * Standard spellbook Earth Bolt.
     */
    EARTH_BOLT,

    /**
     * Standard spellbook Fire Bolt.
     */
    FIRE_BOLT,

    /**
     * Standard spellbook Crumble Undead.
     */
    CRUMBLE_UNDEAD,

    /**
     * Standard spellbook Wind Blast.
     */
    WIND_BLAST,

    /**
     * Standard spellbook Water Blast.
     */
    WATER_BLAST,

    /**
     * Standard spellbook Iban Blast.
     */
    IBAN_BLAST,

    /**
     * Standard spellbook Snare.
     */
    SNARE,

    /**
     * Standard spellbook Magic Dart.
     */
    MAGIC_DART,

    /**
     * Standard spellbook Earth Blast.
     */
    EARTH_BLAST,

    /**
     * Standard spellbook Fire Blast.
     */
    FIRE_BLAST,

    /**
     * Standard spellbook Saradomin Strike.
     */
    SARADOMIN_STRIKE,

    /**
     * Standard spellbook Claws of Guthix.
     */
    CLAWS_OF_GUTHIX,

    /**
     * Standard spellbook Flames of Zamorak.
     */
    FLAMES_OF_ZAMORAK,

    /**
     * Standard spellbook Wind Wave.
     */
    WIND_WAVE,

    /**
     * Standard spellbook Water Wave.
     */
    WATER_WAVE,

    /**
     * Standard spellbook Vulnerability.
     */
    VULNERABILITY,

    /**
     * Standard spellbook Earth Wave.
     */
    EARTH_WAVE,

    /**
     * Standard spellbook Enfeeble.
     */
    ENFEEBLE,

    /**
     * Standard spellbook Fire Wave.
     */
    FIRE_WAVE,

    /**
     * Standard spellbook Entangle.
     */
    ENTANGLE,

    /**
     * Standard spellbook Stun.
     */
    STUN,

    /**
     * Standard spellbook Teleblock.
     */
    TELEBLOCK,

    /**
     * Ancient Magicks Smoke Rush.
     */
    SMOKE_RUSH,

    /**
     * Ancient Magicks Shadow Rush.
     */
    SHADOW_RUSH,

    /**
     * Ancient Magicks Blood Rush.
     */
    BLOOD_RUSH,

    /**
     * Ancient Magicks Ice Rush.
     */
    ICE_RUSH,

    /**
     * Ancient Magicks Smoke Burst.
     */
    SMOKE_BURST,

    /**
     * Ancient Magicks Shadow Burst.
     */
    SHADOW_BURST,

    /**
     * Ancient Magicks Blood Burst.
     */
    BLOOD_BURST,

    /**
     * Ancient Magicks Ice Burst.
     */
    ICE_BURST,

    /**
     * Ancient Magicks Smoke Blitz.
     */
    SMOKE_BLITZ,

    /**
     * Ancient Magicks Shadow Blitz.
     */
    SHADOW_BLITZ,

    /**
     * Ancient Magicks Blood Blitz.
     */
    BLOOD_BLITZ,

    /**
     * Ancient Magicks Ice Blitz.
     */
    ICE_BLITZ,

    /**
     * Ancient Magicks Smoke Barrage.
     */
    SMOKE_BARRAGE,

    /**
     * Ancient Magicks Shadow Barrage.
     */
    SHADOW_BARRAGE,

    /**
     * Ancient Magicks Blood Barrage.
     */
    BLOOD_BARRAGE,

    /**
     * Ancient Magicks Ice Barrage.
     */
    ICE_BARRAGE;


    /**
     * The human-readable spell name derived from the enum constant name.
     */
    private final String formattedName;

    /**
     * The cached definition representing this constant.
     */
    private CombatSpellDefinition def;

    /**
     * Creates a new combat spell constant and derives its formatted display name.
     */
    CombatSpell() {
        formattedName = StringUtils.capitalize(name().toLowerCase().replace('_', ' '));
    }

    /**
     * @return The formatted spell name.
     */
    public String getFormattedName() {
        return formattedName;
    }

    /**
     * @return The cached definition representing this constant.
     */
    public CombatSpellDefinition getDef() {
        if (def == null) {
            def = requireNonNull(CombatSpellDefinition.getSpells().get(this), this + " has no valid CombatSpellDefinition.");
        }
        return def;
    }
}